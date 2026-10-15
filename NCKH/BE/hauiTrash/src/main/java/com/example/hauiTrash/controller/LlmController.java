package com.example.hauiTrash.controller;

import com.example.hauiTrash.client.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmClient llmClient;

    @GetMapping("/test-type")
    public Object testType(@RequestParam String label, @RequestParam(required = false) String display) {
        return llmClient.suggestTrashType(label, display);
    }

    @GetMapping("/test-knowledge")
    public Object testKnowledge(@RequestParam String label,
                                @RequestParam(required = false) String display,
                                @RequestParam(defaultValue = "Tái chế") String trashType) {
        return llmClient.generateKnowledge(label, display, trashType);
    }
}