package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PointHistoryDTO {
    private Integer points;
    private String actionType;
    private String description;
    private LocalDateTime createdAt;
}