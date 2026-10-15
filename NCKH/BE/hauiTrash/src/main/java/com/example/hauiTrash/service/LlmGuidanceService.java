package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.TrashReasoningResultDTO;
import org.springframework.stereotype.Service;

@Service
public interface LlmGuidanceService {
    String generateGuidance(TrashReasoningResultDTO context);
}
