package com.example.hauiTrash.service;

public interface DetectionFeedbackService {
    void submitFeedback(Integer detectionId,
                        String confirmedLabel,
                        String feedbackType,
                        String comment);
}