package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.TrashReasoningResultDTO;
import com.example.hauiTrash.service.TrashKnowledgeGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/kg")
@RequiredArgsConstructor
public class TrashKnowledgeGraphController {

    private final TrashKnowledgeGraphService trashKnowledgeGraphService;

    @GetMapping("/reason")
    public TrashReasoningResultDTO reason(
            @RequestParam String label
    ) {
        return trashKnowledgeGraphService.reason(label);
    }
}