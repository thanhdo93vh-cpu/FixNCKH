package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.service.AiResponseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai_response")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiResponseController {
//bản chưa dùng gemini
    private final AiResponseQueryService aiResponseQueryService;

    @GetMapping("/{aiRequestId}")
    public ResponseEntity<AiResponseDetailsDTO> getResult(@PathVariable Integer aiRequestId) {
        return ResponseEntity.ok(aiResponseQueryService.getByAiRequestId(aiRequestId));
    }
}