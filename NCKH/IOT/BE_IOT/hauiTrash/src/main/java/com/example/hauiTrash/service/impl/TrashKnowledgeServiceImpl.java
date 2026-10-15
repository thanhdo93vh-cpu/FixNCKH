package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.client.LlmClient;
import com.example.hauiTrash.dto.KnowledgeViewDTO;
import com.example.hauiTrash.entity.TrashItem;
import com.example.hauiTrash.entity.TrashItemKnowledge;
import com.example.hauiTrash.entity.TrashItemMapping;
import com.example.hauiTrash.repository.TrashItemKnowledgeRepository;
import com.example.hauiTrash.repository.TrashItemMappingRepository;
import com.example.hauiTrash.repository.TrashItemRepository;
import com.example.hauiTrash.service.TrashKnowledgeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class TrashKnowledgeServiceImpl implements TrashKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(TrashKnowledgeServiceImpl.class);

    private final TrashItemRepository trashItemRepo;
    private final TrashItemMappingRepository mappingRepo;
    private final TrashItemKnowledgeRepository knowledgeRepo;
    private final LlmClient llmClient;

    // =========================
    // LLM Rate limit controls
    // =========================
    private final Semaphore llmSemaphore = new Semaphore(1);

    private final Object rateLock = new Object();
    private long nextAllowedAtMs = 0L;
    private static final long MIN_INTERVAL_MS = 4000;
    private static final long DEFAULT_429_COOLDOWN_MS = 30000;

    // single-flight theo trashItemId cho generateKnowledge
    private final ConcurrentHashMap<Integer, CompletableFuture<KnowledgeViewDTO>> inflightKnowledge =
            new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public KnowledgeViewDTO getKnowledgeIfExists(Integer trashItemId) {
        TrashItem item = trashItemRepo.findById(trashItemId)
                .orElseThrow(() -> new RuntimeException("TrashItem not found: " + trashItemId));

        TrashItemKnowledge kn = knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);
        if (kn == null) return null;

        TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
        String trashType = (mapping != null && mapping.getTrashType() != null)
                ? mapping.getTrashType().getName() : "Không xác định";

        return KnowledgeViewDTO.builder()
                .trashItemId(item.getId())
                .label(item.getLabel())
                .labelDisplay(item.getLabelDisplay())
                .trashType(trashType)
                .material(kn.getMaterial())
                .note(kn.getNote())
                .action(kn.getAction())
                .impact(kn.getImpact())
                .toxicity(kn.getToxicity())
                .safeSteps(kn.getSafeSteps())
                .build();
    }

    @Override
    public KnowledgeViewDTO generateKnowledge(Integer trashItemId, boolean force) {
        // ✅ single-flight: cùng 1 trashItem, chỉ 1 request thật chạy
        CompletableFuture<KnowledgeViewDTO> mine = new CompletableFuture<>();
        CompletableFuture<KnowledgeViewDTO> existing = inflightKnowledge.putIfAbsent(trashItemId, mine);
        if (existing != null) return join(existing);

        try {
            KnowledgeViewDTO r = generateKnowledgeOnce(trashItemId, force);
            mine.complete(r);
            return r;
        } catch (Throwable t) {
            mine.completeExceptionally(t);
            if (t instanceof RuntimeException re) throw re;
            throw new RuntimeException(t);
        } finally {
            inflightKnowledge.remove(trashItemId, mine);
        }
    }

    @Transactional
    protected KnowledgeViewDTO generateKnowledgeOnce(Integer trashItemId, boolean force) {
        TrashItem item = trashItemRepo.findById(trashItemId)
                .orElseThrow(() -> new RuntimeException("TrashItem not found: " + trashItemId));

        var existing = knowledgeRepo.findActiveByTrashItemId(item.getId());
        if (existing.isPresent() && !force) {
            return getKnowledgeIfExists(trashItemId);
        }

        // disable old if force
        if (existing.isPresent() && force) {
            TrashItemKnowledge old = existing.get();
            old.setIsActive(false);
            knowledgeRepo.save(old);
        }

        TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
        String trashTypeName = (mapping != null && mapping.getTrashType() != null)
                ? mapping.getTrashType().getName() : "Không xác định";

        Integer maxV = knowledgeRepo.maxVersion(item.getId());
        int nextV = (maxV == null ? 1 : maxV + 1);

        // ✅ LLM call có throttle + 429 cooldown
        LlmClient.KnowledgeGenResult gen = safeGenerateKnowledge(
                item.getLabel(), item.getLabelDisplay(), trashTypeName
        );

        knowledgeRepo.save(TrashItemKnowledge.builder()
                .trashItem(item)
                .material(gen != null ? nz(gen.getMaterial(), "Không xác định") : "Không xác định")
                .note(gen != null ? nz(gen.getNote(), fbNote()) : fbNote())
                .action(gen != null ? nz(gen.getAction(), fbAction()) : fbAction())
                .impact(gen != null ? nz(gen.getImpact(), fbImpact()) : fbImpact())
                .toxicity(gen != null ? nz(gen.getToxicity(), fbToxicity()) : fbToxicity())
                .safeSteps(gen != null && gen.getSafeSteps() != null && !gen.getSafeSteps().isEmpty()
                        ? gen.getSafeSteps() : fbStepsLong())
                .source(gen != null && "FALLBACK".equalsIgnoreCase(gen.getModel()) ? "FALLBACK" : "AI")
                .model(gen != null ? gen.getModel() : null)
                .version(nextV)
                .isActive(true)
                .build());

        return getKnowledgeIfExists(trashItemId);
    }

    // =========================
    // SAFE LLM CALLS
    // =========================
    private LlmClient.KnowledgeGenResult safeGenerateKnowledge(String label, String labelDisplay, String trashTypeName) {
        return withPermit(() -> {
            try {
                return llmClient.generateKnowledge(label, labelDisplay, trashTypeName);
            } catch (WebClientResponseException.TooManyRequests e) {
                applyCooldownFrom429(e);
                log.warn("LLM 429 when gen knowledge for label={} display={}", label, labelDisplay);
                return fallbackKnowledge();
            } catch (Exception e) {
                log.error("LLM gen knowledge error", e);
                return fallbackKnowledge();
            }
        });
    }

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
                String v = ra.get(0).trim();
                long sec = Long.parseLong(v);
                cooldownMs = Math.max(cooldownMs, sec * 1000L);
            }
        } catch (Exception ignore) {}

        synchronized (rateLock) {
            long now = System.currentTimeMillis();
            nextAllowedAtMs = Math.max(nextAllowedAtMs, now + cooldownMs);
        }
    }

    private KnowledgeViewDTO join(CompletableFuture<KnowledgeViewDTO> f) {
        try {
            return f.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while generating knowledge", ie);
        } catch (ExecutionException ee) {
            Throwable root = ee.getCause() != null ? ee.getCause() : ee;
            if (root instanceof RuntimeException re) throw re;
            throw new RuntimeException(root);
        }
    }

    @FunctionalInterface
    interface CheckedSupplier<T> { T get() throws Exception; }

    // =========================
    // FALLBACK + HELPERS
    // =========================
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

    private String nz(String s, String fb) {
        return (s == null || s.isBlank()) ? fb : s;
    }

    private String fbNote(){ return "Không đốt rác. Làm sạch sơ bộ và phân loại theo hướng dẫn địa phương."; }
    private String fbAction(){ return "Phân loại đúng nhóm rác. Nếu có thể tái chế, hãy đưa tới điểm thu gom/tái chế."; }
    private String fbImpact(){ return "Phân loại đúng giúp giảm rác chôn lấp và tiết kiệm tài nguyên xử lý."; }
    private String fbToxicity(){ return "Tránh đốt vì có thể sinh khí độc. Nếu là pin/hoá chất, cần thu gom riêng."; }

    private List<String> fbStepsLong() {
        return List.of(
                "Làm sạch sơ bộ, loại bỏ phần bẩn hoặc chất lỏng còn sót lại",
                "Để ráo và nếu cần thì đóng gói gọn để tránh rơi vãi",
                "Bỏ đúng nhóm rác theo thùng hoặc điểm thu gom tại địa phương"
        );
    }
}
