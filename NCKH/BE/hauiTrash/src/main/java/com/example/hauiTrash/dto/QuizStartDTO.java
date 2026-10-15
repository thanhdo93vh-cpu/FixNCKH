package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuizStartDTO {
    private String attemptToken;
    private QuizDTO quiz;
}