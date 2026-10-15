package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.TrashItemEnrichResponseDTO;

public interface TrashItemEnrichService {
    TrashItemEnrichResponseDTO enrichTrashItem(Integer trashItemId);

    AiResponseDetailsDTO enrichAllByAiRequestId(Integer id);
}
