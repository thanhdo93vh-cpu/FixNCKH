package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_feedback")
public class UserFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_request_id", nullable = false)
    private AiRequest aiRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detection_id")
    private Detection detection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    // Mức độ hài lòng
    @Column(name = "satisfaction_level", length = 20)
    private String satisfactionLevel;  // SATISFIED / UNSATISFIED

    @Column(name = "satisfaction_reason", columnDefinition = "TEXT")
    private String satisfactionReason;

    // Feedback chi tiết
    @Column(name = "feedback_type", length = 30)
    private String feedbackType;  // CONFIRMED / CORRECTED / REJECTED / CUSTOM

    @Column(name = "selected_code", length = 10)
    private String selectedCode;   // YES, NO, A, B, CUSTOM

    @Column(name = "custom_label", length = 200)
    private String customLabel;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    // Thông tin gốc từ AI
    @Column(name = "original_label", length = 120)
    private String originalLabel;

    @Column(name = "original_confidence")
    private Float originalConfidence;

    @Column(name = "alternative_label", length = 120)
    private String alternativeLabel;

    @Column(name = "alternative_confidence")
    private Float alternativeConfidence;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}