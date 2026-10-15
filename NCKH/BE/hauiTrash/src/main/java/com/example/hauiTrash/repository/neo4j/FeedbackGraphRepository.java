package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.DetectionFeedback;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class FeedbackGraphRepository {

    private final Neo4jClient neo4jClient;

    public void syncFeedback(DetectionFeedback feedback) {

        if (feedback == null
                || feedback.getId() == null
                || feedback.getDetection() == null
                || feedback.getAiRequest() == null) {
            return;
        }

        Map<String, Object> params = new HashMap<>();

        Integer detectionId = feedback.getDetection().getId();
        Integer aiRequestId = feedback.getAiRequest().getId();

        params.put("detectionLogId", "DET_" + detectionId);
        params.put("feedbackId", "FB_" + feedback.getId());

        params.put("detectionId", detectionId);
        params.put("aiRequestId", aiRequestId);

        params.put("predictedLabel", feedback.getPredictedLabel());
        params.put("predictedConfidence", feedback.getPredictedConfidence());

        params.put(
                "detectionStatus",
                feedback.getDetection().getStatus()
        );

        // Giữ lại nhãn gốc trước khi người dùng xác nhận / sửa
        params.put("originalLabel", feedback.getOriginalLabel());

        params.put("isCorrect", feedback.getIsCorrect());

        params.put("correctedLabel", feedback.getCorrectedLabel());
        params.put("comment", feedback.getComment());

        params.put("feedbackType", feedback.getFeedbackType());
        params.put("reviewStatus", feedback.getReviewStatus());

        params.put("confirmedLabel", feedback.getConfirmedLabel());

        params.put(
                "createdAt",
                feedback.getCreatedAt() != null
                        ? feedback.getCreatedAt().toString()
                        : Instant.now().toString()
        );

        params.put(
                "updatedAt",
                feedback.getUpdatedAt() != null
                        ? feedback.getUpdatedAt().toString()
                        : null
        );

        /*
         * 1. Tạo / cập nhật DetectionLog
         * 2. Tạo / cập nhật Feedback
         * 3. Nối DetectionLog -> Feedback
         */
        neo4jClient.query("""
            MERGE (d:DetectionLog {id: $detectionLogId})
            SET d.detectionId = $detectionId,
                d.aiRequestId = $aiRequestId,
                d.predictedLabel = $predictedLabel,
                d.predictedConfidence = $predictedConfidence,
                d.status = $detectionStatus

            MERGE (f:Feedback {id: $feedbackId})
            SET f.feedbackId = $feedbackId,
                f.originalLabel = $originalLabel,
                f.isCorrect = $isCorrect,
                f.correctedLabel = $correctedLabel,
                f.comment = $comment,
                f.feedbackType = $feedbackType,
                f.reviewStatus = $reviewStatus,
                f.confirmedLabel = $confirmedLabel,
                f.createdAt = $createdAt,
                f.updatedAt = $updatedAt

            MERGE (d)-[:HAS_FEEDBACK]->(f)
            """)
                .bindAll(params)
                .run();

        /*
         * Liên kết prediction với TrashItem trong KG
         */
        syncPredictedTrashItem(feedback);

        /*
         * Nếu người dùng sửa kết quả,
         * liên kết Feedback -> TrashItem đúng.
         */
        syncCorrectedTrashItem(feedback);
    }

    private void syncPredictedTrashItem(
            DetectionFeedback feedback) {

        String predictedLabel = normalize(
                feedback.getPredictedLabel()
        );

        if (predictedLabel == null) {
            return;
        }

        neo4jClient.query("""
            MATCH (f:Feedback {id: $feedbackId})
            MATCH (item:TrashItem)-[:HAS_ALIAS]->(a:TrashItemAlias)
            WHERE toLower(item.label) = toLower($label)
               OR toLower(item.labelDisplay) = toLower($label)
               OR toLower(a.alias) = toLower($label)
            MERGE (f)-[:PREDICTED_AS]->(item)
            """)
                .bind("FB_" + feedback.getId())
                .to("feedbackId")
                .bind(predictedLabel)
                .to("label")
                .run();
    }

    private void syncCorrectedTrashItem(
            DetectionFeedback feedback) {

        String correctedLabel = normalize(
                feedback.getCorrectedLabel()
        );

        String predictedLabel = normalize(
                feedback.getPredictedLabel()
        );

        /*
         * Không tạo CORRECTED_TO nếu:
         * - không có correctedLabel
         * - hoặc correctedLabel giống prediction
         *
         * Ví dụ:
         * plastic -> CONFIRMED
         *
         * Không cần:
         * CORRECTED_TO -> plastic
         */
        if (correctedLabel == null) {
            return;
        }

        if (predictedLabel != null
                && predictedLabel.equalsIgnoreCase(correctedLabel)) {
            return;
        }

        neo4jClient.query("""
            MATCH (f:Feedback {id: $feedbackId})
            MATCH (item:TrashItem)
            WHERE toLower(item.label) = toLower($label)
               OR toLower(item.labelDisplay) = toLower($label)

            MERGE (f)-[:CORRECTED_TO]->(item)
            """)
                .bind("FB_" + feedback.getId())
                .to("feedbackId")
                .bind(correctedLabel)
                .to("label")
                .run();
    }

    private String normalize(String value) {

        if (value == null) {
            return null;
        }

        String result = value.trim();

        return result.isBlank() ? null : result;
    }
}