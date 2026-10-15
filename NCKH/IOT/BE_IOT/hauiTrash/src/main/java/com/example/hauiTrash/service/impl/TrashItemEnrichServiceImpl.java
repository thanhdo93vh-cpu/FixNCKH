package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.client.LlmClient;
import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.TrashItemEnrichResponseDTO;
import com.example.hauiTrash.entity.*;
import com.example.hauiTrash.repository.*;
import com.example.hauiTrash.service.TrashItemEnrichService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrashItemEnrichServiceImpl implements TrashItemEnrichService {

    private static final Logger log = LoggerFactory.getLogger(TrashItemEnrichServiceImpl.class);

    // status theo entity của m
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_NEED_REVIEW = "NEED_REVIEW";

    private final AiRequestRepository aiRequestRepo;
    private final TrashItemRepository trashItemRepo;
    private final TrashItemMappingRepository mappingRepo;
    private final TrashItemKnowledgeRepository knowledgeRepo;
    private final TrashTypeRepository trashTypeRepo;
    private final LlmClient llmClient;

    /**
     * QUAN TRỌNG: gọi method @Transactional nội bộ phải qua proxy
     */
    @Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private TrashItemEnrichServiceImpl self;

    // =========================
    // Single-flight
    // =========================
    private final ConcurrentHashMap<Integer, CompletableFuture<TrashItemEnrichResponseDTO>> inflightItem =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CompletableFuture<AiResponseDetailsDTO>> inflightAll =
            new ConcurrentHashMap<>();

    // =========================
    // Rate limit controls
    // =========================
    private final Semaphore llmSemaphore = new Semaphore(1); // 1 request at a time
    private final Object rateLock = new Object();
    private long nextAllowedAtMs = 0L;

    private static final long MIN_INTERVAL_MS = 4000;
    private static final long DEFAULT_429_COOLDOWN_MS = 30000;

    // =========================
    // Public API
    // =========================

    @Override
    public TrashItemEnrichResponseDTO enrichTrashItem(Integer trashItemId) {
        CompletableFuture<TrashItemEnrichResponseDTO> mine = new CompletableFuture<>();
        CompletableFuture<TrashItemEnrichResponseDTO> existing = inflightItem.putIfAbsent(trashItemId, mine);
        if (existing != null) return join(existing);

        try {
            TrashItemEnrichResponseDTO r = enrichTrashItemsInternal(List.of(trashItemId)).get(0);
            mine.complete(r);
            return r;
        } catch (Throwable t) {
            mine.completeExceptionally(t);
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        } finally {
            inflightItem.remove(trashItemId, mine);
        }
    }

    @Override
    public AiResponseDetailsDTO enrichAllByAiRequestId(Integer aiRequestId) {
        CompletableFuture<AiResponseDetailsDTO> mine = new CompletableFuture<>();
        CompletableFuture<AiResponseDetailsDTO> existing = inflightAll.putIfAbsent(aiRequestId, mine);
        if (existing != null) return joinAll(existing);

        try {
            AiResponseDetailsDTO out = doEnrichAll(aiRequestId);
            mine.complete(out);
            return out;
        } catch (Throwable t) {
            mine.completeExceptionally(t);
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        } finally {
            inflightAll.remove(aiRequestId, mine);
        }
    }

    // =========================
    // Core enrich-all
    // =========================

    private AiResponseDetailsDTO doEnrichAll(Integer aiRequestId) {

        AiRequest req = aiRequestRepo.findByIdWithDetections(aiRequestId)
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + aiRequestId));

        List<Detection> dets = (req.getDetections() == null) ? List.of() : req.getDetections();
        if (dets.isEmpty()) {
            return AiResponseDetailsDTO.builder()
                    .id(req.getId())
                    .cloudinaryUrl(req.getCloudinaryUrl())
                    .createdAt(req.getCreatedAt())
                    .finishedAt(req.getFinishedAt())
                    .accountId(req.getAccount() != null ? req.getAccount().getId() : null)
                    .annotatedUrl(null)
                    .count(0)
                    .confidenceAvg(0f)
                    .detections(List.of())
                    .build();
        }

        String annotatedUrl = dets.get(0).getAnnotatedUrl();

        Map<String, List<Detection>> grouped = dets.stream()
                .filter(d -> d.getLabel() != null && !d.getLabel().isBlank())
                .collect(Collectors.groupingBy(Detection::getLabel, LinkedHashMap::new, Collectors.toList()));

        // labelDisplay chỉ dùng để trả UI + fill sau enrich
        Map<String, String> labelDisplayMap = resolveLabelDisplayFromDetections(grouped);

        // ✅ chỉ lưu label thôi (labelDisplay null) + NEED_REVIEW nếu mới
        Map<String, TrashItem> itemsByLabel = self.ensureTrashItemsLabelOnlyNewTx(grouped.keySet());

        List<Integer> itemIds = itemsByLabel.values().stream()
                .map(TrashItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(50)
                .toList();

        // enrich mapping/knowledge nếu cần
        List<TrashItemEnrichResponseDTO> enriched = enrichTrashItemsInternal(itemIds);

        Map<Integer, TrashItemEnrichResponseDTO> enrichedById = enriched.stream()
                .filter(x -> x.getTrashItemId() != null)
                .collect(Collectors.toMap(TrashItemEnrichResponseDTO::getTrashItemId, x -> x, (a, b) -> a));

        int rawCount = dets.size();
        float rawAvg = (float) dets.stream()
                .map(Detection::getConfidence)
                .filter(Objects::nonNull)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        List<AiResponseDetailsDTO.DetectionDTO> out = new ArrayList<>();

        for (var entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<Detection> same = entry.getValue();
            Detection first = same.get(0);

            int quantity = same.size();
            float groupAvg = (float) same.stream()
                    .map(Detection::getConfidence)
                    .filter(Objects::nonNull)
                    .mapToDouble(Float::doubleValue)
                    .average()
                    .orElse(0.0);

            TrashItem item = itemsByLabel.get(label);
            Integer itemId = item != null ? item.getId() : null;
            TrashItemEnrichResponseDTO enrich = (itemId != null) ? enrichedById.get(itemId) : null;

            String ld =
                    firstNonBlank(
                            item != null ? item.getLabelDisplay() : null,   // ✅ DB ưu tiên
                            labelDisplayMap.get(label),                     // detection fallback
                            fallbackLabelDisplay(label)                     // fallback cuối
                    );
            out.add(AiResponseDetailsDTO.DetectionDTO.builder()
                    .label(label)
                    .labelDisplay(ld)
                    .confidence(groupAvg)
                    .annotatedUrl(first.getAnnotatedUrl())
                    .trashItemId(itemId)
                    .trashType(enrich != null ? enrich.getTrashType() : null)
                    .material(enrich != null ? enrich.getMaterial() : null)
                    .note(enrich != null ? enrich.getNote() : null)
                    .action(enrich != null ? enrich.getAction() : null)
                    .detail(enrich == null || enrich.getDetail() == null ? null :
                            AiResponseDetailsDTO.DetailDTO.builder()
                                    .impact(enrich.getDetail().getImpact())
                                    .toxicity(enrich.getDetail().getToxicity())
                                    .safeSteps(enrich.getDetail().getSafeSteps())
                                    .build())
                    .quantity(quantity)
                    .build());

            // ✅ Sau enrich: set labelDisplay + ACTIVE (chỉ khi cần)
            if (itemId != null) self.fillLabelDisplayAndActivateNewTx(itemId, ld);
        }

        return AiResponseDetailsDTO.builder()
                .id(req.getId())
                .cloudinaryUrl(req.getCloudinaryUrl())
                .createdAt(req.getCreatedAt())
                .finishedAt(req.getFinishedAt())
                .accountId(req.getAccount() != null ? req.getAccount().getId() : null)
                .annotatedUrl(annotatedUrl)
                .count(rawCount)
                .confidenceAvg(rawAvg)
                .detections(out)
                .build();
    }
    private String firstNonBlank(String... xs) {
        if (xs == null) return null;
        for (String s : xs) {
            if (s != null && !s.isBlank()) return s.trim();
        }
        return null;
    }

    // =========================
    // Batch enrich core
    // =========================

    private List<TrashItemEnrichResponseDTO> enrichTrashItemsInternal(List<Integer> trashItemIds) {
        if (trashItemIds == null || trashItemIds.isEmpty()) return List.of();

        List<Integer> ids = trashItemIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(50)
                .toList();

        // 1) snapshot (read-only)
        List<Snapshot> snaps = ids.stream()
                .map(self::loadSnapshotReadOnlyTx)
                .toList();

        // 2) xác định item nào cần enrich
        List<Snapshot> needEnrich = snaps.stream()
                .filter(this::needEnrich)
                .toList();

        // 3) nếu không cần enrich -> trả luôn
        if (needEnrich.isEmpty()) {
            return snaps.stream().map(s -> toResponse(s.item, s.mapping, s.knowledge)).toList();
        }

        // 4) suggest mapping cho những item thiếu mapping
        List<Snapshot> needSuggest = needEnrich.stream().filter(s -> s.mapping == null).toList();
        Map<Integer, LlmClient.TypeSuggestion> sugById = new HashMap<>();

        if (!needSuggest.isEmpty()) {
            List<LlmClient.TypeSuggestItemInput> inputs = needSuggest.stream()
                    .map(s -> new LlmClient.TypeSuggestItemInput(n(s.item.getLabel()), n(s.item.getLabelDisplay())))
                    .toList();

            LlmClient.TypeSuggestBatchResult batch = safeSuggestTrashTypeBatch(inputs);
            List<LlmClient.TypeSuggestion> items = (batch != null && batch.getItems() != null) ? batch.getItems() : List.of();

            for (int i = 0; i < needSuggest.size(); i++) {
                Integer itemId = needSuggest.get(i).item.getId();
                LlmClient.TypeSuggestion sug = (i < items.size()) ? items.get(i) : null;
                sugById.put(itemId, normalizeTypeSuggestion(sug));
            }
        }

        // 5) per item: tạo mapping/knowledge nếu thiếu
        for (Snapshot s : needEnrich) {
            TrashItem item = s.item;

            TrashItemMapping mapping = s.mapping;
            if (mapping == null) {
                mapping = self.saveMappingOverwriteNewTx(item.getId(), sugById.get(item.getId()));
            }

            TrashItemKnowledge kn = s.knowledge;
            if (kn == null) {
                String trashTypeName =
                        (mapping != null && mapping.getTrashType() != null) ? mapping.getTrashType().getName() : "Không xác định";

                LlmClient.KnowledgeGenResult gen = safeGenerateKnowledge(item.getLabel(), item.getLabelDisplay(), trashTypeName);
                self.saveKnowledgeOverwriteNewTx(item.getId(), gen);
            }
        }

        // 6) read fresh
        List<TrashItemEnrichResponseDTO> out = new ArrayList<>(snaps.size());
        for (Snapshot s : snaps) {
            TrashItem freshItem = trashItemRepo.findById(s.item.getId()).orElse(s.item);
            TrashItemMapping freshMapping = mappingRepo.findActiveByTrashItemId(freshItem.getId()).orElse(null);
            TrashItemKnowledge freshKn = knowledgeRepo.findActiveByTrashItemId(freshItem.getId()).orElse(null);
            out.add(toResponse(freshItem, freshMapping, freshKn));
        }
        return out;
    }

    /**
     * ✅ QUY TẮC SKIP GEMINI:
     * - status ACTIVE
     * - và có mapping active
     * - và có knowledge active
     */
    private boolean needEnrich(Snapshot s) {
        if (s == null || s.item == null) return true;

        String st = nz(s.item.getStatus(), STATUS_NEED_REVIEW);
        boolean isActive = STATUS_ACTIVE.equalsIgnoreCase(st);

        if (!isActive) return true;
        return s.mapping == null || s.knowledge == null;
    }

    // =========================
    // Snapshot (read-only)
    // =========================

    @Transactional(readOnly = true)
    public Snapshot loadSnapshotReadOnlyTx(Integer trashItemId) {
        TrashItem item = trashItemRepo.findById(trashItemId)
                .orElseThrow(() -> new RuntimeException("TrashItem not found: " + trashItemId));
        TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
        TrashItemKnowledge kn = knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);
        return new Snapshot(item, mapping, kn);
    }

    public record Snapshot(TrashItem item, TrashItemMapping mapping, TrashItemKnowledge knowledge) {}

    // =========================
    // Ensure trash_items: ONLY label + NEED_REVIEW
    // =========================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, TrashItem> ensureTrashItemsLabelOnlyNewTx(Set<String> labels) {
        List<String> list = new ArrayList<>(labels);

        List<TrashItem> existing = trashItemRepo.findAllByLabelIn(list);
        Map<String, TrashItem> map = existing.stream()
                .filter(it -> it.getLabel() != null)
                .collect(Collectors.toMap(TrashItem::getLabel, it -> it, (a, b) -> a));

        for (String label : list) {
            TrashItem it = map.get(label);
            if (it == null) {
                try {
                    TrashItem created = trashItemRepo.save(TrashItem.builder()
                            .label(label)
                            .labelDisplay(null)
                            .status(STATUS_NEED_REVIEW)
                            .build());
                    map.put(label, created);
                } catch (DataIntegrityViolationException dup) {
                    TrashItem again = trashItemRepo.findByLabel(label).orElse(null);
                    if (again != null) map.put(label, again);
                }
            }
        }
        return map;
    }

    /**
     * Fill labelDisplay + set ACTIVE sau enrich (tx ngắn).
     * Chỉ update nếu labelDisplay trống hoặc status != ACTIVE.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fillLabelDisplayAndActivateNewTx(Integer itemId, String labelDisplay) {
        TrashItem item = trashItemRepo.findById(itemId).orElse(null);
        if (item == null) return;

        boolean changed = false;

        if (item.getLabelDisplay() == null || item.getLabelDisplay().isBlank()) {
            item.setLabelDisplay(labelDisplay);
            changed = true;
        }

        if (!STATUS_ACTIVE.equalsIgnoreCase(nz(item.getStatus(), ""))) {
            item.setStatus(STATUS_ACTIVE);
            changed = true;
        }

        if (changed) trashItemRepo.save(item);
    }

    // =========================
    // Overwrite Mapping/Knowledge (deactivate + insert new active)
    // =========================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TrashItemMapping saveMappingOverwriteNewTx(Integer trashItemId, LlmClient.TypeSuggestion sug) {
        TrashItem item = trashItemRepo.findById(trashItemId)
                .orElseThrow(() -> new RuntimeException("TrashItem not found: " + trashItemId));

        mappingRepo.deactivateAll(item.getId());

        TrashType type = null;
        if (sug != null && sug.getTrashTypeCode() != null && !"UNKNOWN".equalsIgnoreCase(sug.getTrashTypeCode())) {
            type = trashTypeRepo.findByCode(sug.getTrashTypeCode()).orElse(null);
        }

        TrashItemMapping created = TrashItemMapping.builder()
                .trashItem(item)
                .trashType(type)
                .mappingSource(sug != null ? "AI_SUGGESTED" : "UNKNOWN")
                .mappingConfidence(sug != null ? nzNum(sug.getConfidence(), 0f) : 0f)
                .isActive(true)
                .build();

        try {
            return mappingRepo.save(created);
        } catch (DataIntegrityViolationException dup) {
            return mappingRepo.findActiveByTrashItemId(item.getId())
                    .orElseThrow(() -> new RuntimeException("Active mapping exists but cannot load."));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TrashItemKnowledge saveKnowledgeOverwriteNewTx(Integer trashItemId, LlmClient.KnowledgeGenResult gen) {
        TrashItem item = trashItemRepo.findById(trashItemId)
                .orElseThrow(() -> new RuntimeException("TrashItem not found: " + trashItemId));

        knowledgeRepo.deactivateAll(item.getId());

        if (gen == null) gen = fallbackKnowledge();

        Integer maxV = knowledgeRepo.maxVersion(item.getId());
        int nextV = (maxV == null ? 1 : maxV + 1);

        String model = nz(gen.getModel(), "unknown");
        String source = "AI";

        TrashItemKnowledge created = TrashItemKnowledge.builder()
                .trashItem(item)
                .material(nz(gen.getMaterial(), "Không xác định"))
                .note(nz(gen.getNote(), fbNote()))
                .action(nz(gen.getAction(), fbAction()))
                .impact(nz(gen.getImpact(), fbImpact()))
                .toxicity(nz(gen.getToxicity(), fbToxicity()))
                .safeSteps(gen.getSafeSteps() != null && !gen.getSafeSteps().isEmpty()
                        ? gen.getSafeSteps()
                        : fbStepsLong())
                .source(source)
                .model(model)
                .version(nextV)
                .isActive(true)
                .build();

        try {
            return knowledgeRepo.save(created);
        } catch (DataIntegrityViolationException dup) {
            return knowledgeRepo.findActiveByTrashItemId(item.getId())
                    .orElseThrow(() -> new RuntimeException("Active knowledge exists but cannot load."));
        }
    }

    // =========================
    // SAFE LLM calls (fix log ok vs fallback)
    // =========================

    private LlmClient.TypeSuggestBatchResult safeSuggestTrashTypeBatch(List<LlmClient.TypeSuggestItemInput> inputs) {
        return withPermit(() -> {
            try {
                return llmClient.suggestTrashTypeBatch(inputs);
            } catch (WebClientResponseException.TooManyRequests e) {
                applyCooldownFrom429(e);
                log.warn("LLM 429 suggest batch size={} body={}", inputs != null ? inputs.size() : 0, safeBody(e));
                LlmClient.TypeSuggestBatchResult fb = new LlmClient.TypeSuggestBatchResult();
                fb.setItems(fallbackTypeSuggestions(inputs, "Rate limited (429)"));
                return fb;
            } catch (WebClientResponseException.BadRequest e) {
                log.error("LLM 400 suggest batch body={}", safeBody(e));
                LlmClient.TypeSuggestBatchResult fb = new LlmClient.TypeSuggestBatchResult();
                fb.setItems(fallbackTypeSuggestions(inputs, "BadRequest (400)"));
                return fb;
            } catch (Exception e) {
                log.error("LLM suggest batch error", e);
                LlmClient.TypeSuggestBatchResult fb = new LlmClient.TypeSuggestBatchResult();
                fb.setItems(fallbackTypeSuggestions(inputs, "Error"));
                return fb;
            }
        });
    }

    private LlmClient.KnowledgeGenResult safeGenerateKnowledge(String label, String labelDisplay, String trashTypeName) {
        return withPermit(() -> {
            try {
                LlmClient.KnowledgeGenResult r = llmClient.generateKnowledge(label, labelDisplay, trashTypeName);

                // ✅ quan trọng: chỉ log OK nếu không phải FALLBACK/rỗng
                if (r == null || isFallbackOrEmpty(r)) {
                    log.warn("LLM returned FALLBACK/EMPTY label={} display={}", label, labelDisplay);
                    return fallbackKnowledge();
                }

                log.info("LLM ok gen knowledge label={} display={} material={}", label, labelDisplay, r.getMaterial());
                return r;

            } catch (WebClientResponseException.TooManyRequests e) {
                applyCooldownFrom429(e);
                log.warn("LLM 429 gen knowledge label={} display={} body={}", label, labelDisplay, safeBody(e));
                return fallbackKnowledge();
            } catch (WebClientResponseException.BadRequest e) {
                log.error("LLM 400 gen knowledge label={} display={} body={}", label, labelDisplay, safeBody(e));
                return fallbackKnowledge();
            } catch (Exception e) {
                log.error("LLM gen knowledge error label={} display={}", label, labelDisplay, e);
                return fallbackKnowledge();
            }
        });
    }

    private boolean isFallbackOrEmpty(LlmClient.KnowledgeGenResult r) {
        if (r == null) return true;
        if ("FALLBACK".equalsIgnoreCase(n(r.getModel()))) return true;

        boolean allTextBlank =
                blank(r.getMaterial()) &&
                        blank(r.getNote()) &&
                        blank(r.getAction()) &&
                        blank(r.getImpact()) &&
                        blank(r.getToxicity());

        boolean stepsEmpty = (r.getSafeSteps() == null || r.getSafeSteps().isEmpty());

        // nếu text rỗng hoặc chứa đúng template fallback -> coi như fallback
        if (allTextBlank) return true;

        // m đang thấy material "Không xác định" + note/action/impact/toxicity là template -> coi như fallback
        boolean looksLikeTemplate =
                "Không xác định".equalsIgnoreCase(n(r.getMaterial()).trim()) &&
                        n(r.getNote()).contains("Không đốt rác") &&
                        n(r.getAction()).contains("Phân loại đúng nhóm rác");

        return looksLikeTemplate || stepsEmpty;
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }

    private <T> T withPermit(CheckedSupplier<T> supplier) {
        boolean acquired = false;
        try {
            llmSemaphore.acquire();
            acquired = true;

            throttle(MIN_INTERVAL_MS);
            return supplier.get();

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (acquired) llmSemaphore.release();
        }
    }

    private void throttle(long minIntervalMs) throws InterruptedException {
        synchronized (rateLock) {
            long now = System.currentTimeMillis();
            long wait = nextAllowedAtMs - now;
            if (wait > 0) Thread.sleep(wait);
            nextAllowedAtMs = System.currentTimeMillis() + minIntervalMs;
        }
    }

    private void applyCooldownFrom429(WebClientResponseException.TooManyRequests e) {
        long cooldownMs = DEFAULT_429_COOLDOWN_MS;
        try {
            List<String> ra = e.getHeaders().get("Retry-After");
            if (ra != null && !ra.isEmpty()) {
                long sec = Long.parseLong(ra.get(0).trim());
                cooldownMs = Math.max(cooldownMs, sec * 1000L);
            }
        } catch (Exception ignore) {}

        synchronized (rateLock) {
            long now = System.currentTimeMillis();
            nextAllowedAtMs = Math.max(nextAllowedAtMs, now + cooldownMs);
        }
    }

    private String safeBody(WebClientResponseException e) {
        try { return e.getResponseBodyAsString(); } catch (Exception ex) { return ""; }
    }

    // =========================
    // labelDisplay helpers
    // =========================
    private Map<String, String> resolveLabelDisplayFromDetections(Map<String, List<Detection>> grouped) {
        Map<String, String> result = new HashMap<>();
        for (var e : grouped.entrySet()) {
            Detection first = e.getValue().get(0);
            String ld = first.getLabelDisplay();
            if (ld != null && !ld.isBlank()) result.put(e.getKey(), ld.trim());
        }
        return result;
    }

    private String fallbackLabelDisplay(String label) {
        if (label == null) return "Không rõ";
        String s = label.replace('_', ' ').trim();
        if (s.isEmpty()) return "Không rõ";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // =========================
    // Response
    // =========================
    private TrashItemEnrichResponseDTO toResponse(TrashItem item, TrashItemMapping mapping, TrashItemKnowledge kn) {
        TrashType type = (mapping != null) ? mapping.getTrashType() : null;

        return TrashItemEnrichResponseDTO.builder()
                .trashItemId(item.getId())
                .label(item.getLabel())
                .labelDisplay(item.getLabelDisplay())
                .trashType(type != null ? type.getName() : null)
                .trashTypeCode(type != null ? type.getCode() : null)
                .material(kn != null ? kn.getMaterial() : null)
                .note(kn != null ? kn.getNote() : null)
                .action(kn != null ? kn.getAction() : null)
                .detail(kn == null ? null : TrashItemEnrichResponseDTO.DetailDTO.builder()
                        .impact(kn.getImpact())
                        .toxicity(kn.getToxicity())
                        .safeSteps(kn.getSafeSteps())
                        .build())
                .build();
    }

    // =========================
    // Join helpers
    // =========================
    private AiResponseDetailsDTO joinAll(CompletableFuture<AiResponseDetailsDTO> f) {
        try {
            return f.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while enrich-all", ie);
        } catch (ExecutionException ee) {
            Throwable root = ee.getCause() != null ? ee.getCause() : ee;
            if (root instanceof RuntimeException re) throw re;
            throw new RuntimeException(root);
        }
    }

    private TrashItemEnrichResponseDTO join(CompletableFuture<TrashItemEnrichResponseDTO> f) {
        try {
            return f.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while enriching trash item", ie);
        } catch (ExecutionException ee) {
            Throwable root = ee.getCause() != null ? ee.getCause() : ee;
            if (root instanceof RuntimeException re) throw re;
            throw new RuntimeException(root);
        }
    }

    // =========================
    // normalize / fallback
    // =========================
    private LlmClient.TypeSuggestion normalizeTypeSuggestion(LlmClient.TypeSuggestion r) {
        if (r == null) return LlmClient.TypeSuggestion.builder().trashTypeCode("UNKNOWN").confidence(0f).reason("null").build();
        if (r.getTrashTypeCode() == null || r.getTrashTypeCode().isBlank()) r.setTrashTypeCode("UNKNOWN");
        if (r.getConfidence() == null) r.setConfidence(0f);
        if (r.getReason() == null) r.setReason("");
        return r;
    }

    private List<LlmClient.TypeSuggestion> fallbackTypeSuggestions(List<LlmClient.TypeSuggestItemInput> inputs, String reason) {
        if (inputs == null) return List.of();
        List<LlmClient.TypeSuggestion> out = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            out.add(LlmClient.TypeSuggestion.builder()
                    .trashTypeCode("UNKNOWN")
                    .confidence(0f)
                    .reason(reason)
                    .build());
        }
        return out;
    }

    private LlmClient.KnowledgeGenResult fallbackKnowledge() {
        return LlmClient.KnowledgeGenResult.builder()
                .material("Không xác định")
                .note(fbNote())
                .action(fbAction())
                .impact(fbImpact())
                .toxicity(fbToxicity())
                .safeSteps(fbStepsLong())
                .model("FALLBACK")
                .build();
    }

    private String nz(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private Float nzNum(Float v, Float fallback) {
        return v == null ? fallback : v;
    }

    private String fbNote() {
        return "Không đốt rác. Làm sạch sơ bộ và phân loại theo hướng dẫn địa phương.";
    }

    private String fbAction() {
        return "Phân loại đúng nhóm rác. Nếu có thể tái chế, hãy đưa tới điểm thu gom/tái chế.";
    }

    private String fbImpact() {
        return "Phân loại đúng giúp giảm rác chôn lấp và tiết kiệm tài nguyên xử lý.";
    }

    private String fbToxicity() {
        return "Tránh đốt vì có thể sinh khí độc. Nếu là pin/hoá chất, cần thu gom riêng.";
    }

    private List<String> fbStepsLong() {
        return List.of(
                "Làm sạch sơ bộ, loại bỏ phần bẩn hoặc chất lỏng còn sót lại",
                "Để ráo và nếu cần thì đóng gói gọn để tránh rơi vãi",
                "Bỏ đúng nhóm rác theo thùng hoặc điểm thu gom tại địa phương"
        );
    }

    private String n(String s) { return s == null ? "" : s; }

    @FunctionalInterface
    interface CheckedSupplier<T> { T get() throws Exception; }
}
