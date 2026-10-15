package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.DetectionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DetectionFeedbackRepository extends JpaRepository<DetectionFeedback, Integer> {
    Optional<DetectionFeedback> findTopByOriginalLabelOrderByIdDesc(String originalLabel);
    List<DetectionFeedback> findByOriginalLabelAndFeedbackTypeIgnoreCase(String originalLabel, String feedbackType);


}