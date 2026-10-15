package com.example.hauiTrash.iot.service.impl;

import com.example.hauiTrash.dto.AiPredictRequestDTO;
import com.example.hauiTrash.dto.AiRequestCreateResponseDTO;
import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.RealtimeDetectionResponse;
import com.example.hauiTrash.dto.YoloPredictResponseDTO;
import com.example.hauiTrash.iot.dto.IotAiRequestResponseDTO;
import com.example.hauiTrash.iot.dto.IotDetailResponseDTO;
import com.example.hauiTrash.iot.dto.IotPredictRequestDTO;
import com.example.hauiTrash.iot.dto.IotPredictResponseDTO;
import com.example.hauiTrash.iot.dto.IotRealtimeResponseDTO;
import com.example.hauiTrash.iot.service.ArduinoService;
import com.example.hauiTrash.iot.service.IotService;
import com.example.hauiTrash.service.AiPipelineService;
import com.example.hauiTrash.service.AiRequestService;
import com.example.hauiTrash.service.AiYoloService;
import com.example.hauiTrash.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IotServiceImpl implements IotService {

    private final CloudinaryService cloudinaryService;
    private final AiRequestService aiRequestService;
    private final AiYoloService aiYoloService;
    private final AiPipelineService aiPipelineService;
    private final ArduinoService arduinoService;

    @Override
    public IotAiRequestResponseDTO createAiRequest(MultipartFile file) {
        String cloudinaryUrl = cloudinaryService.uploadImage(file);
        AiRequestCreateResponseDTO created = aiRequestService.createAiRequest(cloudinaryUrl);

        return IotAiRequestResponseDTO.builder()
                .id(created.getId())
                .cloudinaryUrl(created.getCloudinaryUrl())
                .createdAt(created.getCreatedAt() != null ? created.getCreatedAt().toInstant() : null)
                .finishedAt(created.getFinishedAt() != null ? created.getFinishedAt().toInstant() : null)
                .accountId(created.getAccountId())
                .build();
    }

    @Override
    public IotDetailResponseDTO getDetail(Integer aiRequestId) {
        AiResponseDetailsDTO detail = aiPipelineService.getDetail(aiRequestId);
        return mapToIotDetail(detail);
    }

    @Override
    public IotPredictResponseDTO predict(IotPredictRequestDTO req) {
        AiPredictRequestDTO aiReq = new AiPredictRequestDTO();
        aiReq.setAiRequestId(req.getAiRequestId());
        aiReq.setImageUrl(req.getImageUrl());
        aiReq.setConf(req.getConf());
        aiReq.setIou(req.getIou());

        YoloPredictResponseDTO classified = aiYoloService.predictAndSave(aiReq);

        // Gửi lệnh mở nắp tương ứng sang Arduino
        if (classified.getDetections() != null && !classified.getDetections().isEmpty()) {
            String label = classified.getDetections().get(0).getLabel();
            arduinoService.sendMaterialCommand(label);
        }

        return mapToIotPredictResponse(classified);
    }

    @Override
    public IotRealtimeResponseDTO detectRealtime(MultipartFile file) {
        RealtimeDetectionResponse response = aiYoloService.detectRealtime(file);
        if (response == null) {
            return IotRealtimeResponseDTO.builder()
                    .label(null)
                    .labelDisplay("Chua phan loai")
                    .confidence(0.0f)
                    .build();
        }
        return mapToIotRealtimeResponse(response);
    }

    private IotPredictResponseDTO mapToIotPredictResponse(YoloPredictResponseDTO classified) {
        List<IotPredictResponseDTO.DetectionDTO> detections = Collections.emptyList();
        if (classified.getDetections() != null) {
            detections = classified.getDetections().stream()
                    .map(d -> IotPredictResponseDTO.DetectionDTO.builder()
                            .id(d.getId())
                            .label(d.getLabel())
                            .labelDisplay(d.getLabelDisplay())
                            .confidence(d.getConfidence())
                            .annotatedUrl(d.getAnnotatedUrl())
                            .x1(d.getX1())
                            .y1(d.getY1())
                            .x2(d.getX2())
                            .y2(d.getY2())
                            .cropUrl(d.getCropUrl())
                            .needsConfirm(d.getNeedsConfirm())
                            .build())
                    .collect(Collectors.toList());
        }

        return IotPredictResponseDTO.builder()
                .requestId(classified.getRequestId())
                .imageUrl(classified.getImageUrl())
                .annotatedUrl(classified.getAnnotatedUrl())
                .params(classified.getParams())
                .count(classified.getCount())
                .confidenceAvg(classified.getConfidenceAvg())
                .requiresConfirmation(classified.getRequiresConfirmation())
                .detections(detections)
                .build();
    }

    private IotDetailResponseDTO mapToIotDetail(AiResponseDetailsDTO detail) {
        List<IotDetailResponseDTO.DetectionDTO> detections = Collections.emptyList();
        if (detail.getDetections() != null) {
            detections = detail.getDetections().stream()
                    .map(this::mapDetailDetection)
                    .collect(Collectors.toList());
        }

        return IotDetailResponseDTO.builder()
                .id(detail.getId())
                .cloudinaryUrl(detail.getCloudinaryUrl())
                .createdAt(detail.getCreatedAt())
                .finishedAt(detail.getFinishedAt())
                .accountId(detail.getAccountId())
                .requiresConfirmation(detail.isRequiresConfirmation())
                .annotatedUrl(detail.getAnnotatedUrl())
                .count(detail.getCount())
                .confidenceAvg(detail.getConfidenceAvg())
                .detections(detections)
                .build();
    }

    private IotDetailResponseDTO.DetectionDTO mapDetailDetection(AiResponseDetailsDTO.DetectionDTO d) {
        IotDetailResponseDTO.DetailDTO detailDTO = null;
        if (d.getDetail() != null) {
            detailDTO = IotDetailResponseDTO.DetailDTO.builder()
                    .impact(d.getDetail().getImpact())
                    .toxicity(d.getDetail().getToxicity())
                    .safeSteps(d.getDetail().getSafeSteps())
                    .trashSteps(mapTrashSteps(d.getDetail().getTrashSteps()))
                    .build();
        }

        return IotDetailResponseDTO.DetectionDTO.builder()
                .id(d.getId())
                .label(d.getLabel())
                .labelDisplay(d.getLabelDisplay())
                .confidence(d.getConfidence())
                .annotatedUrl(d.getAnnotatedUrl())
                .trashItemId(d.getTrashItemId())
                .status(d.getStatus())
                .x1(d.getX1())
                .y1(d.getY1())
                .x2(d.getX2())
                .y2(d.getY2())
                .cropUrl(d.getCropUrl())
                .trashType(d.getTrashType())
                .material(d.getMaterial())
                .note(d.getNote())
                .action(d.getAction())
                .detail(detailDTO)
                .quantity(d.getQuantity())
                .build();
    }

    private List<IotDetailResponseDTO.TrashStepDTO> mapTrashSteps(
            List<AiResponseDetailsDTO.TrashStepDTO> source) {
        if (source == null) return Collections.emptyList();
        return source.stream()
                .map(s -> IotDetailResponseDTO.TrashStepDTO.builder()
                        .id(s.getId())
                        .label(s.getLabel())
                        .labelDisplay(s.getLabelDisplay())
                        .imageUrl(s.getImageUrl())
                        .build())
                .collect(Collectors.toList());
    }

    private IotRealtimeResponseDTO mapToIotRealtimeResponse(RealtimeDetectionResponse r) {
        return IotRealtimeResponseDTO.builder()
                .label(r.getLabel())
                .labelDisplay(r.getLabelDisplay())
                .confidence(r.getConfidence())
                .x1(r.getX1())
                .y1(r.getY1())
                .x2(r.getX2())
                .y2(r.getY2())
                .build();
    }
}
