
package com.example.hauiTrash.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackAnswerDTO {
    @NotNull
    private Integer detectionId;

    @NotNull
    private String selectedCode;   // "YES", "NO", "A", "B", "CUSTOM"

    private String customLabel;     // nếu chọn CUSTOM

    private String comment;         // góp ý thêm
}