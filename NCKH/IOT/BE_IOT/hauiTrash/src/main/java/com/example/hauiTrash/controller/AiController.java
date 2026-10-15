package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.AiPredictRequestDTO;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import com.example.hauiTrash.entity.TrashStep;
import com.example.hauiTrash.repository.TrashStepRepository;
import com.example.hauiTrash.dto.RealtimeDetectionResponse;
import com.example.hauiTrash.service.AiYoloService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai_response")
@CrossOrigin(origins = "*")
public class AiController {
    @Autowired
    private AiYoloService aiYoloService;


    @PostMapping("/predict")
    public ResponseEntity<YoloPredictResponseDTO> predict(@RequestBody AiPredictRequestDTO req) {
        return ResponseEntity.ok(aiYoloService.predictAndSave(req));
    }

    @PostMapping("/realtime")
    public ResponseEntity<RealtimeDetectionResponse> detectRealtime(@RequestParam("file") MultipartFile file) {
        RealtimeDetectionResponse response = aiYoloService.detectRealtime(file);
        if (response == null) {
            return ResponseEntity.ok(RealtimeDetectionResponse.builder()
                    .label(null)
                    .labelDisplay("Không phát hiện rác")
                    .confidence(0.0f)
                    .build());
        }
        return ResponseEntity.ok(response);
    }
}
