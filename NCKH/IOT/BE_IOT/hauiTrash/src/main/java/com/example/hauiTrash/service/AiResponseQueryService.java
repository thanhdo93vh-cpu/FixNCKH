package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;

public interface AiResponseQueryService {
    AiResponseDetailsDTO getByAiRequestId(Integer aiRequestId);
}