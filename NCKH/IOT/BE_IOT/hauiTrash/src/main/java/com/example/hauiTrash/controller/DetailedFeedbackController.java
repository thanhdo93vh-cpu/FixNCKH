package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.FeedbackAnswerDTO;
import com.example.hauiTrash.dto.FeedbackQuestionDTO;
import com.example.hauiTrash.dto.SatisfactionRequestDTO;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.service.DetailedFeedbackService;
import com.example.hauiTrash.service.FeedbackQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/feedback")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DetailedFeedbackController {

    private final FeedbackQuestionService questionService;
    private final DetailedFeedbackService feedbackService;

    /**
     * Bước 1: Ghi nhận mức độ hài lòng
     */
    @PostMapping("/satisfaction")
    public ResponseEntity<ApiResponse<Void>> recordSatisfaction(
            @Valid @RequestBody SatisfactionRequestDTO request,
            @AuthenticationPrincipal Account account) {

        Integer accountId = account != null ? account.getId() : null;
        feedbackService.recordSatisfaction(request, accountId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Cảm ơn phản hồi của bạn!")
                .build());
    }

    /**
     * Bước 2: Lấy câu hỏi feedback cho 1 detection
     */
    @GetMapping("/question/{detectionId}")
    public ResponseEntity<ApiResponse<FeedbackQuestionDTO>> getQuestion(
            @PathVariable Integer detectionId) {

        FeedbackQuestionDTO question = questionService.generateQuestion(detectionId);
        return ResponseEntity.ok(ApiResponse.<FeedbackQuestionDTO>builder()
                .data(question)
                .build());
    }

    /**
     * Bước 3: Lấy tất cả câu hỏi cho 1 AI request
     */
    @GetMapping("/questions/{aiRequestId}")
    public ResponseEntity<ApiResponse<List<FeedbackQuestionDTO>>> getAllQuestions(
            @PathVariable Integer aiRequestId) {

        List<FeedbackQuestionDTO> questions = questionService.generateQuestionsForAiRequest(aiRequestId);
        return ResponseEntity.ok(ApiResponse.<List<FeedbackQuestionDTO>>builder()
                .data(questions)
                .build());
    }

    /**
     * Bước 4: Gửi câu trả lời feedback
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<Void>> submitFeedback(
            @Valid @RequestBody FeedbackAnswerDTO answer,
            @AuthenticationPrincipal Account account) {

        Integer accountId = account != null ? account.getId() : null;
        feedbackService.submitFeedback(answer, accountId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Cảm ơn bạn đã đóng góp ý kiến!")
                .build());
    }
}