package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiRequestCreateResponseDTO;

public interface AiRequestService {
    AiRequestCreateResponseDTO createAiRequest(String cloudinaryUrl);
}
