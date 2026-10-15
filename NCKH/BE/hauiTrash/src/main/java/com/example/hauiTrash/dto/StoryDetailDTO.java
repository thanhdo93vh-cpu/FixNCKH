package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class StoryDetailDTO {
    private Long id;
    private String title;
    private String slug;
    private String content;
    private String thumbnailUrl;
    private String category;
    private Integer viewCount;
    private LocalDateTime publishedAt;
}