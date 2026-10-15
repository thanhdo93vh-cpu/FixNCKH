package com.example.hauiTrash.iot.controller;

import com.example.hauiTrash.iot.dto.*;
import com.example.hauiTrash.iot.service.IotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/iot")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class IotController {
    private final IotService iotService;
    @PostMapping("/open")
    public ResponseEntity<IotOpenResponseDTO> open(@Valid @RequestBody IotOpenRequestDTO req) {
        IotOpenResponseDTO result = iotService.openBin(req);
        return ResponseEntity.status(result.isCommandSent() ? 200 : 503).body(result);
    }

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
