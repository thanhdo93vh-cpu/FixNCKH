package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.client.*;
import com.example.hauiTrash.config.GeminiProperties;
import com.example.hauiTrash.dto.*;
import com.example.hauiTrash.entity.*;
import com.example.hauiTrash.service.*;
import com.example.hauiTrash.util.KnowledgeText;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeMappingTest {
    AiPipelineServiceImpl pipeline = mock(AiPipelineServiceImpl.class, CALLS_REAL_METHODS);
    TrashReasoningResultDTO kg = TrashReasoningResultDTO.builder().label("plastic")
            .labelDisplay("plastic_bottle").trashTypeName("RECYCLABLE").trashTypeCode("RECYCLABLE")
            .trashBinCode("BIN_01").materials(List.of("PET")).materialDescriptions(List.of("KG material description"))
            .hazards(List.of("NO_HAZARD")).hazardDescriptions(List.of("KG hazard description"))
            .impacts(List.of("KG impact")).impactDescriptions(List.of("KG impact description"))
            .guidance(List.of("Recycle plastic")).guidanceDescription(List.of("KG treatment description"))
            .guidanceLuY(List.of("KG warning"))
            .guidanceSteps(IntStream.rangeClosed(1,7).mapToObj(i -> "Step " + i).toList()).build();
    TrashItemKnowledge sql = TrashItemKnowledge.builder().material("SQL").materialDescription("SQL")
            .note("SQL").action("SQL").impact("SQL").toxicity("SQL toxicity must not invent KG data").build();

    AiResponseDetailsDTO.DetectionDTO map(String method, LlmClient.KnowledgeGenResult result) {
        var det = Detection.builder().label("plastic_bottle").build();
        if (method.equals("toDTO_ReadOnly")) return ReflectionTestUtils.invokeMethod(pipeline, method, det, null, null, sql, kg);
        Map<String,LlmClient.KnowledgeGenResult> results = new HashMap<>();
        if (kg != null) results.put(kg.getLabel(), result);
        return ReflectionTestUtils.invokeMethod(pipeline, method, det, null, null, sql, kg, results);
    }
    GraphRagService service(ExchangeFunction exchange) {
        var props = new GeminiProperties(); props.setApiKey("test"); props.setModel("test");
        return new GraphRagService(new GeminiLlmClient(WebClient.builder().baseUrl("https://test.invalid")
                .exchangeFunction(exchange).build(), props), new GraphContextBuilder());
    }
    void assertKg(AiResponseDetailsDTO.DetectionDTO d) {
        assertEquals("plastic", d.getLabel()); assertEquals("PET", d.getMaterial());
        assertEquals("KG material description", d.getMaterialDescription());
        assertEquals("RECYCLABLE",d.getTrashType()); assertEquals("BIN_01",d.getBinCode());
        assertEquals("NO_HAZARD",d.getHazard()); assertEquals("KG impact description",d.getDetail().getImpact());
        assertNull(d.getDetail().getToxicity());
        assertTrue(d.getAction().contains("Recycle plastic")); assertTrue(d.getAction().contains("KG treatment description"));
        assertTrue(d.getAction().contains("KG warning"));
        for (String step:kg.getGuidanceSteps()) assertTrue(d.getAction().contains(step));
        assertEquals(kg.getGuidanceSteps(),d.getGuidanceSteps());
    }
    @ParameterizedTest @ValueSource(ints={429,503})
    void httpFailurePreservesKgThroughServiceAndDtoAndCallsOncePerRequest(int status) throws Exception {
        var calls = new AtomicInteger();
        var service = service(r -> { calls.incrementAndGet(); return Mono.just(ClientResponse.create(HttpStatus.valueOf(status))
                .header("Retry-After", "60").body("quota exhausted").build()); });
        ReflectionTestUtils.setField(pipeline,"graphRagService",service);
        Map<String,LlmClient.KnowledgeGenResult> cache = new HashMap<>();
        ReflectionTestUtils.invokeMethod(pipeline,"generateGraphRagOnce",kg,cache);
        ReflectionTestUtils.invokeMethod(pipeline,"generateGraphRagOnce",kg,cache);
        var result = cache.get("plastic"); var dto = map("toDTO_NoLlm",result);
        assertKg(dto); assertEquals("KG_FALLBACK",dto.getSource()); assertEquals("GEMINI_" + status,dto.getReason());
        String json = new ObjectMapper().writeValueAsString(dto);
        assertFalse(json.contains("Kh\u00f4ng c\u00f3 th\u00f4ng tin c\u1ee5 th\u1ec3"));
        assertEquals(7,new ObjectMapper().readTree(json).get("guidanceSteps").size());
        kg.setLabel("another_label");
        ReflectionTestUtils.invokeMethod(pipeline,"generateGraphRagOnce",kg,cache);
        assertEquals("another_label",cache.get("another_label").getLabel());
        assertEquals("GEMINI_" + status,cache.get("another_label").getReason());
        assertEquals(1,calls.get());
    }
    @Test void successfulGeminiCannotOverwriteKgOrIdentifiers() throws Exception {
        String generated = new ObjectMapper().writeValueAsString(Map.of("label","wrong", "material","wrong", "materialDescription","wrong",
                "action","wrong", "impact","wrong", "toxicity","wrong", "hazard","wrong"));
        String body = new ObjectMapper().writeValueAsString(Map.of("candidates",List.of(Map.of("content",Map.of("parts",List.of(Map.of("text",generated)))))));
        var result = service(r -> Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type","application/json").body(body).build())).generateGuidance(kg);
        assertEquals("plastic",result.getLabel()); assertEquals("GEMINI+KG",result.getSource());
        assertEquals("PET",result.getMaterial()); assertNull(result.getToxicity());
        assertEquals(KnowledgeText.action(kg),result.getAction()); assertKg(map("toDTO_NoLlm",result));
    }
    @Test void readOnlyReturnsFullKgWithoutGemini() { assertKg(map("toDTO_ReadOnly",null)); }
    @Test void missingKgStillUsesExistingSqlButDoesNotInventHazardOrToxicity() {
        kg=null;
        for (String method:List.of("toDTO_NoLlm","toDTO_ReadOnly")) {
            var d=map(method,null); assertEquals("SQL",d.getMaterial()); assertEquals("SQL",d.getAction());
            assertNull(d.getHazard()); assertNull(d.getDetail().getToxicity()); assertTrue(d.getGuidanceSteps().isEmpty());
        }
    }
    @Test void realKgToxicityIsRetainedAndAllMaterialsAreKept() {
        kg.setToxicityDescriptions(List.of("KG chemical description", "KG mercury description"));
        kg.setMaterials(List.of("PET","HDPE"));
        var d=map("toDTO_NoLlm",null);
        assertEquals("PET\nHDPE",d.getMaterial());
        assertEquals("KG chemical description\nKG mercury description",d.getDetail().getToxicity());
    }
    @Test void geminiCanFillMissingDescriptiveFieldsOnly() {
        kg.setMaterialDescriptions(List.of()); kg.setGuidance(null); kg.setGuidanceDescription(null);
        kg.setGuidanceLuY(null); kg.setGuidanceSteps(null);
        var g=LlmClient.KnowledgeGenResult.builder().material("wrong").materialDescription("Supplement")
                .action("Supplement action").hazard("invented").toxicity("invented").build();
        var d=map("toDTO_NoLlm",g); assertEquals("PET",d.getMaterial());
        assertEquals("Supplement",d.getMaterialDescription()); assertEquals("Supplement action",d.getAction());
        assertEquals("NO_HAZARD",d.getHazard()); assertNull(d.getDetail().getToxicity());
    }
}
