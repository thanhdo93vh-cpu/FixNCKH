package com.example.hauiTrash.service;

import com.example.hauiTrash.client.LlmClient;
import com.example.hauiTrash.dto.TrashReasoningResultDTO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static com.example.hauiTrash.util.KnowledgeText.*;

@Service
@RequiredArgsConstructor
public class GraphRagService {
    private static final Logger log = LoggerFactory.getLogger(GraphRagService.class);
    private final LlmClient llmClient;
    private final GraphContextBuilder graphContextBuilder;

    public LlmClient.KnowledgeGenResult generateGuidance(TrashReasoningResultDTO kg) {
        if (kg == null) throw new IllegalArgumentException("KG result must not be null");
        try {
            var outcome = llmClient.generateAugmentedKnowledgeOutcome(kg.getLabel(), kg.getLabelDisplay(),
                    kg.getTrashTypeName(), graphContextBuilder.build(kg));
            return merge(kg, outcome != null ? outcome.knowledge() : null,
                    outcome != null ? outcome.reason() : "GEMINI_FAILED");
        } catch (Exception e) {
            return fallback(kg, "GEMINI_EXCEPTION");
        }
    }

    public LlmClient.KnowledgeGenResult fallback(TrashReasoningResultDTO kg, String reason) {
        return merge(kg, null, reason);
    }

    private LlmClient.KnowledgeGenResult merge(TrashReasoningResultDTO kg,
            LlmClient.KnowledgeGenResult gemini, String reason) {
        var result = LlmClient.KnowledgeGenResult.builder()
                .label(kg.getLabel())
                .source(gemini == null ? "KG_FALLBACK" : "GEMINI+KG")
                .reason(gemini == null ? first(reason, "GEMINI_FAILED") : null)
                .material(first(join(kg.getMaterials()), gemini != null ? gemini.getMaterial() : null))
                .materialDescription(first(join(kg.getMaterialDescriptions()), gemini != null ? gemini.getMaterialDescription() : null))
                .note(first(join(kg.getGuidanceLuY()), gemini != null ? gemini.getNote() : null))
                .action(first(action(kg), gemini != null ? gemini.getAction() : null))
                .impact(first(join(kg.getImpactDescriptions()), join(kg.getImpacts()), gemini != null ? gemini.getImpact() : null))
                .hazard(first(join(kg.getHazards()), join(kg.getHazardDescriptions())))
                .toxicity(join(kg.getToxicityDescriptions()))
                .model(gemini != null ? gemini.getModel() : null)
                .build();
        log.info("[GRAPH-RAG] source={} | reason={} | label={} | material={} | materialDescription={} | action={} | impact={} | toxicity={} | hazard={}",
                result.getSource(), result.getReason(), result.getLabel(), result.getMaterial(),
                result.getMaterialDescription(), result.getAction(), result.getImpact(), result.getToxicity(), result.getHazard());
        return result;
    }
}
