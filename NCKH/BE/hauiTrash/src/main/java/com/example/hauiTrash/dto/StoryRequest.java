package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class StoryRequest {
    private String title;
    private String slug;
    private String content;
    private String thumbnailUrl;
    private String category;
    private Boolean isPublished;
    private LocalDateTime publishedAt;
}