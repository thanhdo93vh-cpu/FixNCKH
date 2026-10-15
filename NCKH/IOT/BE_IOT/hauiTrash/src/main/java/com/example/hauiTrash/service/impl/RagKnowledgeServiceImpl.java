package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.SimilarKnowledgeDTO;
import com.example.hauiTrash.entity.KnowledgeEmbedding;
import com.example.hauiTrash.entity.TrashItemKnowledge;
import com.example.hauiTrash.repository.KnowledgeEmbeddingRepository;
import com.example.hauiTrash.repository.TrashItemKnowledgeRepository;
import com.example.hauiTrash.service.RagKnowledgeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagKnowledgeServiceImpl implements RagKnowledgeService {

    private final KnowledgeEmbeddingRepository embeddingRepository;
    private final TrashItemKnowledgeRepository knowledgeRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rag.embedding.api.url:https://api.openai.com/v1/embeddings}")
    private String embeddingApiUrl;

    @Value("${rag.embedding.api.key:}")
    private String embeddingApiKey;

    private static final int DEFAULT_LIMIT = 3;
    private static final double SIMILARITY_THRESHOLD = 0.7;

    @Override
    public List<SimilarKnowledgeDTO> findSimilarKnowledge(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            // Tạo embedding cho query
            String queryEmbedding = generateEmbedding(query);
            List<Double> queryVector = parseEmbedding(queryEmbedding);
            if (queryVector.isEmpty()) {
                return List.of();
            }

            // Tìm kiếm trong database
            int effectiveLimit = limit > 0 ? limit : DEFAULT_LIMIT;

            // Convert sang DTO
            return embeddingRepository.findByEmbeddingJsonIsNotNull().stream()
                    .map(embedding -> new ScoredEmbedding(
                            embedding,
                            cosineSimilarity(queryVector, parseEmbedding(embedding.getEmbeddingJson()))
                    ))
                    .filter(scored -> scored.similarity() >= SIMILARITY_THRESHOLD)
                    .sorted(Comparator.comparingDouble(ScoredEmbedding::similarity).reversed())
                    .limit(effectiveLimit)
                    .map(ScoredEmbedding::embedding)
                    .map(this::convertToDTO)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to find similar knowledge for query: {}", query, e);
            return List.of();
        }
    }

    @Override
    public String generateEmbedding(String text) {
        // TODO: Gọi API embedding (OpenAI, HuggingFace, hoặc dùng local model)
        // Tạm thời trả về mock (cần tích hợp thật)

        if (embeddingApiKey != null && !embeddingApiKey.isBlank()) {
            return callOpenAIEmbedding(text);
        }

        // Fallback: tạo embedding giả dựa trên text hash
        return generateMockEmbedding(text);
    }

    private String callOpenAIEmbedding(String text) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("input", text);
            request.put("model", "text-embedding-ada-002");

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + embeddingApiKey);
            headers.set("Content-Type", "application/json");

            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(request, headers);

            var response = restTemplate.postForEntity(
                    embeddingApiUrl,
                    entity,
                    Map.class
            );

            if (response.getBody() != null && response.getBody().containsKey("data")) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (!data.isEmpty()) {
                    return objectMapper.writeValueAsString(data.get(0).get("embedding"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to call OpenAI embedding API", e);
        }
        return generateMockEmbedding(text);
    }

    private String generateMockEmbedding(String text) {
        // Mock embedding - trong thực tế cần dùng model thật
        List<Double> mockVector = new ArrayList<>();
        for (int i = 0; i < 1536; i++) {
            mockVector.add(Math.sin(text.hashCode() + i) * 0.5);
        }
        try {
            return objectMapper.writeValueAsString(mockVector);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Double> parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse embedding JSON", e);
            return List.of();
        }
    }

    private double cosineSimilarity(List<Double> firstVector, List<Double> secondVector) {
        if (firstVector.isEmpty() || firstVector.size() != secondVector.size()) {
            return 0;
        }

        int length = firstVector.size();
        double dotProduct = 0;
        double firstNorm = 0;
        double secondNorm = 0;

        for (int i = 0; i < length; i++) {
            double firstValue = firstVector.get(i);
            double secondValue = secondVector.get(i);

            dotProduct += firstValue * secondValue;
            firstNorm += firstValue * firstValue;
            secondNorm += secondValue * secondValue;
        }

        if (firstNorm == 0 || secondNorm == 0) {
            return 0;
        }

        return dotProduct / (Math.sqrt(firstNorm) * Math.sqrt(secondNorm));
    }

    private record ScoredEmbedding(KnowledgeEmbedding embedding, double similarity) {
    }

    @Override
    public void updateEmbedding(Integer knowledgeId) {
        TrashItemKnowledge knowledge = knowledgeRepository.findById(knowledgeId).orElse(null);
        if (knowledge == null) return;

        Optional<KnowledgeEmbedding> existing = embeddingRepository.findByLabel(knowledge.getTrashItem().getLabel());

        String content = buildKnowledgeContent(knowledge);
        String embedding = generateEmbedding(content);

        KnowledgeEmbedding embeddingEntity;
        if (existing.isPresent()) {
            embeddingEntity = existing.get();
            embeddingEntity.setContent(content);
            embeddingEntity.setEmbeddingJson(embedding);
        } else {
            embeddingEntity = KnowledgeEmbedding.builder()
                    .knowledge(knowledge)
                    .label(knowledge.getTrashItem().getLabel())
                    .labelDisplay(knowledge.getTrashItem().getLabelDisplay())
                    .content(content)
                    .embeddingJson(embedding)
                    .build();
        }

        embeddingRepository.save(embeddingEntity);
        log.info("Updated embedding for knowledge: {}", knowledge.getTrashItem().getLabel());
    }

    @Override
    public String buildRagContext(String label, String labelDisplay) {
        String query = label + " " + (labelDisplay != null ? labelDisplay : "");
        List<SimilarKnowledgeDTO> similar = findSimilarKnowledge(query, DEFAULT_LIMIT);

        if (similar.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("Dưới đây là thông tin về các loại rác tương tự:\n\n");

        for (int i = 0; i < similar.size(); i++) {
            SimilarKnowledgeDTO s = similar.get(i);
            context.append("--- Ví dụ ").append(i + 1).append(" ---\n");
            context.append("Loại rác: ").append(s.getLabelDisplay()).append("\n");
            context.append("Chất liệu: ").append(s.getMaterial()).append("\n");
            context.append("Lưu ý: ").append(s.getNote()).append("\n");
            context.append("Hành động: ").append(s.getAction()).append("\n");
            context.append("Tác động: ").append(s.getImpact()).append("\n");
            context.append("Độc hại: ").append(s.getToxicity()).append("\n");
            context.append("Các bước xử lý: ").append(String.join(", ", s.getSafeSteps())).append("\n\n");
        }

        return context.toString();
    }

    private String buildKnowledgeContent(TrashItemKnowledge knowledge) {
        return String.format(
                "Loại rác: %s\nChất liệu: %s\nLưu ý: %s\nHành động: %s\nTác động môi trường: %s\nMức độ độc hại: %s\nCác bước xử lý: %s",
                knowledge.getTrashItem().getLabelDisplay(),
                knowledge.getMaterial(),
                knowledge.getNote(),
                knowledge.getAction(),
                knowledge.getImpact(),
                knowledge.getToxicity(),
                String.join(", ", knowledge.getSafeSteps())
        );
    }

    private SimilarKnowledgeDTO convertToDTO(KnowledgeEmbedding embedding) {
        TrashItemKnowledge knowledge = embedding.getKnowledge();
        if (knowledge == null) return null;

        return SimilarKnowledgeDTO.builder()
                .label(knowledge.getTrashItem().getLabel())
                .labelDisplay(knowledge.getTrashItem().getLabelDisplay())
                .material(knowledge.getMaterial())
                .note(knowledge.getNote())
                .action(knowledge.getAction())
                .impact(knowledge.getImpact())
                .toxicity(knowledge.getToxicity())
                .safeSteps(knowledge.getSafeSteps())
                .build();
    }
}
