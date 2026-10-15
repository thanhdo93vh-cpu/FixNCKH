package com.example.hauiTrash.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {
    private String apiKey;
    private String model;
    private String baseUrl;
    private double temperature = 0.2;
    private int timeoutSeconds = 20;
    private String embeddingModel = "text-embedding-004";
}