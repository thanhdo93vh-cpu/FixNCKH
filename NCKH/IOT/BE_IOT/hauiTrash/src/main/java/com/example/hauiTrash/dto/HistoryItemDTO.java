package com.example.hauiTrash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryItemDTO {
    private Integer aiRequestId;
    private Instant createdAt;
    private String cloudinaryUrl;
    private String labelDisplay;
    private String trashType;
    private Float confidence;
}
