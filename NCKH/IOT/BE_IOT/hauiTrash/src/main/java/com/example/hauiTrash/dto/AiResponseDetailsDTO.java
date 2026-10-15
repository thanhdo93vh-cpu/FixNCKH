package com.example.hauiTrash.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiResponseDetailsDTO {

    private Integer id;
    private String cloudinaryUrl;
    private Instant createdAt;
    private Instant finishedAt;
    private Integer accountId;

    private boolean requiresConfirmation;

    private String annotatedUrl;
    private Integer count;
    private Float confidenceAvg;

    private List<DetectionDTO> detections;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetectionDTO {
        private Integer id;
        private String label;
        private String labelDisplay;
        private Float confidence;
        private String annotatedUrl;

        private Integer trashItemId;
        private String status;

        // thêm cho crop preview
        private Integer x1;
        private Integer y1;
        private Integer x2;
        private Integer y2;
        private String cropUrl;

        private String trashType;
        private String material;
        private String note;
        private String action;

        private DetailDTO detail;
        private Integer quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailDTO {
        private String impact;
        private String toxicity;
        private List<String> safeSteps;
        private List<TrashStepDTO> trashSteps;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrashStepDTO {
        private Long id;
        private String label;
        private String labelDisplay;
        private String imageUrl;
    }
}