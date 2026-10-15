package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiPredictRequestDTO;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;

import com.example.hauiTrash.dto.RealtimeDetectionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface AiYoloService {
    YoloPredictResponseDTO predictAndSave(AiPredictRequestDTO req);
    RealtimeDetectionResponse detectRealtime(MultipartFile file);
    RealtimeDetectionResponse detectRealtime(byte[] imageBytes);
}
