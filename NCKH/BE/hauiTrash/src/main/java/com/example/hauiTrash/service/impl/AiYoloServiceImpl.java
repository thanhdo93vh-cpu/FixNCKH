package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.AiPredictRequestDTO;
import com.example.hauiTrash.dto.RealtimeDetectionResponse;
import com.example.hauiTrash.dto.TrashReasoningResultDTO;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import com.example.hauiTrash.entity.AiRequest;
import com.example.hauiTrash.entity.Detection;
import com.example.hauiTrash.repository.AiRequestRepository;
import com.example.hauiTrash.repository.DetectionRepository;
import com.example.hauiTrash.service.AiYoloService;
import com.example.hauiTrash.service.TrashKnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiYoloServiceImpl implements AiYoloService {


    private final RestTemplate restTemplate;


    private final AiRequestRepository aiRequestRepo;


    private final DetectionRepository detectionRepo;
    private final TrashKnowledgeGraphService trashKnowledgeGraphService;
    @Value("${ai.yolo.base-url:http://127.0.0.1:8000}")
    private String yoloBaseUrl;

    private static final float LOW_CONF_THRESHOLD = 0.5f;

    @Override
    @Transactional
    public YoloPredictResponseDTO predictAndSave(AiPredictRequestDTO req) {

        if (req == null || req.getAiRequestId() == null) {
            throw new IllegalArgumentException("aiRequestId is required");
        }

        AiRequest aiRequest = aiRequestRepo.findById(req.getAiRequestId())
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + req.getAiRequestId()));

        Map<String, Object> body = new HashMap<>();
        body.put("request_id", req.getAiRequestId());
        body.put("image_url", req.getImageUrl());
        body.put("conf", req.getConf());
        body.put("iou", req.getIou());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = yoloBaseUrl + "/predict-image-url";

        ResponseEntity<YoloPredictResponseDTO> resp = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                YoloPredictResponseDTO.class
        );

        YoloPredictResponseDTO yolo = resp.getBody();
        if (yolo == null) {
            throw new RuntimeException("YOLO response is null");
        }

        detectionRepo.deleteByAiRequest_Id(req.getAiRequestId());

        List<Detection> rows = new ArrayList<>();

        if (yolo.getDetections() != null && !yolo.getDetections().isEmpty()) {

            for (YoloPredictResponseDTO.DetectionDTO d : yolo.getDetections()) {

                String labelNorm = normalizeLabel(d.getLabel());

// Lấy labelDisplay từ Neo4j Knowledge Graph
                String labelDisplay = null;

                TrashReasoningResultDTO kgResult =
                        trashKnowledgeGraphService.reason(labelNorm);

                if (kgResult != null) {
                    labelDisplay = kgResult.getLabelDisplay();
                }

                // Nếu KG không có thì mới fallback về label YOLO
                if (labelDisplay == null || labelDisplay.isBlank()) {
                    labelDisplay = normalizeLabelDisplay(d.getLabelDisplay());
                }

                if (labelDisplay == null || labelDisplay.isBlank()) {
                    labelDisplay = labelNorm;
                }

                Float confidence = d.getConfidence();

                boolean needsConfirm = Boolean.TRUE.equals(d.getNeedsConfirm())
                        || (confidence != null && confidence < LOW_CONF_THRESHOLD);

                String status = needsConfirm ? "NEEDS_CONFIRM" : "DETECTED";

                // Cập nhật lại response YOLO
                d.setLabel(labelNorm);
                d.setLabelDisplay(labelDisplay);

                Detection detection = Detection.builder()
                        .aiRequest(aiRequest)
                        .label(labelNorm)
                        .labelDisplay(labelDisplay)
                        .confidence(confidence)
                        .annotatedUrl(
                                d.getAnnotatedUrl() != null
                                        ? d.getAnnotatedUrl()
                                        : yolo.getAnnotatedUrl()
                        )
                        .status(status)
                        .x1(d.getX1())
                        .y1(d.getY1())
                        .x2(d.getX2())
                        .y2(d.getY2())
                        .cropUrl(d.getCropUrl())
                        .build();

                rows.add(detection);
            }

            // Lưu vào MySQL và lấy entity đã có ID
            List<Detection> savedRows = detectionRepo.saveAll(rows);

            // Gán ID MySQL vào response YOLO
            for (int i = 0; i < savedRows.size(); i++) {
                Detection saved = savedRows.get(i);
                YoloPredictResponseDTO.DetectionDTO d = yolo.getDetections().get(i);

                if (saved.getId() != null) {
                    d.setId(saved.getId());
                }
            }
        }

        return yolo;
    }

    @Override
    public RealtimeDetectionResponse detectRealtime(MultipartFile file) {
        String url = yoloBaseUrl + "/predict-image-json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to read multipart file", e);
        }
        body.add("conf", 0.5f); // Ngưỡng confidence mặc định cho realtime
        body.add("iou", 0.45f);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<YoloPredictResponseDTO> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity, YoloPredictResponseDTO.class);
        return extractOptimalDetection(resp);
    }

    @Override
    public RealtimeDetectionResponse detectRealtime(byte[] imageBytes) {
        String url = yoloBaseUrl + "/predict-image-json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "frame.jpg"; 
            }
        });
        body.add("conf", 0.5f); 
        body.add("iou", 0.45f);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<YoloPredictResponseDTO> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity, YoloPredictResponseDTO.class);
        return extractOptimalDetection(resp);
    }

    private RealtimeDetectionResponse extractOptimalDetection(ResponseEntity<YoloPredictResponseDTO> resp) {
        YoloPredictResponseDTO yolo = resp.getBody();
        if (yolo == null || yolo.getDetections() == null || yolo.getDetections().isEmpty()) {
            return null;
        }

        // Lấy kết quả có độ tin cậy cao nhất
        YoloPredictResponseDTO.DetectionDTO best = yolo.getDetections().stream()
                .max(Comparator.comparing(YoloPredictResponseDTO.DetectionDTO::getConfidence))
                .orElse(null);

        if (best == null) return null;

        return RealtimeDetectionResponse.builder()
                .label(normalizeLabel(best.getLabel()))
                .labelDisplay(best.getLabelDisplay() != null ? best.getLabelDisplay() : normalizeLabel(best.getLabel()))
                .confidence(best.getConfidence())
                .x1(best.getX1())
                .y1(best.getY1())
                .x2(best.getX2())
                .y2(best.getY2())
                .build();
    }

    private String normalizeLabel(String label) {
        if (label == null) return null;
        String s = label.trim();
        if (s.isEmpty()) return null;
        return s.toLowerCase(Locale.ROOT);
    }

    private String normalizeLabelDisplay(String labelDisplay) {
        if (labelDisplay == null) return null;
        String s = labelDisplay.trim();
        return s.isEmpty() ? null : s;
    }
}