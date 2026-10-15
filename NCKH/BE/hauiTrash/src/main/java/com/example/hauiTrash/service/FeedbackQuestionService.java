
package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.FeedbackQuestionDTO;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public interface FeedbackQuestionService {

    FeedbackQuestionDTO generateQuestion(Integer detectionId);

    List<FeedbackQuestionDTO> generateQuestionsForAiRequest(Integer aiRequestId);
}