package com.example.hauiTrash.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class YoloPredictResponseDTO {
    private Integer requestId;
    private String imageUrl;
    private String annotatedUrl;
    private Map<String, Object> params;
    private Integer count;
    private Float confidenceAvg;
    private Boolean requiresConfirmation;
    private List<DetectionDTO> detections;

    @Data
    public static class DetectionDTO {
        private Integer id;
        private String label;
        private String labelDisplay;
        private Float confidence;
        private String annotatedUrl;

        private Integer x1;
        private Integer y1;
        private Integer x2;
        private Integer y2;

        private String cropUrl;
        private Boolean needsConfirm;
    }
}