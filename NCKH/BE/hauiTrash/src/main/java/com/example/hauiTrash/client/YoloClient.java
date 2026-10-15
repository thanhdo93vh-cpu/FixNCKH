package com.example.hauiTrash.client;

import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class YoloClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.yolo.base-url:http://127.0.0.1:8000}")
    private String baseUrl;

    public YoloPredictResponseDTO predictByImage(
            Integer requestId,
            byte[] imageBytes,
            String fileName,
            float conf,
            float iou) {

        String url = baseUrl + "/predict-image";

        // File ảnh
        ByteArrayResource imageResource = new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        // Multipart body
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        body.add("file", imageResource);
        body.add("request_id", requestId);
        body.add("conf", conf);
        body.add("iou", iou);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> request =
                new HttpEntity<>(body, headers);

        ResponseEntity<YoloPredictResponseDTO> res =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        request,
                        YoloPredictResponseDTO.class
                );

        if (!res.getStatusCode().is2xxSuccessful()
                || res.getBody() == null) {
            throw new RuntimeException("YOLO predict failed");
        }

        return res.getBody();
    }
}