package com.example.hauiTrash.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiGenerateContentRequest {

    private SystemInstruction systemInstruction;
    private List<Content> contents;
    private GenerationConfig generationConfig;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SystemInstruction {
        private List<Part> parts;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Content {
        private String role; // "user"
        private List<Part> parts;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Part {
        private String text;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GenerationConfig {
        private Double temperature;

        // ép model trả JSON "chuẩn"
        private String responseMimeType; // "application/json"
    }
}