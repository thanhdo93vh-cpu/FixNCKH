package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.Classification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassificationRepository extends JpaRepository<Classification, Integer> {
    Optional<Classification> findTopByDetectionIdOrderByIdDesc(Integer detectionId);
    List<Classification> findByDetectionId(Integer detectionId);
}