package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.UserFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Integer> {

    List<UserFeedback> findByAiRequestId(Integer aiRequestId);

    List<UserFeedback> findByDetectionId(Integer detectionId);

    Optional<UserFeedback> findTopByDetectionIdOrderByCreatedAtDesc(Integer detectionId);

    @Query("SELECT f.feedbackType, COUNT(f) FROM UserFeedback f GROUP BY f.feedbackType")
    List<Object[]> countByFeedbackType();

    @Query("SELECT f.satisfactionLevel, COUNT(f) FROM UserFeedback f WHERE f.satisfactionLevel IS NOT NULL GROUP BY f.satisfactionLevel")
    List<Object[]> countBySatisfactionLevel();
}