package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "classifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Classification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detection_id", nullable = false)
    private Detection detection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trash_item_id")
    private TrashItem trashItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trash_type_id")
    private TrashType trashType;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "confidence")
    private Float confidence;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}