package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.ReviewQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewQueueRepository extends JpaRepository<ReviewQueue, Integer> {
    Optional<ReviewQueue> findByEntityTypeAndEntityIdAndStatus(String entityType, Integer entityId, String status);

    Optional<Object> findByRefTableAndRefIdAndQueueStatus(String detections, Integer detId, String pending);
    Optional<ReviewQueue> findTopByDetectionIdOrderByCreatedAtDesc(Integer detectionId);

}