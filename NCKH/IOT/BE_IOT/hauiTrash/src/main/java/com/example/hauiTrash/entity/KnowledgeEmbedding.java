package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity
@Table(name = "knowledge_embeddings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_id", nullable = false)
    private TrashItemKnowledge knowledge;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "label_display", length = 180)
    private String labelDisplay;

    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embeddingJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}