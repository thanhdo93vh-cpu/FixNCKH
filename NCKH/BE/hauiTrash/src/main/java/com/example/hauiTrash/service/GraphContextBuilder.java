package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.TrashReasoningResultDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GraphContextBuilder {

    public List<String> build(
            TrashReasoningResultDTO r
    ) {

        if (r == null) {
            return List.of();
        }

        List<String> contexts = new ArrayList<>();

        contexts.add(
                "Đối tượng: " + safe(r.getLabelDisplay())
        );

        contexts.add(
                "Vật liệu: " + safeList(r.getMaterials())
        );

        contexts.add(
                "Mô tả chất liệu: " +
                        safeList(r.getMaterialDescriptions())
        );
        contexts.add(
                "Mối nguy từ đối tượng: " +
                        safeList(r.getHazards())
        );

        contexts.add(
                "Mô tả mối nguy của đối tượng: " +
                        safeList(r.getHazardDescriptions())
        );
        contexts.add("Độc tính đã có trong KG: " + safeList(r.getToxicityDescriptions()));
        contexts.add(
                "Tác động môi trường: " +
                        safeList(r.getImpacts())
        );

        contexts.add(
                "Mô tả tác động môi trường: " +
                        safeList(r.getImpactDescriptions())
        );
        contexts.add(
                "Nhóm rác: "
                        + safe(r.getTrashTypeCode())
                        + " - "
                        + safe(r.getTrashTypeName())
        );

        contexts.add(
                "Thùng rác: "
                        + safe(r.getTrashBinCode())
                        + " - "
                        + safe(r.getTrashBinName())
        );

        contexts.add(
                "Màu thùng: "
                        + safe(r.getTrashBinColorCode())
        );

        contexts.add(
                "Phương thức xử lý: "
                        + safe(r.getTreatmentName())
        );

        contexts.add(
                "Hướng xử lý: "
                        + safeList(r.getGuidance())
        );

        contexts.add(
                "Mô tả hướng xử lý: "
                        + safeList(r.getGuidanceDescription())
        );

        contexts.add(
                "Các bước xử lý: "
                        + safeList(r.getGuidanceSteps())
        );

        contexts.add(
                "Lưu ý: "
                        + safeList(r.getGuidanceLuY())
        );




        return contexts;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safeList(List<?> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        return values.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}
