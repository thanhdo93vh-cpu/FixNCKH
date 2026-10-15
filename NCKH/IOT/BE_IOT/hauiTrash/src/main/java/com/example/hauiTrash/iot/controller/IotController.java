package com.example.hauiTrash.iot.controller;

import com.example.hauiTrash.iot.dto.IotAiRequestResponseDTO;
import com.example.hauiTrash.iot.dto.IotDetailResponseDTO;
import com.example.hauiTrash.iot.dto.IotPredictRequestDTO;
import com.example.hauiTrash.iot.dto.IotPredictResponseDTO;
import com.example.hauiTrash.iot.dto.IotRealtimeResponseDTO;
import com.example.hauiTrash.iot.service.IotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/iot")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class IotController {

    private final IotService iotService;

    @PostMapping(value = "/ai_request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IotAiRequestResponseDTO> createAiRequest(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(iotService.createAiRequest(file));
    }

    @GetMapping("/ai_response/{id}/detail")
    public ResponseEntity<IotDetailResponseDTO> getDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(iotService.getDetail(id));
    }

    @PostMapping("/predict")
    public ResponseEntity<IotPredictResponseDTO> predict(@RequestBody IotPredictRequestDTO req) {
        return ResponseEntity.ok(iotService.predict(req));
    }

    @PostMapping("/realtime")
    public ResponseEntity<IotRealtimeResponseDTO> classifyRealtime(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(iotService.detectRealtime(file));
    }
}
