package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.entity.AiRequest;
import com.example.hauiTrash.entity.Detection;
import com.example.hauiTrash.repository.AiRequestRepository;
import com.example.hauiTrash.repository.DetectionRepository;
import com.example.hauiTrash.service.AiResponseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiResponseQueryServiceImpl implements AiResponseQueryService {

    private final AiRequestRepository aiRequestRepository;
    private final DetectionRepository detectionRepository;

    @Transactional(readOnly = true)
    @Override
    public AiResponseDetailsDTO getByAiRequestId(Integer aiRequestId) {

        AiRequest req = aiRequestRepository.findById(aiRequestId)
                .orElseThrow(() -> new RuntimeException("ai_request not found: " + aiRequestId));

        List<Detection> dets = detectionRepository.findByAiRequest_Id(aiRequestId);

        float avg = 0f;
        if (!dets.isEmpty()) {
            float sum = 0f;
            int c = 0;
            for (Detection d : dets) {
                if (d.getConfidence() != null) {
                    sum += d.getConfidence();
                    c++;
                }
            }
            avg = c == 0 ? 0f : (sum / c);
        }

        // annotatedUrl: lấy cái đầu tiên khác null (thường giống nhau)
        String annotatedUrl = null;
        for (Detection d : dets) {
            if (d.getAnnotatedUrl() != null && !d.getAnnotatedUrl().isBlank()) {
                annotatedUrl = d.getAnnotatedUrl();
                break;
            }
        }

        List<AiResponseDetailsDTO.DetectionDTO> detDTOs = dets.stream().map(d ->
                AiResponseDetailsDTO.DetectionDTO.builder()
                        .id(d.getId())
                        .label(d.getLabel())
                        .labelDisplay(d.getLabelDisplay())
                        .confidence(d.getConfidence())
                        .annotatedUrl(d.getAnnotatedUrl())
                        .build()
        ).toList();

        return AiResponseDetailsDTO.builder()
                .id(req.getId())
                .cloudinaryUrl(req.getCloudinaryUrl())
                .createdAt(req.getCreatedAt())
                .finishedAt(req.getFinishedAt())
                .accountId(req.getAccount() != null ? req.getAccount().getId() : null)

                .annotatedUrl(annotatedUrl)
                .count(dets.size())
                .confidenceAvg(avg)
                .detections(detDTOs)
                .build();
    }
}