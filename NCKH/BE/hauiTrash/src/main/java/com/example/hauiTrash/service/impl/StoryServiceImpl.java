package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.StoryDetailDTO;
import com.example.hauiTrash.dto.StoryListDTO;
import com.example.hauiTrash.dto.StoryRequest;
import com.example.hauiTrash.entity.Story;
import com.example.hauiTrash.entity.StoryCategory;
import com.example.hauiTrash.repository.StoryRepository;
import com.example.hauiTrash.service.PointService;
import com.example.hauiTrash.service.StoryService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final PointService pointService;
    private final CacheManager cacheManager;
    @Override
    @Transactional(readOnly = true)
    public List<StoryListDTO> getPublishedStories(StoryCategory category, Pageable pageable) {
        List<Story> stories;
        if (category == null) {
            stories = storyRepository.findAllPublished(pageable);
        } else {
            stories = storyRepository.findByCategory(category, pageable);
        }
        return stories.stream().map(this::toListDTO).collect(Collectors.toList());
    }
    @Override
    @Transactional
    public StoryDetailDTO getStoryBySlug(String slug, HttpServletRequest request) {
        // 1. Tìm story theo slug(nó thuận tiện hơn id cho user)
        Story story = storyRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy câu chuyện: " + slug));
        // 2. Lấy IP client(check view)
        String clientIp = getClientIp(request);
        String cacheKey = "story_view_" + slug + "_" + clientIp;
        // 3. Kiểm tra cache (nếu có cache → không tăng view)
        Cache cache = cacheManager.getCache("storyViews");
        Cache.ValueWrapper cached = cache.get(cacheKey);
        if (cached == null) {
            // 4. Chưa có cache → tăng view
            story.setViewCount(story.getViewCount() + 1);
            storyRepository.save(story);
            // 5. Lưu IP vào cache (tự động hết hạn sau 10 phút)
            cache.put(cacheKey, System.currentTimeMillis());
            log.info(" Tăng view cho story: {}, IP: {}", slug, clientIp);
        } else {
            log.info(" Bỏ qua tăng view (trong 10 phút): {}, IP: {}", slug, clientIp);
        }
        // 6. Trả về DTO
        return toDetailDTO(story);
    }
    /**
     * Lấy IP thực của client (qua proxy, load balancer)
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
    private StoryListDTO toListDTO(Story story) {
        return StoryListDTO.builder()
                .id(story.getId())
                .title(story.getTitle())
                .slug(story.getSlug())
                .thumbnailUrl(story.getThumbnailUrl())
                .category(story.getCategory().name())
                .viewCount(story.getViewCount())
                .publishedAt(story.getPublishedAt())
                .build();
    }
    @Override
    @Transactional
    public StoryDetailDTO createStory(StoryRequest request) {
        Story story = Story.builder()
                .title(request.getTitle())
                .slug(request.getSlug())
                .content(request.getContent())
                .thumbnailUrl(request.getThumbnailUrl())
                .category(StoryCategory.valueOf(request.getCategory()))
                .isPublished(request.getIsPublished() != null ? request.getIsPublished() : true)
                .viewCount(0)
                .publishedAt(request.getPublishedAt() != null ? request.getPublishedAt() : LocalDateTime.now())
                .build();
        story = storyRepository.save(story);
        return toDetailDTO(story);
    }
    @Override
    @Transactional
    public StoryDetailDTO updateStory(Long id, StoryRequest request) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy câu chuyện: " + id));
        if (request.getTitle() != null) story.setTitle(request.getTitle());
        if (request.getSlug() != null) story.setSlug(request.getSlug());
        if (request.getContent() != null) story.setContent(request.getContent());
        if (request.getThumbnailUrl() != null) story.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getCategory() != null) story.setCategory(StoryCategory.valueOf(request.getCategory()));
        if (request.getIsPublished() != null) story.setIsPublished(request.getIsPublished());
        if (request.getPublishedAt() != null) story.setPublishedAt(request.getPublishedAt());
        story = storyRepository.save(story);
        return toDetailDTO(story);
    }
    @Override
    @Transactional
    public void deleteStory(Long id) {
        Story story = storyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy câu chuyện: " + id));
        storyRepository.delete(story);
    }
    private StoryDetailDTO toDetailDTO(Story story) {
        return StoryDetailDTO.builder()
                .id(story.getId())
                .title(story.getTitle())
                .slug(story.getSlug())
                .content(story.getContent())
                .thumbnailUrl(story.getThumbnailUrl())
                .category(story.getCategory().name())
                .viewCount(story.getViewCount())
                .publishedAt(story.getPublishedAt())
                .build();
    }
}