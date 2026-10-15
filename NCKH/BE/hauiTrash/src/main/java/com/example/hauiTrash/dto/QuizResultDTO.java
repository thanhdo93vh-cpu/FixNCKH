package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class QuizResultDTO {
    private Integer score;
    private Integer totalPoints;
    private Double percentage;
    private Boolean isPassed;
    private String message;
    private Map<Long, Boolean> correctAnswers;
    private Map<Long, String> correctAnswersList;
    private Map<Long, String> explanation;
}