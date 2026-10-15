package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.TrashBinResponse;
import com.example.hauiTrash.service.TrashBinNeo4jService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/neo4j/trash-bins")
public class TrashBinNeo4jController {

    private final TrashBinNeo4jService trashBinNeo4jService;

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<TrashBinResponse>> findByCode(
            @PathVariable String code) {

        TrashBinResponse result =
                trashBinNeo4jService.findByCode(code);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                ApiResponse.<TrashBinResponse>builder()
                        .message("Tìm TrashBin thành công")
                        .data(result)
                        .build()
        );
    }
    @GetMapping("/name/{name}")
    public ResponseEntity<ApiResponse<TrashBinResponse>> findByName(
            @PathVariable String name) {

        TrashBinResponse result =
                trashBinNeo4jService.findByName(name);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                ApiResponse.<TrashBinResponse>builder()
                        .message("Tìm TrashBin theo name thành công")
                        .data(result)
                        .build()
        );
    }
}