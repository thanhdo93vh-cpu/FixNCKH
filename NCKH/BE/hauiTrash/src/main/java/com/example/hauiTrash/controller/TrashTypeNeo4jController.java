package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.TrashTypeResponse;
import com.example.hauiTrash.service.TrashTypeNeo4jService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/neo4j/trash-types")
public class TrashTypeNeo4jController {

    private final TrashTypeNeo4jService trashTypeNeo4jService;

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<TrashTypeResponse>> findByCode(
            @PathVariable String code) {

        TrashTypeResponse result =
                trashTypeNeo4jService.findByCode(code);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                ApiResponse.<TrashTypeResponse>builder()
                        .message("Tìm TrashType thành công")
                        .data(result)
                        .build()
        );
    }
    @GetMapping("/name/{name}")
    public ResponseEntity<ApiResponse<TrashTypeResponse>> findByName(
            @PathVariable String name) {

        TrashTypeResponse result =
                trashTypeNeo4jService.findByName(name);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                ApiResponse.<TrashTypeResponse>builder()
                        .message("Tìm TrashType theo name thành công")
                        .data(result)
                        .build()
        );
    }
}