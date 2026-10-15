package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "review_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "queue_type", length = 50, nullable = false)
    private String queueType;

    @Column(name = "ref_table", length = 50, nullable = false)
    private String refTable;

    @Column(name = "ref_id", nullable = false)
    private Integer refId;

    @Column(name = "ai_request_id")
    private Integer aiRequestId;

    @Column(name = "detection_id")
    private Integer detectionId;

    @Column(name = "trash_item_id")
    private Integer trashItemId;

    @Column(name = "suggested_label", length = 120)
    private String suggestedLabel;

    @Column(name = "suggested_trash_type_id")
    private Integer suggestedTrashTypeId;

    @Column(name = "suggested_confidence")
    private Float suggestedConfidence;

    @Column(name = "reason_code", length = 50, nullable = false)
    private String reasonCode;

    @Column(name = "queue_status", length = 30)
    private String queueStatus;

    @Column(name = "assigned_to_account_id")
    private Integer assignedToAccountId;

    @Column(name = "resolved_by_account_id")
    private Integer resolvedByAccountId;

    @Column(name = "resolved_note", columnDefinition = "TEXT")
    private String resolvedNote;

    @Column(name = "entity_id")
    private Integer entityId;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void prePersist() {
        if (queueType == null) queueType = "AI_REVIEW";
        if (queueStatus == null) queueStatus = "PENDING";
        if (status == null) status = "PENDING";
        if (priority == null) priority = 1;
        if (reasonCode == null) reasonCode = "UNKNOWN";

        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}