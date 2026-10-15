package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryService {
    String uploadImage(MultipartFile file);

    interface AiResponseQueryService {
        AiResponseDetailsDTO getByAiRequestId(Integer aiRequestId);
    }
}
