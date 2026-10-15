package com.example.hauiTrash.client;

import com.example.hauiTrash.dto.GeminiTrashItemResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    // ====== Suggest trash type (BATCH) ======
    @Data
    class TypeSuggestItemInput {
        private String label;
        private String labelDisplay;

        public TypeSuggestItemInput() {}
        public TypeSuggestItemInput(String label, String labelDisplay) {
            this.label = label;
            this.labelDisplay = labelDisplay;
        }
    }

    @Data
    class TypeSuggestBatchResult {
        private List<TypeSuggestion> items;
    }

    @Data
    @Builder
    class TypeSuggestion {
        private String trashTypeCode;
        private Float confidence;
        private String reason;
    }

    TypeSuggestBatchResult suggestTrashTypeBatch(List<TypeSuggestItemInput> items);

    // Giữ method cũ nếu chỗ khác đang gọi: wrapper sẽ gọi batch size=1
    TypeSuggestion suggestTrashType(String label, String labelDisplay);

    // ====== Knowledge (giữ nguyên nếu cần) ======
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    class KnowledgeGenResult {
        private String material;
        private String note;
        private String action;
        private String impact;
        private String toxicity;
        private List<String> safeSteps;
        private String model;
    }

    KnowledgeGenResult generateKnowledge(String label, String labelDisplay, String trashTypeName);

    // ====== Label display (BATCH) ======
    String generateLabelDisplay(String label);

    Map<String, String> generateLabelDisplayBatch(List<String> labels);
    GeminiTrashItemResult generateTrashItem(String userInput);

    // ====== RAG: Embedding ======
    float[] embedText(String text);

    // ====== RAG: Augmented Generation ======
    KnowledgeGenResult generateAugmentedKnowledge(String label, String labelDisplay, String trashTypeName, List<String> retrievedContexts);
}
