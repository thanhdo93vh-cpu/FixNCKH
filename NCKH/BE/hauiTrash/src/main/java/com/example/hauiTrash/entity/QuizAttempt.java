package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(columnDefinition = "TEXT")
    private String questionOrder;      // JSON: [3,1,4,2,5] - thứ tự câu hỏi đã random

    @Column(columnDefinition = "TEXT")
    private String optionsMapping;     // JSON mapping đáp án đã random

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "is_completed")
    private Boolean isCompleted;

    @PrePersist
    protected void onCreate() {
        startedAt = LocalDateTime.now();
        isCompleted = false;
    }
}