package com.example.hauiTrash.client;

import com.example.hauiTrash.dto.MaterialPredictResponseDTO;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class YoloClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.resnet.base-url:${ai.yolo.base-url:http://127.0.0.1:8000}}")
    private String baseUrl;

    public YoloPredictResponseDTO predictByImageUrl(Integer requestId, String imageUrl, float conf, float iou) {
        String url = baseUrl + "/classify-image-url";

        Map<String, Object> body = Map.of(
                "request_id", requestId,
                "image_url", imageUrl
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<MaterialPredictResponseDTO> res = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                MaterialPredictResponseDTO.class
        );

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new RuntimeException("ResNet material classification failed");
        }
        return toLegacyResponse(requestId, imageUrl, res.getBody());
    }

    private YoloPredictResponseDTO toLegacyResponse(Integer requestId, String imageUrl, MaterialPredictResponseDTO material) {
        String label = normalizeMaterialLabel(material.getLabel());

        YoloPredictResponseDTO response = new YoloPredictResponseDTO();
        response.setRequestId(requestId);
        response.setImageUrl(imageUrl);
        response.setAnnotatedUrl(null);
        response.setParams(Map.of("model", "resnet50"));
        response.setCount(label != null ? 1 : 0);
        response.setConfidenceAvg(0f);
        response.setRequiresConfirmation(label == null || "unknown".equalsIgnoreCase(label));

        if (label != null) {
            YoloPredictResponseDTO.DetectionDTO detection = new YoloPredictResponseDTO.DetectionDTO();
            detection.setLabel(label);
            detection.setLabelDisplay(fallbackMaterialDisplay(label));
            detection.setConfidence(null);
            detection.setAnnotatedUrl(null);
            detection.setNeedsConfirm("unknown".equalsIgnoreCase(label));
            response.setDetections(List.of(detection));
        } else {
            response.setDetections(List.of());
        }
        return response;
    }

    private String normalizeMaterialLabel(String label) {
        if (label == null) return null;
        String normalized = label.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return null;
        return "mental".equals(normalized) ? "metal" : normalized;
    }

    private String fallbackMaterialDisplay(String label) {
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
}
