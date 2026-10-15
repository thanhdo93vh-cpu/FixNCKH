package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.SampleImageResponse;
import com.example.hauiTrash.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class SampleImageController {

    private final ImageService imageService;

    @GetMapping("/sample")
    public ResponseEntity<ApiResponse<SampleImageResponse>> getSampleImage(
            @RequestParam(required = false) String material) {

        SampleImageResponse data =
                imageService.getSampleImage(material);

        return ResponseEntity.ok(
                ApiResponse.<SampleImageResponse>builder()
                        .message("Lấy ảnh mẫu thành công")
                        .data(data)
                        .build()
        );
    }
}