package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.TrashReasoningResultDTO;
import com.example.hauiTrash.repository.neo4j.TrashReasoningRepository;
import com.example.hauiTrash.service.TrashKnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrashKnowledgeGraphServiceImpl
        implements TrashKnowledgeGraphService {

    private final TrashReasoningRepository trashReasoningRepository;

    @Override
    public TrashReasoningResultDTO reason(String label) {
        System.out.println("========== KG REASON ==========");
        System.out.println("LABEL NHẬN ĐƯỢC: [" + label + "]");

        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException(
                    "Label rác không được để trống"
            );
        }

        return trashReasoningRepository
                .reasonByLabel(label.trim())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy label trong Knowledge Graph: "
                                        + label
                        )
                );
    }
}