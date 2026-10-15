package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.TrashItemResponse;
import com.example.hauiTrash.service.TrashItemNeo4jService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/neo4j/trash-items")
public class TrashItemNeo4jController {

    private final TrashItemNeo4jService trashItemNeo4jService;

    @GetMapping("/resolve")
    public ResponseEntity<ApiResponse<TrashItemResponse>> resolve(
            @RequestParam String label) {

        TrashItemResponse result =
                trashItemNeo4jService.findByLabelOrAlias(label);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                ApiResponse.<TrashItemResponse>builder()
                        .message("Xác định TrashItem thành công")
                        .data(result)
                        .build()
        );
    }
}