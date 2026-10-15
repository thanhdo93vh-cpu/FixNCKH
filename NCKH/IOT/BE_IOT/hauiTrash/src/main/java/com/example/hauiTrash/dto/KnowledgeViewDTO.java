package com.example.hauiTrash.dto;

import lombok.*;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class KnowledgeViewDTO {
    private Integer trashItemId;
    private String label;
    private String labelDisplay;

    // UI trái (loại rác + chất liệu + note + action)
    private String trashType;     // "Tái chế" / "Hữu cơ"...
    private String material;      // hiển thị "Chất liệu"
    private String note;          // hiển thị "Lưu ý xử lý"
    private String action;        // hiển thị "Hành động đề xuất"

    // UI phải (phân tích chi tiết)
    private String impact;        // "Tác động môi trường & kinh tế"
    private String toxicity;      // "Mức độ độc hại"
    private List<String> safeSteps; // "Quy trình xử lý an toàn"
}
