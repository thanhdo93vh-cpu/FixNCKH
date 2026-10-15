package com.example.hauiTrash.client;

import com.example.hauiTrash.dto.GeminiTrashItemResult;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DummyLlmClient implements LlmClient {

    @Override
    public TypeSuggestBatchResult suggestTrashTypeBatch(List<TypeSuggestItemInput> items) {
        TypeSuggestBatchResult r = new TypeSuggestBatchResult();

        if (items == null || items.isEmpty()) {
            r.setItems(List.of());
            return r;
        }

        List<TypeSuggestion> out = new ArrayList<>();
        for (TypeSuggestItemInput it : items) {
            // dummy: luôn RECYCLE, nhưng vẫn giữ format đầy đủ
            out.add(TypeSuggestion.builder()
                    .trashTypeCode("RECYCLE")
                    .confidence(0.7f)
                    .reason("Dummy fallback")
                    .build());
        }

        r.setItems(out);
        return r;
    }

    @Override
    public TypeSuggestion suggestTrashType(String label, String labelDisplay) {
        // TẤT CẢ đều đi list: wrap sang batch size=1
        TypeSuggestBatchResult r = suggestTrashTypeBatch(
                List.of(new TypeSuggestItemInput(label, labelDisplay))
        );

        if (r != null && r.getItems() != null && !r.getItems().isEmpty() && r.getItems().get(0) != null) {
            return r.getItems().get(0);
        }

        return TypeSuggestion.builder()
                .trashTypeCode("UNKNOWN")
                .confidence(0f)
                .reason("Dummy empty_result")
                .build();
    }

    @Override
    public KnowledgeGenResult generateKnowledge(String label, String labelDisplay, String trashTypeName) {
        return KnowledgeGenResult.builder()
                .material("Không xác định")
                .note("Không đốt. Làm sạch sơ bộ trước khi phân loại.")
                .action("Phân loại theo nhóm rác phù hợp và bỏ đúng thùng/điểm thu gom.")
                .impact("Giảm rác thải chôn lấp và tiết kiệm tài nguyên xử lý.")
                .toxicity("Tránh đốt vì có thể sinh khói/khí gây hại; đồ nguy hại cần thu gom riêng.")
                .safeSteps(List.of(
                        "Làm sạch sơ bộ và loại bỏ phần bẩn nếu có",
                        "Để ráo và gom gọn để tránh rơi vãi",
                        "Bỏ đúng nhóm rác theo hướng dẫn địa phương"
                ))
                .model("dummy")
                .build();
    }

    @Override
    public String generateLabelDisplay(String label) {
        //  wrap sang batch size=1
        Map<String, String> map = generateLabelDisplayBatch(List.of(label));
        String k = label == null ? "" : label.trim();
        return map.getOrDefault(k, k.replace('_', ' ').trim());
    }

    @Override
    public Map<String, String> generateLabelDisplayBatch(List<String> labels) {
        if (labels == null || labels.isEmpty()) return Map.of();

        Map<String, String> map = new HashMap<>();
        for (String lb : labels) {
            if (lb == null) continue;
            String k = lb.trim();
            if (k.isBlank()) continue;
            map.put(k, k.replace('_', ' ').trim());
        }
        return map;
    }

    @Override
    public GeminiTrashItemResult generateTrashItem(String userInput) {
        return null;
    }

    @Override
    public float[] embedText(String text) {
        return new float[0]; // dummy: no embedding
    }

    @Override
    public KnowledgeGenResult generateAugmentedKnowledge(String label, String labelDisplay, String trashTypeName, List<String> retrievedContexts) {
        // dummy: delegate to basic knowledge generation
        return generateKnowledge(label, labelDisplay, trashTypeName);
    }
}
