package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "certificate_code", unique = true, nullable = false)
    private String certificateCode;     // Mã chứng chỉ unique

    @Column(name = "total_quizzes_required")
    private Integer totalQuizzesRequired;  // Số bài cần đậu (mặc định 5)

    @Column(name = "total_quizzes_passed")
    private Integer totalQuizzesPassed;    // Số bài đã đậu

    @Column(columnDefinition = "TEXT")
    private String completedQuizIds;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @PrePersist
    protected void onCreate() {
        issuedAt = LocalDateTime.now();
        if (totalQuizzesRequired == null) totalQuizzesRequired = 5;
    }
}