package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserQuiz {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;                // Bài quiz nào

    private Integer score;              // Điểm đạt được

    @Column(name = "total_points")
    private Integer totalPoints;        // Tổng điểm

    private Double percentage;          // Phần trăm hoàn thành

    @Column(name = "is_passed")
    private Boolean isPassed;           // Có đậu không

    @Column(columnDefinition = "TEXT")
    private String answers;

    @Column(columnDefinition = "TEXT")
    private String shuffledMapping;     // mapping của câu hỏi đã random

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        completedAt = LocalDateTime.now();
    }
}