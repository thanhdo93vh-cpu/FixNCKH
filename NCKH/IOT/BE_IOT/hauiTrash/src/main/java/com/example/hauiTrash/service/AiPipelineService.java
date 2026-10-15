package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;

public interface AiPipelineService {
    AiResponseDetailsDTO predictAndEnrich(Integer requestId);
    AiResponseDetailsDTO getDetail(Integer requestId);

    AiResponseDetailsDTO submitFeedbackAndReturn(Integer id, String confirmedLabel, String feedbackType, String comment);
    java.util.List<com.example.hauiTrash.dto.HistoryItemDTO> getUserHistory();
}