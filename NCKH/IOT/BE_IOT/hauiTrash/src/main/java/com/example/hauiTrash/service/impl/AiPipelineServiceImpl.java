package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.client.LlmClient;
import com.example.hauiTrash.client.YoloClient;
import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.GeminiTrashItemResult;
import com.example.hauiTrash.dto.VisualRagResult;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import com.example.hauiTrash.entity.*;
import com.example.hauiTrash.repository.*;
import com.example.hauiTrash.service.AiPipelineService;
import com.example.hauiTrash.service.PointService;
import com.example.hauiTrash.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiPipelineServiceImpl implements AiPipelineService {

     private final AiRequestRepository aiRequestRepo;
     private final DetectionRepository detectionRepo;
     private final TrashItemRepository trashItemRepo;
     private final TrashItemMappingRepository mappingRepo;
     private final TrashItemKnowledgeRepository knowledgeRepo;
     private final ClassificationRepository classificationRepo;
     private final PointService pointService;

     private final DetectionFeedbackRepository feedbackRepo;
     private final ReviewQueueRepository reviewQueueRepo;
     private final TrashItemAliasRepository trashItemAliasRepo;

     private final TrashStepRepository trashStepRepo;
     private final YoloClient yoloClient;
     private final LlmClient llmClient;
     private final RagService ragService;

    private static final float DEFAULT_CONF = 0.25f;
    private static final float DEFAULT_IOU = 0.60f;
    private static final float LOW_CONF_THRESHOLD = 0.50f;

    /**
     * Chỉ override khi số vote confirmed >= threshold
     */
    private static final int FEEDBACK_OVERRIDE_MIN_VOTES = 3;

    /**
     * Nếu object đang NEEDS_CONFIRM thì không ép override mạnh.
     * Có thể để threshold cao hơn nếu muốn.
     */
    private static final int FEEDBACK_OVERRIDE_MIN_VOTES_FOR_LOW_CONF = 999999;

    @Override
    @Transactional
    public AiResponseDetailsDTO predictAndEnrich(Integer requestId) {
        AiRequest req = aiRequestRepo.findByIdWithDetections(requestId)
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + requestId));

        // 1) YOLO predict
        YoloPredictResponseDTO yolo = yoloClient.predictByImageUrl(
                req.getId(),
                req.getCloudinaryUrl(),
                DEFAULT_CONF,
                DEFAULT_IOU
        );

        // 2) clear old detections
        req.getDetections().clear();

        if (yolo.getDetections() != null) {
            for (var d : yolo.getDetections()) {
                Float conf = d.getConfidence();
                String label = normLabel(d.getLabel());
                boolean needsConfirm = label == null
                        || "unknown".equalsIgnoreCase(label)
                        || (conf != null && conf < LOW_CONF_THRESHOLD);

                Detection det = Detection.builder()
                        .label(label)
                        .labelDisplay(normLabelDisplay(d.getLabelDisplay()))
                        .confidence(conf)
                        .annotatedUrl(d.getAnnotatedUrl())
                        .status(needsConfirm ? "NEEDS_CONFIRM" : "DETECTED")
                        .x1(d.getX1())
                        .y1(d.getY1())
                        .x2(d.getX2())
                        .y2(d.getY2())
                        .cropUrl(d.getCropUrl())
                        .build();

                det.setAiRequest(req);
                req.getDetections().add(det);
            }
        }

        req.setFinishedAt(Instant.now());
        aiRequestRepo.saveAndFlush(req);

        // 3) Visual RAG + Feedback Loop
        enrichDetectionsWithVisualRagAndFeedback(req);
        // 4) Cộng điểm cho user
        Account account = req.getAccount();
        if (account != null) {
            List<Detection> detections = req.getDetections();
            if (detections != null && !detections.isEmpty()) {
                // Cộng điểm cho từng object theo loại rác
                for (Detection det : detections) {
                    PointActionType action = PointActionType.fromLabel(det.getLabel());
                    if (action != null) {
                        pointService.addPoints(
                                account.getId(),
                                action.name(),
                                "detection_" + det.getId(),
                                "Phát hiện: " + (det.getLabelDisplay() != null ? det.getLabelDisplay() : det.getLabel())
                        );
                    }
                }
                // Bonus khi phát hiện nhiều object
                if (detections.size() > 1) {
                    pointService.addPoints(
                            account.getId(),
                            "DETECTION_MULTI",
                            "request_" + req.getId(),
                            "Phát hiện " + detections.size() + " vật thể"
                    );
                }
            }
        }
        // 5) response
        return buildResponse_NoLlm(req, yolo.getAnnotatedUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public AiResponseDetailsDTO getDetail(Integer requestId) {
        AiRequest req = aiRequestRepo.findByIdWithDetections(requestId)
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + requestId));

        String annotatedUrl = (req.getDetections() != null && !req.getDetections().isEmpty())
                ? req.getDetections().get(0).getAnnotatedUrl()
                : null;

        return buildResponseReadOnly_NoLlm(req, annotatedUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.example.hauiTrash.dto.HistoryItemDTO> getUserHistory() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new com.example.hauiTrash.exception.UnauthorizedException("Chưa đăng nhập");
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof Account acc)) {
            throw new com.example.hauiTrash.exception.UnauthorizedException("User không hợp lệ");
        }

        List<AiRequest> reqs = aiRequestRepo.findHistoryByAccountId(acc.getId());
        List<com.example.hauiTrash.dto.HistoryItemDTO> res = new ArrayList<>();

        for (AiRequest r : reqs) {
            com.example.hauiTrash.dto.HistoryItemDTO dto = new com.example.hauiTrash.dto.HistoryItemDTO();
            dto.setAiRequestId(r.getId());
            dto.setCreatedAt(r.getCreatedAt());
            dto.setCloudinaryUrl(r.getCloudinaryUrl());

            if (r.getDetections() != null && !r.getDetections().isEmpty()) {
                AiResponseDetailsDTO detail = buildResponseReadOnly_NoLlm(r, null);
                if (detail.getDetections() != null && !detail.getDetections().isEmpty()) {
                    AiResponseDetailsDTO.DetectionDTO mainDet = detail.getDetections().stream()
                            .filter(d -> !"NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                            .max(Comparator.comparing(AiResponseDetailsDTO.DetectionDTO::getConfidence, Comparator.nullsFirst(Float::compareTo)))
                            .orElse(detail.getDetections().get(0));

                    dto.setLabelDisplay(mainDet.getLabelDisplay() != null ? mainDet.getLabelDisplay() : "Không xác định");
                    dto.setTrashType(mainDet.getTrashType() != null && !mainDet.getTrashType().isBlank() ? mainDet.getTrashType() : "Không rõ");
                    dto.setConfidence(mainDet.getConfidence() != null ? mainDet.getConfidence() : 0f);
                } else {
                    dto.setLabelDisplay("Không có");
                    dto.setTrashType("Không rõ");
                    dto.setConfidence(0f);
                }
            } else {
                dto.setLabelDisplay("Không nhận diện được");
                dto.setTrashType("Không rõ");
                dto.setConfidence(0f);
            }
            res.add(dto);
        }
        return res;
    }

    /**
     * Logic mới:
     * 1. Tách NEEDS_CONFIRM riêng, không override mạnh
     * 2. Group detections bình thường theo label
     * 3. Lấy dominant label của request
     * 4. Với group normal:
     *    - có thể apply feedback override theo vote
     *    - retrieval ưu tiên cropUrl
     *    - dùng dominant label như tín hiệu ưu tiên khi cần lấy tri thức
     */
    @Transactional
    protected void enrichDetectionsWithVisualRagAndFeedback(AiRequest req) {
        if (req.getDetections() == null || req.getDetections().isEmpty()) {
            return;
        }

        List<Detection> all = req.getDetections();

        // Tách low-confidence riêng
        List<Detection> needConfirm = all.stream()
                .filter(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                .toList();

        List<Detection> normal = all.stream()
                .filter(d -> !"NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                .toList();

        // Group phần normal theo label
        Map<String, List<Detection>> groupedNormal = normal.stream()
                .filter(d -> d.getLabel() != null && !d.getLabel().isBlank())
                .collect(Collectors.groupingBy(
                        d -> normLabel(d.getLabel()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // dominant label của request, chỉ tính trên group normal
        String dominantLabel = groupedNormal.entrySet().stream()
                .max(Comparator.<Map.Entry<String, List<Detection>>>comparingInt(e -> e.getValue().size())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse(null);

        // ===== A. Xử lý nhóm normal =====
        for (var entry : groupedNormal.entrySet()) {
            String groupLabel = normLabel(entry.getKey());
            List<Detection> sameGroup = entry.getValue();

            // feedback override theo vote confirmedLabel
            String votedOverrideLabel = applyFeedbackOverrideByVote(groupLabel, false);

            String effectiveGroupLabel = votedOverrideLabel != null ? votedOverrideLabel : groupLabel;

            // đại diện group: ưu tiên có cropUrl
            Detection representative = sameGroup.stream()
                    .filter(d -> d.getCropUrl() != null && !d.getCropUrl().isBlank())
                    .findFirst()
                    .orElse(sameGroup.get(0));

            // Visual RAG: ưu tiên crop retrieval, fallback label retrieval
            VisualRagResult rag = retrieveVisualRag(representative, effectiveGroupLabel, dominantLabel);

            for (Detection det : sameGroup) {
                // cập nhật label theo vote nếu đủ điều kiện
                if (votedOverrideLabel != null && !votedOverrideLabel.equals(normLabel(det.getLabel()))) {
                    det.setLabel(votedOverrideLabel);

                    // đồng bộ luôn labelDisplay theo label mới, không giữ labelDisplay cũ sai
                    det.setLabelDisplay(fallbackLabelDisplay(votedOverrideLabel));
                }

                // đảm bảo trash_item tồn tại
                TrashItem item = getOrCreateTrashItem_NoLlm(
                        det.getLabel(),
                        det.getLabelDisplay() != null ? det.getLabelDisplay() : fallbackLabelDisplay(det.getLabel())
                );

                // nếu trash_item đã có display chuẩn thì đồng bộ ngược lại detection
                if (item != null && item.getLabelDisplay() != null && !item.getLabelDisplay().isBlank()) {
                    det.setLabelDisplay(item.getLabelDisplay());
                } else if (det.getLabelDisplay() == null || det.getLabelDisplay().isBlank()) {
                    det.setLabelDisplay(fallbackLabelDisplay(det.getLabel()));
                }

                // classification theo RAG
                if (rag != null) {
                    Classification cls = Classification.builder()
                            .detection(det)
                            .trashItem(rag.getTrashItem() != null ? rag.getTrashItem() : item)
                            .trashType(rag.getTrashType())
                            .source("RAG")
                            .confidence(rag.getMappingConfidence())
                            .note(buildRagNote(effectiveGroupLabel, dominantLabel, representative.getCropUrl()))
                            .build();

                    classificationRepo.save(cls);

                    // Chỉ đồng bộ label sang trashItem khi rag có item rõ ràng
                    if (rag.getTrashItem() != null) {
                        det.setLabel(rag.getTrashItem().getLabel());
                        det.setLabelDisplay(
                                rag.getTrashItem().getLabelDisplay() != null
                                        ? rag.getTrashItem().getLabelDisplay()
                                        : fallbackLabelDisplay(rag.getTrashItem().getLabel())
                        );
                    }
                }

                enqueueReviewIfNeeded(det, rag != null);
            }
        }

        // ===== B. Xử lý NEEDS_CONFIRM =====
        // Không override quá mạnh label, nhưng vẫn phải chuẩn hóa labelDisplay
        for (Detection det : needConfirm) {
            String rawLabel = normLabel(det.getLabel());
            String rawDisplay = normLabelDisplay(det.getLabelDisplay());

            // có thể retrieve nhẹ bằng crop + raw label để hỗ trợ tri thức, nhưng không ép đổi label
            VisualRagResult rag = retrieveVisualRag(det, rawLabel, dominantLabel);

            // đảm bảo trash_item tồn tại theo raw label hiện tại
            TrashItem item = getOrCreateTrashItem_NoLlm(
                    rawLabel,
                    rawDisplay != null ? rawDisplay : fallbackLabelDisplay(rawLabel)
            );

            // chuẩn hóa lại labelDisplay cho low-confidence:
            // ưu tiên trash_item / rag item, nhưng KHÔNG đổi label
            if (rag != null && rag.getTrashItem() != null) {
                String resolvedDisplay = rag.getTrashItem().getLabelDisplay();
                det.setLabelDisplay(
                        (resolvedDisplay != null && !resolvedDisplay.isBlank())
                                ? resolvedDisplay
                                : fallbackLabelDisplay(rawLabel)
                );
            } else if (item != null && item.getLabelDisplay() != null && !item.getLabelDisplay().isBlank()) {
                det.setLabelDisplay(item.getLabelDisplay());
            } else {
                det.setLabelDisplay(fallbackLabelDisplay(rawLabel));
            }

            // nếu muốn vẫn lưu classification tham khảo thì có thể lưu, nhưng KHÔNG ép đổi det.label
            if (rag != null) {
                Classification cls = Classification.builder()
                        .detection(det)
                        .trashItem(rag.getTrashItem())
                        .trashType(rag.getTrashType())
                        .source("RAG_LOW_CONF")
                        .confidence(rag.getMappingConfidence())
                        .note("Low-confidence detection, RAG attached for reference only")
                        .build();

                classificationRepo.save(cls);
            }

            enqueueReviewIfNeeded(det, rag != null);
        }

        aiRequestRepo.saveAndFlush(req);
    }
    /**
     * Feedback override mới:
     * - chỉ xét feedback CONFIRMED
     * - vote theo confirmedLabel
     * - chỉ override khi đạt threshold
     * - low-confidence thì mặc định không override mạnh
     */
    protected String applyFeedbackOverrideByVote(String rawLabel, boolean isLowConfidence) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return rawLabel;
        }

        List<DetectionFeedback> feedbacks = feedbackRepo
                .findByOriginalLabelAndFeedbackTypeIgnoreCase(normLabel(rawLabel), "CONFIRMED");

        if (feedbacks == null || feedbacks.isEmpty()) {
            return rawLabel;
        }

        Map<String, Long> votes = feedbacks.stream()
                .map(DetectionFeedback::getConfirmedLabel)
                .map(this::normLabel)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        if (votes.isEmpty()) {
            return rawLabel;
        }

        Map.Entry<String, Long> winner = votes.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .orElse(null);

        if (winner == null) {
            return rawLabel;
        }

        int threshold = isLowConfidence
                ? FEEDBACK_OVERRIDE_MIN_VOTES_FOR_LOW_CONF
                : FEEDBACK_OVERRIDE_MIN_VOTES;

        if (winner.getValue() < threshold) {
            return rawLabel;
        }

        return normLabel(winner.getKey());
    }

    /**
     * RAG Pipeline:
     * 1. Embed query (label + labelDisplay) via Gemini text-embedding-004
     * 2. Cosine similarity search against pre-computed knowledge embeddings
     * 3. Top-K retrieved contexts → Gemini LLM → Augmented Knowledge
     * 4. Fallback to exact DB match if semantic search fails
     */
    @Transactional
    protected VisualRagResult retrieveVisualRag(Detection det, String label, String dominantLabel) {
        String labelDisplay = det != null ? det.getLabelDisplay() : null;
        String cropUrl = det != null ? det.getCropUrl() : null;

        // Use RAG Service for semantic retrieval + augmented generation
        try {
            VisualRagResult ragResult = ragService.retrieve(label, labelDisplay, cropUrl);
            if (ragResult != null) {
                log.info("RAG retrieval success: label={} source={} score={}",
                        label,
                        ragResult.getRagSource(),
                        ragResult.getRagSimilarityScore());
                return ragResult;
            }
        } catch (Exception e) {
            log.warn("RAG retrieval failed, falling back to DB: label={} error={}", label, e.getMessage());
        }

        // Ultimate fallback: exact DB match
        return fallbackDbRetrieval(label);
    }

    /**
     * Fallback: exact SQL match (old behavior before RAG).
     */
    @Transactional(readOnly = true)
    protected VisualRagResult fallbackDbRetrieval(String label) {
        if (label == null || label.isBlank()) return null;

        String normalized = normLabel(label);

        TrashItem item = trashItemAliasRepo.findByAlias(normalized)
                .map(TrashItemAlias::getTrashItem)
                .orElseGet(() -> trashItemRepo.findByLabel(normalized).orElse(null));

        if (item == null) return null;

        TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
        TrashItemKnowledge knowledge = knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

        return VisualRagResult.builder()
                .trashItem(item)
                .trashType(mapping != null ? mapping.getTrashType() : null)
                .knowledge(knowledge)
                .mappingConfidence(mapping != null ? mapping.getMappingConfidence() : null)
                .ragSource("FALLBACK_DB")
                .build();
    }

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(AiPipelineServiceImpl.class);

    protected String buildRagNote(String effectiveGroupLabel, String dominantLabel, String cropUrl) {
        StringBuilder sb = new StringBuilder("Auto classified by RAG");
        if (effectiveGroupLabel != null) {
            sb.append(" | groupLabel=").append(effectiveGroupLabel);
        }
        if (dominantLabel != null) {
            sb.append(" | dominantLabel=").append(dominantLabel);
        }
        if (cropUrl != null && !cropUrl.isBlank()) {
            sb.append(" | retrieval=crop-first");
        } else {
            sb.append(" | retrieval=semantic");
        }
        return sb.toString();
    }

    protected void enqueueReviewIfNeeded(Detection det, boolean ragFound) {
        Float conf = det.getConfidence();
        String reason = null;
        String reasonCode = null;
        int priority = 1;

        if (conf == null || conf < LOW_CONF_THRESHOLD) {
            reason = "Độ tin cậy thấp, cần xác nhận.";
            reasonCode = "LOW_CONFIDENCE";
            priority = 10;
            det.setStatus("NEEDS_CONFIRM");
        } else if (!ragFound) {
            reason = "Không tìm thấy tri thức phù hợp.";
            reasonCode = "NO_RAG_MATCH";
            priority = 8;
        }

        if (reason == null) {
            return;
        }

        if (det.getId() == null) {
            detectionRepo.saveAndFlush(det);
        }

        Integer detId = det.getId();
        if (detId == null) {
            throw new RuntimeException("Detection ID is null, cannot create review_queue");
        }

        boolean existsPending = reviewQueueRepo
                .findByRefTableAndRefIdAndQueueStatus("detections", detId, "PENDING")
                .isPresent();

        if (!existsPending) {
            ReviewQueue rq = ReviewQueue.builder()
                    .queueType("AI_REVIEW")
                    .refTable("detections")
                    .refId(detId)

                    .aiRequestId(det.getAiRequest() != null ? det.getAiRequest().getId() : null)
                    .detectionId(detId)
                    .trashItemId(null)

                    .entityType("DETECTION")
                    .entityId(detId)

                    .suggestedLabel(det.getLabel())
                    .suggestedTrashTypeId(null)
                    .suggestedConfidence(det.getConfidence())

                    .reasonCode(reasonCode)
                    .reason(reason)

                    .queueStatus("PENDING")
                    .status("PENDING")
                    .priority(priority)

                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            reviewQueueRepo.save(rq);
        }
    }
    @Transactional
    public AiResponseDetailsDTO submitFeedbackAndReturn(
            Integer detectionId,
            String confirmedInput,
            String feedbackType,
            String comment
    ) {
        Detection det = detectionRepo.findById(detectionId)
                .orElseThrow(() -> new RuntimeException("Detection not found: " + detectionId));

        AiRequest req = det.getAiRequest();

        String originalLabel = det.getLabel();

        TrashItem resolvedItem = null;
        if ("CONFIRMED".equalsIgnoreCase(feedbackType)) {
            resolvedItem = resolveOrCreateTrashItemFromUserInput(confirmedInput);
        }

        String feedbackAction;
        if ("CONFIRMED".equalsIgnoreCase(feedbackType)) {
            if (resolvedItem != null && originalLabel != null
                    && originalLabel.equalsIgnoreCase(resolvedItem.getLabel())) {
                feedbackAction = "CONFIRM";
            } else {
                feedbackAction = "CORRECT";
            }
        } else if ("REJECTED".equalsIgnoreCase(feedbackType)) {
            feedbackAction = "REJECT";
        } else {
            feedbackAction = "CONFIRM";
        }

        DetectionFeedback fb = DetectionFeedback.builder()
                .detection(det) .reviewStatus("PENDING")
                .aiRequest(req)
                .originalLabel(originalLabel)
                .confirmedLabel(resolvedItem != null ? resolvedItem.getLabel() : null)
                .feedbackType(feedbackType)
                .feedbackAction(feedbackAction)
                .reviewStatus("PENDING")
                .comment(comment)
                .createdAt(Instant.now())
                .build();

        feedbackRepo.save(fb);
        feedbackRepo.save(fb);

        det.setStatus("CONFIRMED");

        if (resolvedItem != null) {
            det.setLabel(resolvedItem.getLabel());
            det.setLabelDisplay(resolvedItem.getLabelDisplay());
        }

        String ragBaseLabel = resolvedItem != null ? resolvedItem.getLabel() : det.getLabel();
        VisualRagResult rag = retrieveVisualRag(det, ragBaseLabel, ragBaseLabel);

        Classification cls = Classification.builder()
                .detection(det)

                .trashItem(rag != null && rag.getTrashItem() != null ? rag.getTrashItem() : resolvedItem)
                .trashType(rag != null ? rag.getTrashType() : null)
                .source("FEEDBACK_RAG")
                .confidence(1.0f)
                .note("Re-classified from user feedback")
                .build();

        classificationRepo.save(cls);

        reviewQueueRepo.findByEntityTypeAndEntityIdAndStatus("DETECTION", det.getId(), "PENDING")
                .ifPresent(rq -> {
                    rq.setStatus("DONE");
                    rq.setResolvedAt(Instant.now());
                    reviewQueueRepo.save(rq);
                });

        detectionRepo.saveAndFlush(det);
        aiRequestRepo.flush();

        AiRequest reloadedReq = aiRequestRepo.findByIdWithDetections(req.getId())
                .orElseThrow(() -> new RuntimeException("AiRequest not found after feedback: " + req.getId()));

        String annotatedUrl = (reloadedReq.getDetections() != null && !reloadedReq.getDetections().isEmpty())
                ? reloadedReq.getDetections().get(0).getAnnotatedUrl()
                : null;

        AiResponseDetailsDTO dto = buildResponse_NoLlm(reloadedReq, annotatedUrl);

        if (dto == null) {
            throw new RuntimeException("buildResponse_NoLlm returned null");
        }

        return dto;
    }
    @Transactional
    protected TrashItem resolveOrCreateTrashItemFromUserInput(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new RuntimeException("confirmed input cannot be blank");
        }

        String normalizedInput = userInput.trim();

        // 1. tìm theo label exact
        Optional<TrashItem> byLabel = trashItemRepo.findByLabel(normLabel(normalizedInput));
        if (byLabel.isPresent()) {
            return byLabel.get();
        }

        // 2. tìm theo labelDisplay exact
        Optional<TrashItem> byDisplay = trashItemRepo.findByLabelDisplayIgnoreCase(normalizedInput);
        if (byDisplay.isPresent()) {
            return byDisplay.get();
        }

        // 3. tìm theo alias nếu có
        Optional<TrashItemAlias> alias = trashItemAliasRepo.findByAliasIgnoreCase(normalizedInput);
        if (alias.isPresent() && alias.get().getTrashItem() != null) {
            return alias.get().getTrashItem();
        }

        // 4. chưa có -> gọi Gemini sinh label + labelDisplay chuẩn
        GeminiTrashItemResult geminiResult = llmClient.generateTrashItem(normalizedInput);
        String finalLabel = normLabel(geminiResult.getLabel());
        String finalDisplay = geminiResult.getLabelDisplay() != null && !geminiResult.getLabelDisplay().isBlank()
                ? geminiResult.getLabelDisplay().trim()
                : normalizedInput;

        // check lại 1 lần theo label Gemini trả về
        Optional<TrashItem> existed = trashItemRepo.findByLabel(finalLabel);
        if (existed.isPresent()) {
            TrashItem item = existed.get();
            if (item.getLabelDisplay() == null || item.getLabelDisplay().isBlank()) {
                item.setLabelDisplay(finalDisplay);
                item.setUpdatedAt(LocalDateTime.now());
                return trashItemRepo.save(item);
            }
            return item;
        }

        TrashItem newItem = TrashItem.builder()
                .label(finalLabel)
                .labelDisplay(finalDisplay)
                .status("NEED_REVIEW")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return trashItemRepo.save(newItem);
    }

    // =========================
    // Build response
    // =========================
    @Transactional
    protected AiResponseDetailsDTO buildResponse_NoLlm(AiRequest req, String annotatedUrl) {
        List<Detection> dets = (req.getDetections() == null) ? List.of() : req.getDetections();

        int rawCount = dets.size();
        float rawAvg = (float) dets.stream()
                .map(Detection::getConfidence)
                .filter(Objects::nonNull)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        // 1. Tách NEEDS_CONFIRM
        List<Detection> needConfirm = dets.stream()
                .filter(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                .toList();

        // 2. Group phần còn lại theo label
        Map<String, List<Detection>> grouped = dets.stream()
                .filter(d -> !"NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                .filter(d -> d.getLabel() != null)
                .collect(Collectors.groupingBy(
                        Detection::getLabel,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, String> labelDisplayMap = resolveLabelDisplay_NoLlm(grouped);

        List<AiResponseDetailsDTO.DetectionDTO> out = new ArrayList<>();

        // A. NEEDS_CONFIRM để riêng
        for (Detection det : needConfirm) {
            TrashItem item = trashItemRepo.findByLabel(det.getLabel()).orElse(null);
            TrashItemMapping mapping = (item == null) ? null : mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
            TrashItemKnowledge knowledge = (item == null) ? null : knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

            AiResponseDetailsDTO.DetectionDTO dto = toDTO_NoLlm(det, item, mapping, knowledge);
            dto.setQuantity(1);
            dto.setStatus("NEEDS_CONFIRM");

            out.add(dto);
        }

        // B. Group normal
        for (var entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<Detection> same = entry.getValue();

            int quantity = same.size();
            float groupAvg = (float) same.stream()
                    .map(Detection::getConfidence)
                    .filter(Objects::nonNull)
                    .mapToDouble(Float::doubleValue)
                    .average()
                    .orElse(0.0);

            Detection first = same.stream()
                    .filter(d -> d.getCropUrl() != null && !d.getCropUrl().isBlank())
                    .findFirst()
                    .orElse(same.get(0));

            String labelDisplay = labelDisplayMap.getOrDefault(label, fallbackLabelDisplay(label));

            TrashItem item = getOrCreateTrashItem_NoLlm(label, labelDisplay);
            TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
            TrashItemKnowledge knowledge = knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

            AiResponseDetailsDTO.DetectionDTO dto = toDTO_NoLlm(first, item, mapping, knowledge);

            dto.setId(first.getId());
            dto.setTrashItemId(item.getId());
            dto.setQuantity(quantity);
            dto.setConfidence(groupAvg);
            dto.setLabelDisplay(labelDisplay);
            dto.setStatus("DETECTED");

            out.add(dto);
        }

        out.sort(Comparator
                .comparing((AiResponseDetailsDTO.DetectionDTO x) ->
                        !"NEEDS_CONFIRM".equalsIgnoreCase(x.getStatus()))
                .thenComparing(
                        AiResponseDetailsDTO.DetectionDTO::getId,
                        Comparator.nullsLast(Integer::compareTo)
                )
        );

        boolean requiresConfirmation = !needConfirm.isEmpty();

        return AiResponseDetailsDTO.builder()
                .id(req.getId())
                .cloudinaryUrl(req.getCloudinaryUrl())
                .createdAt(req.getCreatedAt())
                .finishedAt(req.getFinishedAt())
                .accountId(req.getAccount() != null ? req.getAccount().getId() : null)
                .annotatedUrl(annotatedUrl)
                .count(rawCount)
                .confidenceAvg(rawAvg)
                .requiresConfirmation(requiresConfirmation)
                .detections(out)
                .build();
    }
    @Transactional(readOnly = true)
    protected AiResponseDetailsDTO buildResponseReadOnly_NoLlm(AiRequest req, String annotatedUrl) {
        List<Detection> dets = (req.getDetections() == null) ? List.of() : req.getDetections();

        int rawCount = dets.size();
        float rawAvg = (float) dets.stream()
                .map(Detection::getConfidence)
                .filter(Objects::nonNull)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        List<Detection> needConfirm = dets.stream()
                .filter(d -> "NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                .toList();

        Map<String, List<Detection>> grouped = dets.stream()
                .filter(d -> !"NEEDS_CONFIRM".equalsIgnoreCase(d.getStatus()))
                .filter(d -> d.getLabel() != null)
                .collect(Collectors.groupingBy(
                        d -> normLabel(d.getLabel()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<String, String> labelDisplayMap = resolveLabelDisplay_ReadOnly(grouped);

        List<AiResponseDetailsDTO.DetectionDTO> out = new ArrayList<>();

        // A. NEEDS_CONFIRM để riêng
        for (Detection det : needConfirm) {
            String label = normLabel(det.getLabel());

            TrashItem item = findTrashItemReadOnly(label);
            TrashItemMapping mapping = (item == null)
                    ? null
                    : mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
            TrashItemKnowledge knowledge = (item == null)
                    ? null
                    : knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

            AiResponseDetailsDTO.DetectionDTO dto = toDTO_ReadOnly(det, item, mapping, knowledge);
            dto.setQuantity(1);
            dto.setStatus("NEEDS_CONFIRM");
            dto.setTrashItemId(item != null ? item.getId() : null);

            out.add(dto);
        }

        // B. Group normal
        for (Map.Entry<String, List<Detection>> entry : grouped.entrySet()) {
            String label = entry.getKey();
            List<Detection> same = entry.getValue();

            int quantity = same.size();
            float groupAvg = (float) same.stream()
                    .map(Detection::getConfidence)
                    .filter(Objects::nonNull)
                    .mapToDouble(Float::doubleValue)
                    .average()
                    .orElse(0.0);

            Detection first = same.stream()
                    .filter(d -> d.getCropUrl() != null && !d.getCropUrl().isBlank())
                    .findFirst()
                    .orElse(same.get(0));

            String labelDisplay = labelDisplayMap.getOrDefault(label, fallbackLabelDisplay(label));

            TrashItem item = findTrashItemReadOnly(label);
            TrashItemMapping mapping = (item == null)
                    ? null
                    : mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
            TrashItemKnowledge knowledge = (item == null)
                    ? null
                    : knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

            AiResponseDetailsDTO.DetectionDTO dto = toDTO_ReadOnly(first, item, mapping, knowledge);
            dto.setId(first.getId());
            dto.setTrashItemId(item != null ? item.getId() : null);
            dto.setQuantity(quantity);
            dto.setConfidence(groupAvg);
            dto.setLabelDisplay(labelDisplay);
            dto.setStatus("DETECTED");

            out.add(dto);
        }

        out.sort(Comparator
                .comparing((AiResponseDetailsDTO.DetectionDTO x) ->
                        !"NEEDS_CONFIRM".equalsIgnoreCase(x.getStatus()))
                .thenComparing(
                        AiResponseDetailsDTO.DetectionDTO::getId,
                        Comparator.nullsLast(Integer::compareTo)
                )
        );

        return AiResponseDetailsDTO.builder()
                .id(req.getId())
                .cloudinaryUrl(req.getCloudinaryUrl())
                .createdAt(req.getCreatedAt())
                .finishedAt(req.getFinishedAt())
                .accountId(req.getAccount() != null ? req.getAccount().getId() : null)
                .annotatedUrl(annotatedUrl)
                .count(rawCount)
                .confidenceAvg(rawAvg)
                .requiresConfirmation(!needConfirm.isEmpty())
                .detections(out)
                .build();
    }
    private AiResponseDetailsDTO.DetectionDTO toDTO_ReadOnly(
            Detection det,
            TrashItem item,
            TrashItemMapping mapping,
            TrashItemKnowledge kn
    ) {
        Classification latestCls = null;
        if (det.getId() != null) {
            latestCls = classificationRepo.findTopByDetectionIdOrderByIdDesc(det.getId()).orElse(null);
        }

        TrashItem finalItem = item;
        TrashType finalType = mapping != null ? mapping.getTrashType() : null;

        if (latestCls != null) {
            if (latestCls.getTrashItem() != null) {
                finalItem = latestCls.getTrashItem();
            }
            if (latestCls.getTrashType() != null) {
                finalType = latestCls.getTrashType();
            }
        }

        TrashItemKnowledge finalKnowledge = kn;
        if (finalItem != null) {
            finalKnowledge = knowledgeRepo.findActiveByTrashItemId(finalItem.getId()).orElse(kn);
        }

        String resolvedLabel = det.getLabel();
        if (finalItem != null && finalItem.getLabel() != null && !finalItem.getLabel().isBlank()) {
            resolvedLabel = finalItem.getLabel();
        }
        resolvedLabel = normLabel(resolvedLabel);

        String resolvedLabelDisplay;
        if (finalItem != null
                && finalItem.getLabelDisplay() != null
                && !finalItem.getLabelDisplay().isBlank()) {
            resolvedLabelDisplay = finalItem.getLabelDisplay();
        } else if (det.getLabelDisplay() != null && !det.getLabelDisplay().isBlank()) {
            resolvedLabelDisplay = det.getLabelDisplay();
        } else {
            resolvedLabelDisplay = fallbackLabelDisplay(resolvedLabel);
        }

        List<TrashStep> stepsList = trashStepRepo.findByLabelIgnoreCase(resolvedLabel);
        List<AiResponseDetailsDTO.TrashStepDTO> stepDTOs = stepsList.isEmpty() ? null : stepsList.stream().map(s -> AiResponseDetailsDTO.TrashStepDTO.builder()
                .id(s.getId())
                .label(s.getLabel())
                .labelDisplay(s.getLabelDisplay())
                .imageUrl(s.getImageUrl())
                .build()).toList();

        return AiResponseDetailsDTO.DetectionDTO.builder()
                .id(det.getId())
                .label(resolvedLabel)
                .labelDisplay(resolvedLabelDisplay)
                .confidence(det.getConfidence() != null ? det.getConfidence() : 0f)
                .annotatedUrl(det.getAnnotatedUrl())
                .status(det.getStatus())
                .x1(det.getX1())
                .y1(det.getY1())
                .x2(det.getX2())
                .y2(det.getY2())
                .cropUrl(det.getCropUrl())
                .trashItemId(finalItem != null ? finalItem.getId() : null)
                .trashType(finalType != null ? finalType.getName() : null)
                .material(finalKnowledge != null ? finalKnowledge.getMaterial() : null)
                .note(finalKnowledge != null ? finalKnowledge.getNote() : null)
                .action(finalKnowledge != null ? finalKnowledge.getAction() : null)
                .detail((finalKnowledge == null && stepDTOs == null) ? null : AiResponseDetailsDTO.DetailDTO.builder()
                        .impact(finalKnowledge != null ? finalKnowledge.getImpact() : null)
                        .toxicity(finalKnowledge != null ? finalKnowledge.getToxicity() : null)
                        .safeSteps(finalKnowledge != null ? finalKnowledge.getSafeSteps() : null)
                        .trashSteps(stepDTOs)
                        .build())
                .build();
    }
    private Map<String, String> resolveLabelDisplay_ReadOnly(Map<String, List<Detection>> grouped) {
        Map<String, String> result = new HashMap<>();

        List<String> labels = grouped.keySet().stream()
                .filter(Objects::nonNull)
                .map(this::normLabel)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!labels.isEmpty()) {
            List<TrashItem> items = trashItemRepo.findAllByLabelIn(labels);
            for (TrashItem it : items) {
                if (it.getLabel() != null
                        && it.getLabelDisplay() != null
                        && !it.getLabelDisplay().isBlank()) {
                    result.put(normLabel(it.getLabel()), it.getLabelDisplay().trim());
                }
            }
        }

        for (Map.Entry<String, List<Detection>> e : grouped.entrySet()) {
            String label = normLabel(e.getKey());
            if (result.containsKey(label)) {
                continue;
            }

            Detection first = e.getValue().get(0);
            String yoloLd = normLabelDisplay(first.getLabelDisplay());
            if (yoloLd != null) {
                result.put(label, yoloLd);
            }
        }

        for (String label : grouped.keySet()) {
            String normalized = normLabel(label);
            result.putIfAbsent(normalized, fallbackLabelDisplay(normalized));
        }

        return result;
    }
    private TrashItem findTrashItemReadOnly(String label) {
        String normalizedLabel = normLabel(label);
        if (normalizedLabel == null || normalizedLabel.isBlank()) {
            return null;
        }
        return trashItemRepo.findByLabel(normalizedLabel).orElse(null);
    }

    private Map<String, String> resolveLabelDisplay_NoLlm(Map<String, List<Detection>> grouped) {
        Map<String, String> result = new HashMap<>();

        // 1. ưu tiên từ DB trước để tránh YOLO labelDisplay sai
        List<String> labels = grouped.keySet().stream()
                .filter(Objects::nonNull)
                .map(this::normLabel)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!labels.isEmpty()) {
            List<TrashItem> items = trashItemRepo.findAllByLabelIn(labels);
            for (TrashItem it : items) {
                if (it.getLabel() != null && it.getLabelDisplay() != null && !it.getLabelDisplay().isBlank()) {
                    result.put(normLabel(it.getLabel()), it.getLabelDisplay().trim());
                }
            }
        }

        // 2. nếu DB chưa có thì mới lấy từ detection / YOLO
        for (var e : grouped.entrySet()) {
            String label = normLabel(e.getKey());
            if (result.containsKey(label)) {
                continue;
            }

            Detection first = e.getValue().get(0);
            String yoloLd = normLabelDisplay(first.getLabelDisplay());

            if (yoloLd != null) {
                result.put(label, yoloLd);
            }
        }

        // 3. fallback
        for (String label : grouped.keySet()) {
            String normalized = normLabel(label);
            result.putIfAbsent(normalized, fallbackLabelDisplay(normalized));
        }

        return result;
    }
    protected TrashItem getOrCreateTrashItem_NoLlm(String label, String labelDisplay) {
        String normalizedLabel = normLabel(label);
        String normalizedDisplay = normLabelDisplay(labelDisplay);

        if (normalizedLabel == null || normalizedLabel.isBlank()) {
            throw new RuntimeException("Label cannot be null/blank");
        }

        String finalDisplay = normalizedDisplay != null ? normalizedDisplay : fallbackLabelDisplay(normalizedLabel);

        return trashItemRepo.findByLabel(normalizedLabel)
                .map(item -> {
                    boolean changed = false;

                    if (item.getLabelDisplay() == null || item.getLabelDisplay().isBlank()) {
                        item.setLabelDisplay(finalDisplay);
                        changed = true;
                    }

                    if (item.getUpdatedAt() == null) {
                        item.setUpdatedAt(LocalDateTime.now());
                        changed = true;
                    }

                    return changed ? trashItemRepo.save(item) : item;
                })
                .orElseGet(() ->
                        trashItemRepo.save(TrashItem.builder()
                                .label(normalizedLabel)
                                .labelDisplay(finalDisplay)
                                .status("NEED_REVIEW")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build())
                );
    }

    private AiResponseDetailsDTO.DetectionDTO toDTO_NoLlm(
            Detection det,
            TrashItem item,
            TrashItemMapping mapping,
            TrashItemKnowledge kn
    ) {
        Classification latestCls = null;
        if (det.getId() != null) {
            latestCls = classificationRepo.findTopByDetectionIdOrderByIdDesc(det.getId()).orElse(null);
        }

        TrashItem finalItem = item;
        TrashType finalType = mapping != null ? mapping.getTrashType() : null;

        if (latestCls != null) {
            if (latestCls.getTrashItem() != null) {
                finalItem = latestCls.getTrashItem();
            }
            if (latestCls.getTrashType() != null) {
                finalType = latestCls.getTrashType();
            }
        }

        TrashItemKnowledge finalKnowledge = kn;
        if (finalItem != null) {
            finalKnowledge = knowledgeRepo.findActiveByTrashItemId(finalItem.getId()).orElse(kn);
        }

        String resolvedLabel = det.getLabel();
        if (finalItem != null && finalItem.getLabel() != null && !finalItem.getLabel().isBlank()) {
            resolvedLabel = finalItem.getLabel();
        }
        resolvedLabel = normLabel(resolvedLabel);

        String resolvedLabelDisplay = null;
        if (finalItem != null && finalItem.getLabelDisplay() != null && !finalItem.getLabelDisplay().isBlank()) {
            resolvedLabelDisplay = finalItem.getLabelDisplay();
        } else if (det.getLabelDisplay() != null && !det.getLabelDisplay().isBlank()) {
            resolvedLabelDisplay = det.getLabelDisplay();
        } else {
            resolvedLabelDisplay = fallbackLabelDisplay(resolvedLabel);
        }

        List<TrashStep> stepsList = trashStepRepo.findByLabelIgnoreCase(resolvedLabel);
        List<AiResponseDetailsDTO.TrashStepDTO> stepDTOs = stepsList.isEmpty() ? null : stepsList.stream().map(s -> AiResponseDetailsDTO.TrashStepDTO.builder()
                .id(s.getId())
                .label(s.getLabel())
                .labelDisplay(s.getLabelDisplay())
                .imageUrl(s.getImageUrl())
                .build()).toList();

        return AiResponseDetailsDTO.DetectionDTO.builder()
                .id(det.getId())
                .label(resolvedLabel)
                .labelDisplay(resolvedLabelDisplay)
                .confidence(det.getConfidence() != null ? det.getConfidence() : 0f)
                .annotatedUrl(det.getAnnotatedUrl())
                .status(det.getStatus())

                .x1(det.getX1())
                .y1(det.getY1())
                .x2(det.getX2())
                .y2(det.getY2())
                .cropUrl(det.getCropUrl())

                .trashItemId(finalItem != null ? finalItem.getId() : null)
                .trashType(finalType != null ? finalType.getName() : null)
                .material(finalKnowledge != null ? finalKnowledge.getMaterial() : null)
                .note(finalKnowledge != null ? finalKnowledge.getNote() : null)
                .action(finalKnowledge != null ? finalKnowledge.getAction() : null)
                .detail((finalKnowledge == null && stepDTOs == null) ? null : AiResponseDetailsDTO.DetailDTO.builder()
                        .impact(finalKnowledge != null ? finalKnowledge.getImpact() : null)
                        .toxicity(finalKnowledge != null ? finalKnowledge.getToxicity() : null)
                        .safeSteps(finalKnowledge != null ? finalKnowledge.getSafeSteps() : null)
                        .trashSteps(stepDTOs)
                        .build())
                .build();
    }

    private String fallbackLabelDisplay(String label) {
        if (label == null) return "Không rõ";
        String s = label.replace('_', ' ').trim();
        if (s.isEmpty()) return "Không rõ";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String normLabel(String label) {
        if (label == null) return null;
        String s = label.trim();
        if (s.isEmpty()) return null;
        return s.toLowerCase(Locale.ROOT);
    }

    private String normLabelDisplay(String labelDisplay) {
        if (labelDisplay == null) return null;
        String s = labelDisplay.trim();
        return s.isEmpty() ? null : s;
    }
}
