package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.AiResponseDetailsDTO;
import com.example.hauiTrash.dto.FeedbackRequest;
import com.example.hauiTrash.dto.KnowledgeViewDTO;
import com.example.hauiTrash.service.AiPipelineService;
import com.example.hauiTrash.service.TrashKnowledgeService;
import lombok.RequiredArgsConstructor;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AiPipelineController {
    @Autowired
    private AiPipelineService aiPipelineService;
    @Autowired
    private TrashKnowledgeService knowledgeService;
    @PostMapping(
            value = "/ai_requests/{id}/predict",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<AiResponseDetailsDTO> predict(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            return ResponseEntity.ok(
                    aiPipelineService.predictAndEnrich(
                            id,
                            file.getBytes(),
                            file.getOriginalFilename()
                    )
            );
        } catch (IOException e) {
            throw new RuntimeException("Không thể đọc file ảnh: " + e.getMessage(), e);
        }
    }

    @GetMapping("/ai_response/{id}/detail")
    public ResponseEntity<AiResponseDetailsDTO> getDetail(@PathVariable Integer id) {
        return ResponseEntity.ok(aiPipelineService.getDetail(id));
    }
    @GetMapping("/api/trash-items/{trashItemId}/knowledge")
    public ResponseEntity<KnowledgeViewDTO> getKnowledge(@PathVariable Integer trashItemId) {
        KnowledgeViewDTO dto = knowledgeService.getKnowledgeIfExists(trashItemId);
        if (dto == null) return ResponseEntity.noContent().build(); // 204
        return ResponseEntity.ok(dto); // 200
    }
    @PostMapping("/api/trash-items/{trashItemId}/knowledge:generate")
    public KnowledgeViewDTO generate(@PathVariable Integer trashItemId,
                                     @RequestParam(defaultValue = "false") boolean force) {
        return knowledgeService.generateKnowledge(trashItemId, force);
    }
    @PostMapping("/detections/{id}/feedback")
    public ResponseEntity<AiResponseDetailsDTO> submitFeedback(
            @PathVariable Integer id,
            @RequestBody FeedbackRequest req
    ) {
        AiResponseDetailsDTO result = aiPipelineService.submitFeedbackAndReturn(
                id,
                req.getConfirmedLabel(),
                req.getFeedbackType(),
                req.getComment()
        );

        System.out.println("result = " + result);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/user/history")
    public ResponseEntity<com.example.hauiTrash.dto.ApiResponse<java.util.List<com.example.hauiTrash.dto.HistoryItemDTO>>> getMyHistory() {
        return ResponseEntity.ok(com.example.hauiTrash.dto.ApiResponse.<java.util.List<com.example.hauiTrash.dto.HistoryItemDTO>>builder()
                .message("Lấy lịch sử thành công")
                .data(aiPipelineService.getUserHistory())
                .build());
    }
}