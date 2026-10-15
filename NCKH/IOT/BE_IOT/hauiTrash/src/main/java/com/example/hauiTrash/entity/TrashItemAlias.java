package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "trash_item_aliases")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashItemAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trash_item_id", nullable = false)
    private TrashItem trashItem;

    @Column(name = "alias", length = 100, nullable = false, unique = true)
    private String alias;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}