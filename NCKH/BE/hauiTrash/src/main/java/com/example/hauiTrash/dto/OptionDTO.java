
package com.example.hauiTrash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionDTO {
    private String code;       // "A", "B", "CUSTOM", "YES", "NO"
    private String label;      // "Chai nhựa", "Đúng", "Sai"
    private Float confidence;  // độ tin cậy (nếu là AI đề xuất)
}