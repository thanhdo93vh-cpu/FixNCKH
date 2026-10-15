package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CompletedQuizDTO {
    private Long quizId;
    private String quizTitle;
    private Integer score;
    private Integer totalPoints;
    private Double percentage;
    private Boolean isPassed;
    private LocalDateTime completedAt;
}
