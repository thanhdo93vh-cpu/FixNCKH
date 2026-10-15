package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.StoryDetailDTO;
import com.example.hauiTrash.dto.StoryListDTO;
import com.example.hauiTrash.dto.StoryRequest;
import com.example.hauiTrash.entity.StoryCategory;
import com.example.hauiTrash.service.StoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    //?page=0&size=10&category=RECYCLE_TIPS( lọc theo category)
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoryListDTO>>> getStories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) StoryCategory category
    ) {
        List<StoryListDTO> stories = storyService.getPublishedStories(category, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.<List<StoryListDTO>>builder()
                .message("Lấy danh sách câu chuyện thành công")
                .data(stories)
                .build());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<StoryDetailDTO>> getStoryDetail(@PathVariable String slug, HttpServletRequest request) {
        StoryDetailDTO story = storyService.getStoryBySlug(slug,request);
        return ResponseEntity.ok(ApiResponse.<StoryDetailDTO>builder()
                .message("Lấy chi tiết câu chuyện thành công")
                .data(story)
                .build());
    }

}