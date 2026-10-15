package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CertificateDTO {
    private String certificateCode;
    private Integer totalQuizzesRequired;     // Số bài cần đậu
    private Integer totalQuizzesPassed;       // Số bài đã đậu
    private LocalDateTime issuedAt;           // Ngày cấp chứng chỉ
    private String userName;
    private List<CompletedQuizDTO> completedQuizzes; // Danh sách chi tiết các bài đã làm
    private String message;                   // Thông báo
}