package com.example.hauiTrash.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrashItemEnrichResponseDTO {
    private Integer trashItemId;
    private String label;
    private String labelDisplay;

    // mapping
    private String trashType;   // name
    private String trashTypeCode;

    // knowledge
    private String material;
    private String note;
    private String action;

    private DetailDTO detail;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailDTO {
        private String impact;
        private String toxicity;
        private List<String> safeSteps;
    }
}
