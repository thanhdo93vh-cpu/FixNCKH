package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.TrashItemEnrichResponseDTO;
import com.example.hauiTrash.service.TrashItemEnrichService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trash-items")
public class TrashItemEnrichController {
    @Autowired
    private TrashItemEnrichService enrichService;

    @PostMapping("/{id}/enrich")
    public ResponseEntity<TrashItemEnrichResponseDTO> enrich(@PathVariable("id") Integer id) {
        return ResponseEntity.ok(enrichService.enrichTrashItem(id));
    }

    @PostMapping("/{id}/enrich-all")
    public ResponseEntity<AiResponseDetailsDTO> enrichAll(@PathVariable("id") Integer id) {
        return ResponseEntity.ok(enrichService.enrichAllByAiRequestId(id));
    }
}
