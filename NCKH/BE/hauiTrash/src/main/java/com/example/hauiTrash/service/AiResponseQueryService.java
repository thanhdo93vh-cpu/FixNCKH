package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import org.springframework.stereotype.Service;

@Service
public interface AiResponseQueryService {
    AiResponseDetailsDTO getByAiRequestId(Integer aiRequestId);
}