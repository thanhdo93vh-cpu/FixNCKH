
package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.FeedbackAnswerDTO;
import com.example.hauiTrash.dto.SatisfactionRequestDTO;
import org.springframework.stereotype.Service;

@Service
public interface DetailedFeedbackService {

    void recordSatisfaction(SatisfactionRequestDTO request, Integer accountId);

    void submitFeedback(FeedbackAnswerDTO answer, Integer accountId);
}