package com.example.hauiTrash.entity;

import com.example.hauiTrash.entity.TrashItem;
import com.example.hauiTrash.entity.TrashType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.GenerationTime;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "trash_item_mappings",
        indexes = {
                @Index(name = "ix_tim_trash_item_id", columnList = "trash_item_id"),
                @Index(name = "ux_tim_active_trash_item_id", columnList = "active_trash_item_id", unique = true),
                @Index(name = "ix_tim_trash_type_id", columnList = "trash_type_id")
        }
)
public class TrashItemMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trash_item_id", nullable = false)
    private TrashItem trashItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trash_type_id")
    private TrashType trashType;

    @Column(name = "mapping_source", nullable = false, length = 30)
    private String mappingSource; // MANUAL / AI_SUGGESTED / HYBRID

    @Column(name = "mapping_confidence")
    private Float mappingConfidence;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ✅ GENERATED ALWAYS STORED -> chỉ đọc, DB tự tính
    @Generated(GenerationTime.ALWAYS)
    @Column(name = "active_trash_item_id", insertable = false, updatable = false)
    private Integer activeTrashItemId;

    @PrePersist
    void prePersist() {
        if (mappingSource == null) mappingSource = "MANUAL";
        if (isActive == null) isActive = true;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
