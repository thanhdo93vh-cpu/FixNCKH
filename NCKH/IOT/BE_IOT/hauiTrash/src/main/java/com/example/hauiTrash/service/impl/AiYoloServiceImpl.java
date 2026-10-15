package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.AiPredictRequestDTO;
import com.example.hauiTrash.dto.MaterialPredictResponseDTO;
import com.example.hauiTrash.dto.RealtimeDetectionResponse;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import com.example.hauiTrash.entity.AiRequest;
import com.example.hauiTrash.entity.Detection;
import com.example.hauiTrash.repository.AiRequestRepository;
import com.example.hauiTrash.repository.DetectionRepository;
import com.example.hauiTrash.service.AiYoloService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiYoloServiceImpl implements AiYoloService {

    private final RestTemplate restTemplate;
    private final AiRequestRepository aiRequestRepo;
    private final DetectionRepository detectionRepo;

    @Value("${ai.resnet.base-url:${ai.yolo.base-url:http://127.0.0.1:8000}}")
    private String aiBaseUrl;

    @Override
    @Transactional
    public YoloPredictResponseDTO predictAndSave(AiPredictRequestDTO req) {
        if (req == null || req.getAiRequestId() == null) {
            throw new IllegalArgumentException("aiRequestId is required");
        }

        AiRequest aiRequest = aiRequestRepo.findById(req.getAiRequestId())
                .orElseThrow(() -> new RuntimeException("AiRequest not found: " + req.getAiRequestId()));

        String imageUrl = firstNonBlank(req.getImageUrl(), aiRequest.getCloudinaryUrl());
        if (imageUrl == null) {
            throw new IllegalArgumentException("imageUrl is required");
        }

        MaterialPredictResponseDTO material = classifyByImageUrl(req.getAiRequestId(), imageUrl);

        detectionRepo.deleteByAiRequest_Id(req.getAiRequestId());

        String label = normalizeMaterialLabel(material.getLabel());
        boolean needsConfirm = label == null || "unknown".equalsIgnoreCase(label);

        Detection savedDetection = null;
        if (label != null) {
            Detection row = Detection.builder()
                    .aiRequest(aiRequest)
                    .label(label)
                    .labelDisplay(materialDisplay(label))
                    .confidence(null)
                    .annotatedUrl(null)
                    .status(needsConfirm ? "NEEDS_CONFIRM" : "DETECTED")
                    .build();
            savedDetection = detectionRepo.save(row);
        }

        aiRequest.setFinishedAt(Instant.now());
        aiRequestRepo.save(aiRequest);

        return buildLegacyResponse(req.getAiRequestId(), imageUrl, savedDetection, needsConfirm);
    }

    @Override
    public RealtimeDetectionResponse detectRealtime(MultipartFile file) {
        try {
            return classifyBytes(file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read multipart file", e);
        }
    }

    @Override
    public RealtimeDetectionResponse detectRealtime(byte[] imageBytes) {
        return classifyBytes(imageBytes, "frame.jpg");
    }

    private MaterialPredictResponseDTO classifyByImageUrl(Integer requestId, String imageUrl) {
        String url = aiBaseUrl + "/classify-image-url";
        Map<String, Object> body = Map.of(
                "request_id", requestId,
                "image_url", imageUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<MaterialPredictResponseDTO> resp = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                MaterialPredictResponseDTO.class
        );

        MaterialPredictResponseDTO material = resp.getBody();
        if (!resp.getStatusCode().is2xxSuccessful() || material == null) {
            throw new RuntimeException("ResNet material classification failed");
        }
        return material;
    }

    private RealtimeDetectionResponse classifyBytes(byte[] imageBytes, String filename) {
        String url = aiBaseUrl + "/classify-image";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return firstNonBlank(filename, "frame.jpg");
            }
        });

        ResponseEntity<MaterialPredictResponseDTO> resp = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                MaterialPredictResponseDTO.class
        );

        MaterialPredictResponseDTO material = resp.getBody();
        if (!resp.getStatusCode().is2xxSuccessful() || material == null) {
            throw new RuntimeException("ResNet material classification failed");
        }

        String label = normalizeMaterialLabel(material.getLabel());
        return RealtimeDetectionResponse.builder()
                .label(label)
                .labelDisplay(materialDisplay(label))
                .confidence(null)
                .build();
    }

    private YoloPredictResponseDTO buildLegacyResponse(
            Integer requestId,
            String imageUrl,
            Detection savedDetection,
            boolean needsConfirm
    ) {
        YoloPredictResponseDTO response = new YoloPredictResponseDTO();
        response.setRequestId(requestId);
        response.setImageUrl(imageUrl);
        response.setAnnotatedUrl(null);
        response.setParams(Map.of("model", "resnet50"));
        response.setCount(savedDetection != null ? 1 : 0);
        response.setConfidenceAvg(0f);
        response.setRequiresConfirmation(needsConfirm);

        if (savedDetection == null) {
            response.setDetections(List.of());
            return response;
        }

        YoloPredictResponseDTO.DetectionDTO detection = new YoloPredictResponseDTO.DetectionDTO();
        detection.setId(savedDetection.getId());
        detection.setLabel(savedDetection.getLabel());
        detection.setLabelDisplay(savedDetection.getLabelDisplay());
        detection.setConfidence(savedDetection.getConfidence());
        detection.setAnnotatedUrl(savedDetection.getAnnotatedUrl());
        detection.setNeedsConfirm(needsConfirm);
        response.setDetections(List.of(detection));
        return response;
    }

    private String normalizeMaterialLabel(String label) {
        String normalized = normalizeLabel(label);
        if ("mental".equals(normalized)) {
            return "metal";
        }
        return normalized;
    }

    private String materialDisplay(String label) {
        if (label == null) return "Kh\u00f4ng x\u00e1c \u0111\u1ecbnh";
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "plastic" -> "Nh\u1ef1a";
            case "metal" -> "Kim lo\u1ea1i";
            case "glass" -> "Th\u1ee7y tinh";
            case "paper" -> "Gi\u1ea5y";
            case "unknown" -> "Kh\u00f4ng x\u00e1c \u0111\u1ecbnh";
            default -> {
                String s = label.replace('_', ' ').trim();
                yield s.isEmpty() ? "Kh\u00f4ng x\u00e1c \u0111\u1ecbnh" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }

    private String normalizeLabel(String label) {
        if (label == null) return null;
        String s = label.trim();
        if (s.isEmpty()) return null;
        return s.toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
