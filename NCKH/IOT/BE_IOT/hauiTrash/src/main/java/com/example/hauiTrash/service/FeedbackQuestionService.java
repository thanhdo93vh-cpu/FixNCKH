
package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.FeedbackQuestionDTO;

import java.util.List;

public interface FeedbackQuestionService {

    FeedbackQuestionDTO generateQuestion(Integer detectionId);

    List<FeedbackQuestionDTO> generateQuestionsForAiRequest(Integer aiRequestId);
}