package com.example.hauiTrash.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hauiTrash.entity.KnowledgeEmbedding;

public interface KnowledgeEmbeddingRepository extends JpaRepository<KnowledgeEmbedding, Integer> {

    Optional<KnowledgeEmbedding> findByLabel(String label);

    @EntityGraph(attributePaths = {"knowledge", "knowledge.trashItem"})
    List<KnowledgeEmbedding> findByEmbeddingJsonIsNotNull();

    List<KnowledgeEmbedding> findByLabelContainingIgnoreCase(String keyword);
}
