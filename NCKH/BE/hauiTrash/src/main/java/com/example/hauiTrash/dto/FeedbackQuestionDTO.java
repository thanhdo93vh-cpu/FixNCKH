
package com.example.hauiTrash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackQuestionDTO {
    private Integer detectionId;
    private String cropImageUrl;
    private String originalLabel;
    private Float originalConfidence;
    private String questionType;  // SINGLE_CONFIRM / MULTIPLE_CHOICE
    private List<OptionDTO> options;
    private String instruction;
}