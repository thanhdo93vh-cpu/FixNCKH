package com.example.hauiTrash.util;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Missing knowledge must not mask a lower-priority source. */
public final class KnowledgeText {
    private KnowledgeText() {}

    public static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.equalsIgnoreCase("null") || text.equals("{}")
                || text.toLowerCase(Locale.ROOT).contains(
                "không có thông tin cụ thể trong dữ liệu tri thức")) return null;
        return text;
    }

    public static String first(String... candidates) {
        for (String candidate : candidates) {
            String value = clean(candidate);
            if (value != null) return value;
        }
        return null;
    }

    public static List<String> values(List<String> source) {
        return source == null ? List.of() : source.stream()
                .map(KnowledgeText::clean).filter(Objects::nonNull).toList();
    }

    public static String join(List<String> source) {
        return clean(String.join("\n", values(source)));
    }

    public static String action(com.example.hauiTrash.dto.TrashReasoningResultDTO kg) {
        if (kg == null) return null;
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        addSection(parts, "", kg.getGuidance());
        addSection(parts, "Hướng xử lý:\n", kg.getGuidanceDescription());
        addSection(parts, "Lưu ý:\n", kg.getGuidanceLuY());
        addSection(parts, "Các bước:\n", kg.getGuidanceSteps());
        return clean(String.join("\n\n", parts));
    }

    private static void addSection(List<String> parts, String heading, List<String> values) {
        String text = join(values);
        if (text != null) parts.add(heading + text);
    }
}
