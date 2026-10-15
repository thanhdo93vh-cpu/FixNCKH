package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.KnowledgeEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeEmbeddingRepository extends JpaRepository<KnowledgeEmbedding, Integer> {

    Optional<KnowledgeEmbedding> findByLabel(String label);

    @Query(value = """
        SELECT * FROM knowledge_embeddings 
        WHERE embedding <-> cast(:embedding as vector) < :threshold
        ORDER BY embedding <-> cast(:embedding as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<KnowledgeEmbedding> findSimilarEmbeddings(
            @Param("embedding") String embeddingJson,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );

    List<KnowledgeEmbedding> findByLabelContainingIgnoreCase(String keyword);
}