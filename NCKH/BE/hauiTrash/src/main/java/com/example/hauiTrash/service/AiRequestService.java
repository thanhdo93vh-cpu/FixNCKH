package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiRequestCreateResponseDTO;
import org.springframework.stereotype.Service;

@Service
public interface AiRequestService {
    AiRequestCreateResponseDTO createAiRequest(String cloudinaryUrl);
}
