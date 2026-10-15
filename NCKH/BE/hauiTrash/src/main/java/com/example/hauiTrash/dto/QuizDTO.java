package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class QuizDTO {
    private Long id;
    private String title;
    private Integer timeLimitMinutes;
    private Integer passingScore;
    private Integer totalPoints;
    private List<QuestionDTO> questions;
}