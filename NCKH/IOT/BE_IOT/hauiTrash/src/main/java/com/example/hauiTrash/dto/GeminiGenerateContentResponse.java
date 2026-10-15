package com.example.hauiTrash.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GeminiGenerateContentResponse {
    private List<Candidate> candidates;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Candidate {
        private Content content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Content {
        private List<Part> parts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Data
    public static class Part {
        private String text;
    }
}