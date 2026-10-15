package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "detection_feedback")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectionFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detection_id", nullable = false)
    private Detection detection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_request_id", nullable = false)
    private AiRequest aiRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "predicted_label", length = 120)
    private String predictedLabel;

    @Column(name = "predicted_confidence")
    private Float predictedConfidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predicted_trash_item_id")
    private TrashItem predictedTrashItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "predicted_trash_type_id")
    private TrashType predictedTrashType;

    @Column(name = "feedback_action", length = 30)
    private String feedbackAction;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "corrected_label", length = 120)
    private String correctedLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_trash_item_id")
    private TrashItem correctedTrashItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_trash_type_id")
    private TrashType correctedTrashType;

    @Column(name = "feedback_note", columnDefinition = "TEXT")
    private String feedbackNote;

    @Column(name = "review_status", length = 30)
    private String reviewStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_account_id")
    private Account reviewedByAccount;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "confirmed_label", length = 100)
    private String confirmedLabel;

    @Column(name = "feedback_type", length = 30)
    private String feedbackType;

    @Column(name = "original_label", length = 100)
    private String originalLabel;
}