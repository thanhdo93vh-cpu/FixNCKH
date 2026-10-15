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
    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoryDetailDTO>> createStory(@Valid @RequestBody StoryRequest request) {
        StoryDetailDTO story = storyService.createStory(request);
        return ResponseEntity.ok(ApiResponse.<StoryDetailDTO>builder()
                .message("Tạo câu chuyện thành công")
                .data(story)
                .build());
    }
    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoryDetailDTO>> updateStory(
            @PathVariable Long id,
            @Valid @RequestBody StoryRequest request
    ) {
        StoryDetailDTO story = storyService.updateStory(id, request);
        return ResponseEntity.ok(ApiResponse.<StoryDetailDTO>builder()
                .message("Cập nhật câu chuyện thành công")
                .data(story)
                .build());
    }
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable Long id) {
        storyService.deleteStory(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Xóa câu chuyện thành công")
                .build());
    }
}