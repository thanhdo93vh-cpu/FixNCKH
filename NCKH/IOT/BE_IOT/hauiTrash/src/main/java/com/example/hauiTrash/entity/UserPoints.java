package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "user_points")
public class UserPoints {
    @Id
    @Column(name = "account_id")
    private Integer accountId;

    @Column(name = "total_points")
    private Integer totalPoints;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (totalPoints == null) totalPoints = 0;
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}