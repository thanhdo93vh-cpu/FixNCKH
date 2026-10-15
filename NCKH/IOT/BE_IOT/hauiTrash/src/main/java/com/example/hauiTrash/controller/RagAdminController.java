package com.example.hauiTrash.controller;

import com.example.hauiTrash.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin endpoint for RAG (Retrieval-Augmented Generation) management.
 *
 * - POST /api/admin/rag/rebuild-embeddings : Rebuild all knowledge embeddings
 * - POST /api/admin/rag/reload-cache       : Reload embedding cache from DB
 */
@RestController
@RequestMapping("/api/admin/rag")
@RequiredArgsConstructor
public class RagAdminController {

    private final RagService ragService;

    /**
     * Rebuild embeddings for all active trash_item_knowledge entries.
     * This calls Gemini text-embedding-004 API for each knowledge entry.
     * Should be called once to populate embeddings, then periodically when knowledge changes.
     */
    @PostMapping("/rebuild-embeddings")
    public ResponseEntity<Map<String, Object>> rebuildEmbeddings() {
        int count = ragService.rebuildAllEmbeddings();
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "RAG embeddings rebuilt successfully",
                "embeddingsGenerated", count
        ));
    }

    /**
     * Reload embedding cache from database (no API calls, just reload).
     */
    @PostMapping("/reload-cache")
    public ResponseEntity<Map<String, Object>> reloadCache() {
        ragService.loadEmbeddingsToCache();
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "RAG cache reloaded from database"
        ));
    }
}
