package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.TrashItemAliasResponse;
import com.example.hauiTrash.entity.neo4j.TrashItemAliasNode;
import com.example.hauiTrash.service.TrashItemAliasNeo4jService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/neo4j/trash-item-aliases")
public class TrashItemAliasNeo4jController {

    private final TrashItemAliasNeo4jService trashItemAliasNeo4jService;

    @GetMapping("/alias/{alias}")
    public ResponseEntity<ApiResponse<TrashItemAliasResponse>> findByAlias(
            @PathVariable String alias) {

        TrashItemAliasResponse result =
                trashItemAliasNeo4jService.findByAlias(alias);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(
                ApiResponse.<TrashItemAliasResponse>builder()
                        .message("Tìm TrashItemAlias thành công")
                        .data(result)
                        .build()
        );
    }
}