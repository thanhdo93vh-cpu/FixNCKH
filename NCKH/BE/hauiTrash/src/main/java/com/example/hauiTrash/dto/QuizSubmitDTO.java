package com.example.hauiTrash.dto;

import lombok.Data;

import java.util.Map;

@Data
public class QuizSubmitDTO {
    private String attemptToken;
    private Long quizId;
    private Map<Long, String> answers;  // questionId -> "A","B","C","D"
}