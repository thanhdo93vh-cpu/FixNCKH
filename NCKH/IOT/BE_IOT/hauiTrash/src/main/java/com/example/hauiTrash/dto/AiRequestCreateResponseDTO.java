package com.example.hauiTrash.dto;

import lombok.*;

import java.sql.Timestamp;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRequestCreateResponseDTO {
    private Integer id;
    private String cloudinaryUrl;
    private Timestamp createdAt;
    private Timestamp finishedAt;
    private Integer accountId;
}
