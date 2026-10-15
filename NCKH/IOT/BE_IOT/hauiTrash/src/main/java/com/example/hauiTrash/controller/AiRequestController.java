package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.AiRequestCreateResponseDTO;
import com.example.hauiTrash.service.AiRequestService;
import com.example.hauiTrash.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai_request")
@CrossOrigin(origins = "*")
public class AiRequestController {
    @Autowired
    private CloudinaryService cloudinaryService;
    @Autowired
    private AiRequestService aiRequestService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AiRequestCreateResponseDTO> createAiRequest(
            @RequestParam("file") MultipartFile file    ) {
        String cloudinaryUrl = cloudinaryService.uploadImage(file);

        AiRequestCreateResponseDTO response =
                aiRequestService.createAiRequest(cloudinaryUrl);

        return ResponseEntity.ok(response);
    }
}