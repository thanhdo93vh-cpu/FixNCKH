package com.example.hauiTrash.client;
import com.example.hauiTrash.config.GeminiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class GeminiFallbackTest {
    GeminiLlmClient client(ExchangeFunction exchange) {
        var props = new GeminiProperties(); props.setApiKey("test"); props.setModel("test");
        return new GeminiLlmClient(WebClient.builder().baseUrl("https://test.invalid").exchangeFunction(exchange).build(), props);
    }
    ClientResponse response(String json) throws Exception {
        String body = new ObjectMapper().writeValueAsString(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", json)))))));
        return ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body).build();
    }
    @ParameterizedTest @ValueSource(strings={"", "{}", "{ }", "null", "{bad", "{\"material\":\" \"}", "{\"material\":\"Không có thông tin cụ thể trong dữ liệu tri thức.\"}"})
    void emptyOrInvalidReturnsNull(String json) throws Exception {
        var response = response(json);
        assertNull(client(r -> Mono.just(response)).generateAugmentedKnowledge("test", "test", "test", List.of()));
    }
    @Test void partialKnowledgePreservesMissingFields() throws Exception {
        var response = response("{\"material\":\"Plastic\"}");
        var result = client(r -> Mono.just(response)).generateAugmentedKnowledge("test", "test", "test", List.of());
        assertEquals("Plastic",result.getMaterial()); assertNull(result.getAction());
    }
    @ParameterizedTest @ValueSource(ints={429,503,400,401,403,404,500})
    void httpErrorNeverRetries(int status) {
        var calls=new AtomicInteger();
        var client=client(r -> {calls.incrementAndGet(); return Mono.just(ClientResponse.create(HttpStatus.valueOf(status)).build());});
        var outcome=client.generateAugmentedKnowledgeOutcome("test","test","test",List.of());
        assertNull(outcome.knowledge()); assertEquals("GEMINI_" + status,outcome.reason());
        assertEquals(1,calls.get());
    }
    @Test void ignoresGeminiIdentifiers() throws Exception {
        var response = response("{\"label\":\"wrong\",\"source\":\"fake\",\"reason\":\"fake\",\"action\":\"Recycle\"}");
        var result = client(r -> Mono.just(response)).generateAugmentedKnowledge("plastic", "test", "test", List.of());
        assertEquals("Recycle", result.getAction());
        assertNull(result.getLabel()); assertNull(result.getSource()); assertNull(result.getReason());
    }
    @Test void timeoutFallsBack() {
        var calls = new AtomicInteger();
        var client=client(r -> { calls.incrementAndGet(); return Mono.error(new java.util.concurrent.TimeoutException("test timeout")); });
        assertNull(client.generateAugmentedKnowledge("test","test","test",List.of()));
        assertEquals(1, calls.get());
    }
}
