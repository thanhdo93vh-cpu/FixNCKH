package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.Detection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetectionRepository extends JpaRepository<Detection, Integer> {
    List<Detection> findByAiRequest_Id(Integer aiRequestId);
    void deleteByAiRequest_Id(Integer aiRequestId);
}
