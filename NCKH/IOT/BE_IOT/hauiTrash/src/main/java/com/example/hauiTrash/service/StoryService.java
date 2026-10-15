package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.StoryDetailDTO;
import com.example.hauiTrash.dto.StoryListDTO;
import com.example.hauiTrash.dto.StoryRequest;
import com.example.hauiTrash.entity.StoryCategory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface StoryService {
    public List<StoryListDTO> getPublishedStories(StoryCategory category, Pageable pageable);
    public StoryDetailDTO getStoryBySlug(String slug, HttpServletRequest request);
    StoryDetailDTO createStory(StoryRequest request);
    StoryDetailDTO updateStory(Long id, StoryRequest request);
    void deleteStory(Long id);
}