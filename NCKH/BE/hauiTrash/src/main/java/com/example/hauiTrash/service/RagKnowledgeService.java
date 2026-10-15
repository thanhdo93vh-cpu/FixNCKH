package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.SimilarKnowledgeDTO;
import com.example.hauiTrash.entity.TrashItemKnowledge;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
@Service
public interface RagKnowledgeService {

    /**
     * Tìm kiếm knowledge tương tự bằng RAG
     */
    List<SimilarKnowledgeDTO> findSimilarKnowledge(String query, int limit);

    /**
     * Tạo embedding cho text
     */
    String generateEmbedding(String text);

    /**
     * Tạo hoặc cập nhật embedding cho knowledge
     */
    void updateEmbedding(Integer knowledgeId);

    /**
     * Tìm kiếm và build context cho prompt
     */
    String buildRagContext(String label, String labelDisplay);
}