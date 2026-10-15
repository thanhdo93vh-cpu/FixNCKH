package com.example.hauiTrash.service;

import org.springframework.stereotype.Service;

@Service
public interface DetectionFeedbackService {
    void submitFeedback(Integer detectionId,
                        String confirmedLabel,
                        String feedbackType,
                        String comment);
}