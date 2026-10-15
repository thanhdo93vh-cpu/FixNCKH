package com.example.hauiTrash.service;

import com.example.hauiTrash.client.LlmClient;
import com.example.hauiTrash.dto.VisualRagResult;
import com.example.hauiTrash.entity.*;
import com.example.hauiTrash.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) Service.
 *
 * Pipeline:
 *   1. EMBED: Generate embedding for query text via Gemini text-embedding-004
 *   2. RETRIEVE: Cosine similarity search against pre-computed knowledge embeddings
 *   3. AUGMENT: Feed top-K retrieved contexts to Gemini LLM for enriched response
 *
 * This replaces the old exact-match SQL retrieval with semantic similarity search.
 */
@Service
@RequiredArgsConstructor
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    @Autowired private LlmClient llmClient;
    @Autowired private TrashItemKnowledgeRepository knowledgeRepo;
    @Autowired private TrashItemRepository trashItemRepo;
    @Autowired private TrashItemMappingRepository mappingRepo;
    @Autowired private TrashItemAliasRepository trashItemAliasRepo;

    /** Similarity threshold — below this, result is considered irrelevant */
    private static final float SIMILARITY_THRESHOLD = 0.65f;

    /** Top-K results to retrieve for augmented generation */
    private static final int TOP_K = 3;

    /**
     * In-memory cache of knowledge embeddings.
     * Key: TrashItemKnowledge.id → Value: float[] embedding
     */
    private final ConcurrentHashMap<Integer, float[]> embeddingCache = new ConcurrentHashMap<>();

    /**
     * Cached knowledge metadata.
     * Key: TrashItemKnowledge.id → Value: knowledge text summary
     */
    private final ConcurrentHashMap<Integer, KnowledgeCacheEntry> knowledgeCache = new ConcurrentHashMap<>();

    private static class KnowledgeCacheEntry {
        final Integer knowledgeId;
        final Integer trashItemId;
        final String label;
        final String labelDisplay;
        final String material;
        final String note;
        final String action;
        final String trashTypeName;

        KnowledgeCacheEntry(TrashItemKnowledge k, String trashTypeName) {
            this.knowledgeId = k.getId();
            this.trashItemId = k.getTrashItem().getId();
            this.label = k.getTrashItem().getLabel();
            this.labelDisplay = k.getTrashItem().getLabelDisplay();
            this.material = k.getMaterial();
            this.note = k.getNote();
            this.action = k.getAction();
            this.trashTypeName = trashTypeName;
        }

        /**
         * Build text representation for embedding and context retrieval.
         */
        String toEmbeddingText() {
            StringBuilder sb = new StringBuilder();
            if (label != null) sb.append("Label: ").append(label).append(". ");
            if (labelDisplay != null) sb.append("Tên: ").append(labelDisplay).append(". ");
            if (material != null) sb.append("Vật liệu: ").append(material).append(". ");
            if (note != null) sb.append("Ghi chú: ").append(note).append(". ");
            if (action != null) sb.append("Cách xử lý: ").append(action).append(". ");
            if (trashTypeName != null) sb.append("Loại rác: ").append(trashTypeName).append(".");
            return sb.toString().trim();
        }

        String toContextString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(labelDisplay != null ? labelDisplay : label).append("]");
            if (trashTypeName != null) sb.append(" - Loại: ").append(trashTypeName);
            if (material != null) sb.append("\nVật liệu: ").append(material);
            if (note != null) sb.append("\nGhi chú: ").append(note);
            if (action != null) sb.append("\nCách xử lý: ").append(action);
            return sb.toString();
        }
    }

    // ========================================================
    // INITIALIZATION: Load embeddings into memory cache
    // ========================================================

    @PostConstruct
    public void init() {
        try {
            loadEmbeddingsToCache();
            log.info("RAG Service initialized. Cache size: {}", embeddingCache.size());
        } catch (Exception e) {
            log.warn("RAG Service init failed (will work without cache): {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public void loadEmbeddingsToCache() {
        List<TrashItemKnowledge> allWithEmbeddings = knowledgeRepo.findAllActiveWithEmbeddings();

        embeddingCache.clear();
        knowledgeCache.clear();

        for (TrashItemKnowledge k : allWithEmbeddings) {
            if (k.getEmbeddingJson() != null && !k.getEmbeddingJson().isEmpty()) {
                float[] vec = toFloatArray(k.getEmbeddingJson());
                embeddingCache.put(k.getId(), vec);

                String trashTypeName = resolveTrashTypeName(k.getTrashItem());
                knowledgeCache.put(k.getId(), new KnowledgeCacheEntry(k, trashTypeName));
            }
        }

        log.info("Loaded {} knowledge embeddings into RAG cache", embeddingCache.size());
    }

    // ========================================================
    // CORE RAG PIPELINE
    // ========================================================

    /**
     * Main RAG retrieval method — replaces old retrieveVisualRag.
     *
     * Pipeline:
     *   1. Try semantic search (embedding similarity)
     *   2. If semantic fails, fallback to exact DB match
     *   3. If contexts found, use Augmented Generation via LLM
     */
    @Transactional
    public VisualRagResult retrieve(String label, String labelDisplay, String cropUrl) {
        if (label == null || label.isBlank()) {
            return null;
        }

        // ===== STEP 1: Semantic Retrieval =====
        String queryText = buildQueryText(label, labelDisplay);
        List<RetrievedContext> topK = semanticSearch(queryText, TOP_K);

        if (!topK.isEmpty()) {
            log.info("RAG semantic search found {} results for label={}, topScore={}",
                    topK.size(), label, topK.get(0).score);

            // Use the best match's trash item as primary
            KnowledgeCacheEntry bestEntry = knowledgeCache.get(topK.get(0).knowledgeId);
            if (bestEntry != null) {
                TrashItem item = trashItemRepo.findById(bestEntry.trashItemId).orElse(null);
                TrashItemKnowledge knowledge = knowledgeRepo.findById(bestEntry.knowledgeId).orElse(null);
                TrashItemMapping mapping = (item != null)
                        ? mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null)
                        : null;

                // ===== STEP 2: Augmented Generation =====
                List<String> contextStrings = topK.stream()
                        .map(r -> r.contextText)
                        .collect(Collectors.toList());

                String trashTypeName = (mapping != null && mapping.getTrashType() != null)
                        ? mapping.getTrashType().getName()
                        : bestEntry.trashTypeName;

                TrashItemKnowledge augmented = generateAugmentedKnowledge(
                        label, labelDisplay, trashTypeName, contextStrings);

                return VisualRagResult.builder()
                        .trashItem(item)
                        .trashType(mapping != null ? mapping.getTrashType() : null)
                        .knowledge(knowledge)
                        .mappingConfidence(mapping != null ? mapping.getMappingConfidence() : null)
                        .ragSimilarityScore(topK.get(0).score)
                        .ragSource("RAG_SEMANTIC")
                        .augmentedKnowledge(augmented)
                        .build();
            }
        }

        // ===== FALLBACK: Exact DB match (like old system) =====
        log.info("RAG semantic search empty, falling back to exact DB match for label={}", label);
        return fallbackExactMatch(label);
    }

    // ========================================================
    // SEMANTIC SEARCH
    // ========================================================

    private record RetrievedContext(Integer knowledgeId, float score, String contextText) {}

    private List<RetrievedContext> semanticSearch(String queryText, int topK) {
        if (embeddingCache.isEmpty()) {
            log.warn("RAG embedding cache is empty, cannot perform semantic search");
            return List.of();
        }

        // Generate query embedding
        float[] queryEmbedding = llmClient.embedText(queryText);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            log.warn("Failed to generate query embedding for: {}", queryText);
            return List.of();
        }

        // Compute cosine similarity against all cached embeddings
        List<RetrievedContext> results = new ArrayList<>();

        for (Map.Entry<Integer, float[]> entry : embeddingCache.entrySet()) {
            Integer knowledgeId = entry.getKey();
            float[] docEmbedding = entry.getValue();

            float similarity = cosineSimilarity(queryEmbedding, docEmbedding);

            if (similarity >= SIMILARITY_THRESHOLD) {
                KnowledgeCacheEntry cached = knowledgeCache.get(knowledgeId);
                String contextText = (cached != null) ? cached.toContextString() : "";
                results.add(new RetrievedContext(knowledgeId, similarity, contextText));
            }
        }

        // Sort by similarity descending, take top-K
        results.sort((a, b) -> Float.compare(b.score, a.score));
        return results.stream().limit(topK).collect(Collectors.toList());
    }

    // ========================================================
    // AUGMENTED GENERATION
    // ========================================================

    private TrashItemKnowledge generateAugmentedKnowledge(
            String label, String labelDisplay, String trashTypeName,
            List<String> retrievedContexts
    ) {
        try {
            LlmClient.KnowledgeGenResult result = llmClient.generateAugmentedKnowledge(
                    label, labelDisplay, trashTypeName, retrievedContexts);

            if (result == null) {
                return null;
            }

            // Build a transient TrashItemKnowledge (not persisted, just for response)
            return TrashItemKnowledge.builder()
                    .material(result.getMaterial())
                    .note(result.getNote())
                    .action(result.getAction())
                    .impact(result.getImpact())
                    .toxicity(result.getToxicity())
                    .safeSteps(result.getSafeSteps())
                    .source("RAG")
                    .model(result.getModel())
                    .version(0)
                    .isActive(false) // transient, not persisted
                    .build();

        } catch (Exception e) {
            log.error("RAG augmented generation failed for label={}", label, e);
            return null;
        }
    }

    // ========================================================
    // FALLBACK: Exact DB match (old behavior)
    // ========================================================

    private VisualRagResult fallbackExactMatch(String label) {
        String normalized = label.trim().toLowerCase(java.util.Locale.ROOT);

        TrashItem item = trashItemAliasRepo.findByAlias(normalized)
                .map(TrashItemAlias::getTrashItem)
                .orElseGet(() -> trashItemRepo.findByLabel(normalized).orElse(null));

        if (item == null) {
            return null;
        }

        TrashItemMapping mapping = mappingRepo.findActiveByTrashItemId(item.getId()).orElse(null);
        TrashItemKnowledge knowledge = knowledgeRepo.findActiveByTrashItemId(item.getId()).orElse(null);

        return VisualRagResult.builder()
                .trashItem(item)
                .trashType(mapping != null ? mapping.getTrashType() : null)
                .knowledge(knowledge)
                .mappingConfidence(mapping != null ? mapping.getMappingConfidence() : null)
                .ragSimilarityScore(null)
                .ragSource("FALLBACK_DB")
                .augmentedKnowledge(null)
                .build();
    }

    // ========================================================
    // ADMIN: Rebuild all embeddings
    // ========================================================

    /**
     * Rebuild embeddings for all active knowledge entries.
     * Called via admin endpoint or on first startup.
     */
    @Transactional
    public int rebuildAllEmbeddings() {
        List<TrashItemKnowledge> allActive = knowledgeRepo.findAllActive();
        int count = 0;

        for (TrashItemKnowledge k : allActive) {
            try {
                String trashTypeName = resolveTrashTypeName(k.getTrashItem());
                KnowledgeCacheEntry entry = new KnowledgeCacheEntry(k, trashTypeName);
                String text = entry.toEmbeddingText();

                float[] embedding = llmClient.embedText(text);
                if (embedding.length > 0) {
                    k.setEmbeddingJson(toFloatList(embedding));
                    k.setUpdatedAt(LocalDateTime.now());
                    knowledgeRepo.save(k);

                    // Update cache
                    embeddingCache.put(k.getId(), embedding);
                    knowledgeCache.put(k.getId(), entry);

                    count++;
                    log.info("Embedded knowledge id={} label={} dim={}",
                            k.getId(), k.getTrashItem().getLabel(), embedding.length);
                }

                // Rate limit: avoid hitting Gemini API too fast
                Thread.sleep(200);

            } catch (Exception e) {
                log.error("Failed to embed knowledge id={}", k.getId(), e);
            }
        }

        log.info("RAG rebuild complete: {}/{} embeddings generated", count, allActive.size());
        return count;
    }

    // ========================================================
    // UTILITY METHODS
    // ========================================================

    private String buildQueryText(String label, String labelDisplay) {
        StringBuilder sb = new StringBuilder();
        if (label != null) sb.append("Label: ").append(label).append(". ");
        if (labelDisplay != null) sb.append("Tên: ").append(labelDisplay).append(".");
        return sb.toString().trim();
    }

    private String resolveTrashTypeName(TrashItem item) {
        if (item == null || item.getId() == null) return null;
        return mappingRepo.findActiveByTrashItemId(item.getId())
                .map(m -> m.getTrashType() != null ? m.getTrashType().getName() : null)
                .orElse(null);
    }

    /**
     * Cosine similarity between two vectors.
     */
    static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0f;

        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        float denom = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        return denom == 0f ? 0f : dot / denom;
    }

    private float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i) != null ? list.get(i) : 0f;
        }
        return arr;
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}
