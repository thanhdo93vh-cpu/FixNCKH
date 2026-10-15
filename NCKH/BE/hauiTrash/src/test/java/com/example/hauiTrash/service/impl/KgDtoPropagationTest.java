package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.*;
import com.example.hauiTrash.repository.neo4j.TrashReasoningRepository;
import com.example.hauiTrash.iot.dto.IotDetailResponseDTO;
import com.example.hauiTrash.iot.service.impl.IotServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KgDtoPropagationTest {
    @Test void repositoryMapsAllGuidanceAndExistingHazardImpactFields() {
        Map<String,Object> row = new HashMap<>();
        row.put("label","plastic"); row.put("materials",List.of("PET","HDPE"));
        row.put("guidance",List.of("Guidance A","Guidance B"));
        row.put("guidanceDescription",List.of("Description A","Description B"));
        row.put("guidanceLuY",List.of("Note A","Note B"));
        row.put("guidanceSteps",List.of("Step 1","Step 2","Step 3","Step 4","Step 5","Step 6","Step 7"));
        row.put("hazards",List.of("CHEMICAL")); row.put("hazardDescriptions",List.of("KG hazard"));
        row.put("toxicityDescriptions",List.of("KG chemical description"));
        row.put("impacts",List.of("KG impact")); row.put("impactDescriptions",List.of("KG impact description"));
        TrashReasoningResultDTO dto = ReflectionTestUtils.invokeMethod(new TrashReasoningRepository(null),"toDTO",row);
        assertEquals(row.get("guidance"),dto.getGuidance());
        assertEquals(row.get("guidanceDescription"),dto.getGuidanceDescription());
        assertEquals(row.get("guidanceLuY"),dto.getGuidanceLuY());
        assertEquals(row.get("guidanceSteps"),dto.getGuidanceSteps());
        assertEquals(row.get("hazards"),dto.getHazards());
        assertEquals(row.get("toxicityDescriptions"),dto.getToxicityDescriptions());
        assertEquals(row.get("impactDescriptions"),dto.getImpactDescriptions());
    }
    @Test void iotPreservesKnowledgeAndProvenance() {
        var dto = AiResponseDetailsDTO.DetectionDTO.builder().label("plastic").material("PET")
                .materialDescription("KG material description").action("Full KG action").hazard("NO_HAZARD")
                .source("KG_FALLBACK").reason("GEMINI_429").binCode("BIN_01")
                .guidanceSteps(List.of("Step 1","Step 2"))
                .detail(AiResponseDetailsDTO.DetailDTO.builder().impact("KG impact").build()).build();
        IotDetailResponseDTO.DetectionDTO out = ReflectionTestUtils.invokeMethod(
                mock(IotServiceImpl.class,CALLS_REAL_METHODS),"mapDetailDetection",dto);
        assertEquals(dto.getLabel(),out.getLabel()); assertEquals(dto.getMaterialDescription(),out.getMaterialDescription());
        assertEquals(dto.getAction(),out.getAction()); assertEquals(dto.getHazard(),out.getHazard());
        assertEquals(dto.getSource(),out.getSource()); assertEquals(dto.getReason(),out.getReason());
        assertEquals(dto.getBinCode(),out.getBinCode()); assertEquals(dto.getGuidanceSteps(),out.getGuidanceSteps());
        assertEquals(dto.getDetail().getImpact(),out.getDetail().getImpact());
    }
}
