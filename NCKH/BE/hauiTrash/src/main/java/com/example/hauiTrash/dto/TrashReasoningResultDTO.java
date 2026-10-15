package com.example.hauiTrash.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashReasoningResultDTO {

    private Long itemId;

    private String label;

    private String labelDisplay;

    private List<String> materials;
    private List<String> materialDescriptions;
    // Rule
    private String ruleCode;

    private String ruleName;

    private String ruleDescription;

    // Trash Type
    private String trashTypeCode;

    private String trashTypeName;

    private String trashTypeDescription;

    // Trash Bin
    private String trashBinCode;

    private String trashBinName;
    private String trashBinColorCode;
    private String trashBinDescription;

    // Hazard
    private List<String> hazards;
    private List<String> hazardDescriptions;
    // Descriptions of existing CHEMICAL / HEAVY_METAL / MERCURY Hazard nodes only.
    private List<String> toxicityDescriptions;

    private List<String> impacts;
    private List<String> impactDescriptions;
    // Treatment
    private String treatmentCode;

    private String treatmentName;

    private String treatmentDescription;

    // Guidance
    private List<String> guidance;
    private List<String> guidanceDescription;
    private List<String> guidanceLuY;
    private List<String> guidanceSteps;

    // Source
    private List<String> sources;
}
