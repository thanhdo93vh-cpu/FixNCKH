package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.FeedbackAnswerDTO;
import com.example.hauiTrash.dto.SatisfactionRequestDTO;
import com.example.hauiTrash.entity.*;
import com.example.hauiTrash.repository.*;
import com.example.hauiTrash.service.DetailedFeedbackService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DetailedFeedbackServiceImpl implements DetailedFeedbackService {

    private final UserFeedbackRepository feedbackRepository;
    private final AiRequestRepository aiRequestRepository;
    private final DetectionRepository detectionRepository;
    private final ClassificationRepository classificationRepository;
    private final TrashItemRepository trashItemRepository;

    @Override
    @Transactional
    public void recordSatisfaction(SatisfactionRequestDTO request, Integer accountId) {
        AiRequest aiRequest = aiRequestRepository.findById(request.getAiRequestId())
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + request.getAiRequestId()));

        UserFeedback feedback = UserFeedback.builder()
                .aiRequest(aiRequest)
                .satisfactionLevel(request.getLevel())
                .satisfactionReason(request.getReason())
                .build();

        feedbackRepository.save(feedback);
        log.info("Recorded satisfaction for request {}: {}", request.getAiRequestId(), request.getLevel());
    }

    @Override
    @Transactional
    public void submitFeedback(FeedbackAnswerDTO answer, Integer accountId) {
        Detection detection = detectionRepository.findById(answer.getDetectionId())
                .orElseThrow(() -> new RuntimeException("Detection not found: " + answer.getDetectionId()));

        List<Classification> classifications = classificationRepository.findByDetectionId(detection.getId());
        ConflictInfo conflictInfo = analyzeConflicts(classifications);

        processUserChoice(answer, detection, conflictInfo);
        detectionRepository.save(detection);
        saveFeedbackRecord(answer, detection, conflictInfo);

        log.info("Saved feedback for detection {}: selectedCode={}",
                answer.getDetectionId(), answer.getSelectedCode());
    }

    //  PRIVATE METHODS

    private ConflictInfo analyzeConflicts(List<Classification> classifications) {
        if (classifications == null || classifications.isEmpty()) {
            return ConflictInfo.empty();
        }

        Map<String, Classification> bestByLabel = classifications.stream()
                .collect(Collectors.toMap(
                        c -> c.getTrashItem().getLabel(),
                        c -> c,
                        (existing, replacement) ->
                                existing.getConfidence() > replacement.getConfidence() ? existing : replacement
                ));

        List<Classification> uniqueClassifications = List.copyOf(bestByLabel.values());

        if (uniqueClassifications.isEmpty()) {
            return ConflictInfo.empty();
        }

        Classification first = uniqueClassifications.getFirst();
        String label1 = first.getTrashItem().getLabel();
        String labelDisplay1 = getLabelDisplay(first.getTrashItem());
        Float confidence1 = first.getConfidence();

        boolean hasConflict = uniqueClassifications.size() >= 2;

        String label2 = null;
        String labelDisplay2 = null;
        Float confidence2 = null;

        if (hasConflict) {
            Classification second = uniqueClassifications.get(1);
            label2 = second.getTrashItem().getLabel();
            labelDisplay2 = getLabelDisplay(second.getTrashItem());
            confidence2 = second.getConfidence();
        }

        return ConflictInfo.builder()
                .hasConflict(hasConflict)
                .label1(label1)
                .labelDisplay1(labelDisplay1)
                .confidence1(confidence1)
                .label2(label2)
                .labelDisplay2(labelDisplay2)
                .confidence2(confidence2)
                .build();
    }

    private void processUserChoice(FeedbackAnswerDTO answer, Detection detection, ConflictInfo conflict) {
        String selectedCode = answer.getSelectedCode();

        switch (selectedCode) {
            case "YES":
                detection.setStatus("CONFIRMED_BY_USER");
                break;
            case "NO":
                detection.setStatus("REJECTED_BY_USER");
                break;
            case "A":
                if (conflict.getLabel1() != null) {
                    detection.setLabel(conflict.getLabel1());
                    detection.setLabelDisplay(conflict.getLabelDisplay1());
                }
                detection.setStatus("CORRECTED_BY_USER");
                break;
            case "B":
                if (conflict.getLabel2() != null) {
                    detection.setLabel(conflict.getLabel2());
                    detection.setLabelDisplay(conflict.getLabelDisplay2());
                }
                detection.setStatus("CORRECTED_BY_USER");
                break;
            case "CUSTOM":
                String customLabel = answer.getCustomLabel();
                if (customLabel != null && !customLabel.isBlank()) {
                    detection.setLabel(customLabel);
                    detection.setLabelDisplay(customLabel);
                    createOrUpdateTrashItem(customLabel);
                }
                detection.setStatus("CUSTOM_FEEDBACK");
                break;
            default:
                log.warn("Unknown selected code: {}", selectedCode);
        }
    }

    private void saveFeedbackRecord(FeedbackAnswerDTO answer, Detection detection, ConflictInfo conflict) {
        UserFeedback feedback = UserFeedback.builder()
                .detection(detection)
                .aiRequest(detection.getAiRequest())
                .selectedCode(answer.getSelectedCode())
                .customLabel(answer.getCustomLabel())
                .comment(answer.getComment())
                .originalLabel(detection.getLabel())
                .originalConfidence(detection.getConfidence())
                .alternativeLabel(conflict.isHasConflict() ? conflict.getLabel2() : null)
                .alternativeConfidence(conflict.isHasConflict() ? conflict.getConfidence2() : null)
                .build();

        feedbackRepository.save(feedback);
    }

    private void createOrUpdateTrashItem(String customLabel) {
        String normalizedLabel = customLabel.toLowerCase().trim().replace(" ", "_");

        Optional<TrashItem> existing = trashItemRepository.findByLabel(normalizedLabel);
        if (existing.isEmpty()) {
            TrashItem newItem = TrashItem.builder()
                    .label(normalizedLabel)
                    .labelDisplay(customLabel)
                    .status("NEED_REVIEW")
                    .build();
            trashItemRepository.save(newItem);
            log.info("Created new TrashItem from user feedback: {}", customLabel);
        }
    }

    private String getLabelDisplay(TrashItem item) {
        return item.getLabelDisplay() != null ? item.getLabelDisplay() : item.getLabel();
    }

    //INNER CLASSES

    @Builder
    @Getter
    private static class ConflictInfo {
        private final boolean hasConflict;
        private final String label1;
        private final String labelDisplay1;
        private final Float confidence1;
        private final String label2;
        private final String labelDisplay2;
        private final Float confidence2;

        public static ConflictInfo empty() {
            return ConflictInfo.builder()
                    .hasConflict(false)
                    .build();
        }

    }
}