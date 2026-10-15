package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.KnowledgeViewDTO;

public interface TrashKnowledgeService {

    /**
     * Trả về knowledge nếu đã có (active).
     * Nếu chưa có -> return null (Controller sẽ trả 204 No Content).
     */
    KnowledgeViewDTO getKnowledgeIfExists(Integer trashItemId);

    /**
     * Generate knowledge (tạo mới). Nếu force=false và đã có -> trả luôn cái hiện tại.
     */
    KnowledgeViewDTO generateKnowledge(Integer trashItemId, boolean force);
}
