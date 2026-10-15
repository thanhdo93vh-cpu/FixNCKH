package com.example.hauiTrash.iot.repository;

import com.example.hauiTrash.entity.AiRequest;
import com.example.hauiTrash.entity.Detection;
import com.example.hauiTrash.repository.AiRequestRepository;
import com.example.hauiTrash.repository.DetectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class IotRepository {

    private final AiRequestRepository aiRequestRepo;
    private final DetectionRepository detectionRepo;

    public AiRequest saveAiRequest(AiRequest aiRequest) {
        return aiRequestRepo.save(aiRequest);
    }

    public Optional<AiRequest> findAiRequestById(Integer id) {
        return aiRequestRepo.findById(id);
    }

    public List<Detection> findDetectionsByAiRequestId(Integer aiRequestId) {
        return detectionRepo.findByAiRequest_Id(aiRequestId);
    }

    public void deleteDetectionsByAiRequestId(Integer aiRequestId) {
        detectionRepo.deleteByAiRequest_Id(aiRequestId);
    }

    public List<Detection> saveAllDetections(List<Detection> detections) {
        return detectionRepo.saveAll(detections);
    }
}
