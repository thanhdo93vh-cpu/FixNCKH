package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.entity.Detection;
import com.example.hauiTrash.entity.DetectionFeedback;
import com.example.hauiTrash.entity.ReviewQueue;
import com.example.hauiTrash.repository.DetectionFeedbackRepository;
import com.example.hauiTrash.repository.DetectionRepository;
import com.example.hauiTrash.repository.ReviewQueueRepository;
import com.example.hauiTrash.service.DetectionFeedbackService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DetectionFeedbackServiceImpl implements DetectionFeedbackService {

    private final DetectionRepository detectionRepository;
    private final DetectionFeedbackRepository detectionFeedbackRepository;
    private final ReviewQueueRepository reviewQueueRepository;

    @Override
    public void submitFeedback(Integer detectionId,
                               String confirmedLabel,
                               String feedbackType,
                               String comment) {

        Detection detection = detectionRepository.findById(detectionId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy detection id = " + detectionId));

        Instant now = Instant.now();

        DetectionFeedback feedback = new DetectionFeedback();
        feedback.setDetection(detection);
        feedback.setAiRequest(detection.getAiRequest());
        feedback.setAccount(null); // nếu chưa có user login thì để null

        feedback.setPredictedLabel(detection.getLabel());
        feedback.setPredictedConfidence(detection.getConfidence());
        feedback.setOriginalLabel(detection.getLabel());

        feedback.setConfirmedLabel(confirmedLabel);
        feedback.setFeedbackType(feedbackType);
        feedback.setComment(comment);

        feedback.setCreatedAt(now);
        feedback.setUpdatedAt(now);
        feedback.setReviewStatus("PENDING");

        if ("CONFIRMED".equalsIgnoreCase(feedbackType)) {
            feedback.setFeedbackAction("CONFIRMED");
            feedback.setIsCorrect(true);
            feedback.setCorrectedLabel(detection.getLabel());

            detection.setStatus("CONFIRMED");
        } else if ("CORRECTED".equalsIgnoreCase(feedbackType)) {
            feedback.setFeedbackAction("CORRECTED");
            feedback.setIsCorrect(false);
            feedback.setCorrectedLabel(confirmedLabel);
            feedback.setFeedbackNote(comment);

            detection.setStatus("CORRECTED");
            if (confirmedLabel != null && !confirmedLabel.isBlank()) {
                detection.setLabel(confirmedLabel);
                detection.setLabelDisplay(confirmedLabel);
            }
        } else if ("REJECTED".equalsIgnoreCase(feedbackType)) {
            feedback.setFeedbackAction("REJECTED");
            feedback.setIsCorrect(false);
            feedback.setCorrectedLabel(null);
            feedback.setFeedbackNote(comment);

            detection.setStatus("REJECTED");
        } else {
            feedback.setFeedbackAction(feedbackType);
            feedback.setIsCorrect(false);
            feedback.setFeedbackNote(comment);
        }

        detectionFeedbackRepository.save(feedback);
        detectionRepository.save(detection);

        Optional<ReviewQueue> optionalQueue =
                reviewQueueRepository.findTopByDetectionIdOrderByCreatedAtDesc(detectionId);

        if (optionalQueue.isPresent()) {
            ReviewQueue queue = optionalQueue.get();
            queue.setQueueStatus("RESOLVED");
            queue.setStatus("RESOLVED");
            queue.setResolvedNote(comment);
            queue.setResolvedAt(now);
            reviewQueueRepository.save(queue);
        }
    }
}