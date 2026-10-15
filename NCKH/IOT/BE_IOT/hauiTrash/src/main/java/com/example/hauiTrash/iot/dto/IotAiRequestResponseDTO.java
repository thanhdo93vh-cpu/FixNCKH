package com.example.hauiTrash.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IotAiRequestResponseDTO {
    private Integer id;
    private String cloudinaryUrl;
    private Instant createdAt;
    private Instant finishedAt;
    private Integer accountId;
}
