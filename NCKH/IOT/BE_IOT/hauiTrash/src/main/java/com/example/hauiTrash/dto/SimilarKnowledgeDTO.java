package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SimilarKnowledgeDTO {
    private String label;
    private String labelDisplay;
    private String material;
    private String note;
    private String action;
    private String impact;
    private String toxicity;
    private List<String> safeSteps;
    private Double similarityScore;
}