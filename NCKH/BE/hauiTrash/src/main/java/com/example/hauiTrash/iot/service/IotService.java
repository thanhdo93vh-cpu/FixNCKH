package com.example.hauiTrash.iot.service;

import com.example.hauiTrash.iot.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
@Service
public interface IotService {
    IotOpenResponseDTO openBin(IotOpenRequestDTO req);

    IotAiRequestResponseDTO createAiRequest(MultipartFile file);

    IotDetailResponseDTO getDetail(Integer aiRequestId);

    IotPredictResponseDTO predict(IotPredictRequestDTO req);

    IotRealtimeResponseDTO detectRealtime(MultipartFile file);
}
