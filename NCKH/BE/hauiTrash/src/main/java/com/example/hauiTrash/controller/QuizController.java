package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.*;
import com.example.hauiTrash.service.QuizService;
import com.example.hauiTrash.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final JwtUtil jwtUtil;

    @GetMapping("/story/{storyId}")
    public ResponseEntity<ApiResponse<QuizDTO>> getQuizByStory(@PathVariable Long storyId) {
        QuizDTO quiz = quizService.getQuizByStoryId(storyId);
        return ResponseEntity.ok(ApiResponse.<QuizDTO>builder()
                .message("Lấy bài trắc nghiệm thành công")
                .data(quiz)
                .build());
    }

    @PostMapping("/start/{storyId}")
    public ResponseEntity<ApiResponse<QuizStartDTO>> startQuiz(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long storyId) {

        Integer accountId = jwtUtil.getAccountId(authHeader.substring(7));

        QuizStartDTO quizStart = quizService.startQuiz(accountId, storyId);
        return ResponseEntity.ok(ApiResponse.<QuizStartDTO>builder()
                .message("Bắt đầu làm bài thành công")
                .data(quizStart)
                .build());
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<QuizResultDTO>> submitQuiz(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody QuizSubmitDTO submitData) {

        Integer accountId = jwtUtil.getAccountId(authHeader.substring(7));
        QuizResultDTO result = quizService.submitQuiz(accountId, submitData);
        return ResponseEntity.ok(ApiResponse.<QuizResultDTO>builder()
                .message("Nộp bài thành công")
                .data(result)
                .build());
    }

    @GetMapping("/completed")
    public ResponseEntity<ApiResponse<List<CompletedQuizDTO>>> getCompletedQuizzes(
            @RequestHeader("Authorization") String authHeader) {

        Integer accountId = jwtUtil.getAccountId(authHeader.substring(7));
        List<CompletedQuizDTO> completed = quizService.getCompletedQuizzes(accountId);
        return ResponseEntity.ok(ApiResponse.<List<CompletedQuizDTO>>builder()
                .message("Lấy danh sách bài đã làm thành công")
                .data(completed)
                .build());
    }
}