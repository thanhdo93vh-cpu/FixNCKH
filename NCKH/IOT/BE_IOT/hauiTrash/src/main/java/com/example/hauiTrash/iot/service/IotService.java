package com.example.hauiTrash.iot.service;

import com.example.hauiTrash.iot.dto.IotAiRequestResponseDTO;
import com.example.hauiTrash.iot.dto.IotDetailResponseDTO;
import com.example.hauiTrash.iot.dto.IotPredictRequestDTO;
import com.example.hauiTrash.iot.dto.IotPredictResponseDTO;
import com.example.hauiTrash.iot.dto.IotRealtimeResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface IotService {
    IotAiRequestResponseDTO createAiRequest(MultipartFile file);

    IotDetailResponseDTO getDetail(Integer aiRequestId);

    IotPredictResponseDTO predict(IotPredictRequestDTO req);

    IotRealtimeResponseDTO detectRealtime(MultipartFile file);
}
