package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.CertificateDTO;
import com.example.hauiTrash.dto.CompletedQuizDTO;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.entity.Certificate;
import com.example.hauiTrash.repository.AccountRepository;
import com.example.hauiTrash.repository.CertificateRepository;
import com.example.hauiTrash.repository.UserQuizRepository;
import com.example.hauiTrash.service.CertificateService;
import com.example.hauiTrash.service.QuizService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private final CertificateRepository certificateRepository;
    private final UserQuizRepository userQuizRepository;
    private final AccountRepository accountRepository;
    private final QuizService quizService;
    private final ObjectMapper objectMapper;

    private static final int REQUIRED_QUIZZES = 5;

    @Override
    @Transactional
    public CertificateDTO checkAndIssueCertificate(Integer userId) {
        // Đếm số bài đã đậu
        long passedQuizzes = userQuizRepository.countPassedQuizzesByUserId(userId.longValue());
        List<Long> passedQuizIds = userQuizRepository.findPassedQuizIdsByUserId(userId.longValue());

        // Kiểm tra chứng chỉ đã tồn tại chưa
        Certificate existingCert = certificateRepository.findByUserId(userId.longValue()).orElse(null);

        if (existingCert != null) {
            existingCert.setTotalQuizzesPassed((int) passedQuizzes);
            try {
                existingCert.setCompletedQuizIds(objectMapper.writeValueAsString(passedQuizIds));
            } catch (Exception e) {
                existingCert.setCompletedQuizIds("[]");
            }

            if (passedQuizzes >= REQUIRED_QUIZZES && existingCert.getIssuedAt() == null) {
                existingCert.setIssuedAt(LocalDateTime.now());
            }

            certificateRepository.save(existingCert);
            return buildCertificateDTO(existingCert, userId);
        }

        // Tạo chứng chỉ mới
        String certificateCode = generateCertificateCode(userId);

        String completedQuizIdsJson;
        try {
            completedQuizIdsJson = objectMapper.writeValueAsString(passedQuizIds);
        } catch (Exception e) {
            completedQuizIdsJson = "[]";
        }

        Certificate newCert = Certificate.builder()
                .userId(userId.longValue())
                .certificateCode(certificateCode)
                .totalQuizzesRequired(REQUIRED_QUIZZES)
                .totalQuizzesPassed((int) passedQuizzes)
                .completedQuizIds(completedQuizIdsJson)
                .build();

        if (passedQuizzes >= REQUIRED_QUIZZES) {
            newCert.setIssuedAt(LocalDateTime.now());
        }

        certificateRepository.save(newCert);
        return buildCertificateDTO(newCert, userId);
    }

    @Override
    public CertificateDTO getMyCertificate(Integer userId) {
        Certificate certificate = certificateRepository.findByUserId(userId.longValue())
                .orElseThrow(() -> new RuntimeException("Bạn chưa có chứng chỉ nào. Hãy hoàn thành "
                        + REQUIRED_QUIZZES + " bài trắc nghiệm để nhận chứng chỉ!"));

        return buildCertificateDTO(certificate, userId);
    }

    @Override
    public boolean isEligibleForCertificate(Integer userId) {
        long passedQuizzes = userQuizRepository.countPassedQuizzesByUserId(userId.longValue());
        return passedQuizzes >= REQUIRED_QUIZZES;
    }

    // ==================== PRIVATE METHODS ====================

    private CertificateDTO buildCertificateDTO(Certificate certificate, Integer userId) {
        // Lấy thông tin account - dùng Integer trực tiếp
        Account account = accountRepository.findById(userId).orElse(null);

        // Lấy danh sách bài đã hoàn thành
        List<CompletedQuizDTO> completedQuizzes = quizService.getCompletedQuizzes(userId);

        String message;
        if (certificate.getTotalQuizzesPassed() >= REQUIRED_QUIZZES) {
            message = " Chúc mừng! Bạn đã hoàn thành " + REQUIRED_QUIZZES +
                    " bài trắc nghiệm và nhận được chứng chỉ.";
        } else {
            int remaining = REQUIRED_QUIZZES - certificate.getTotalQuizzesPassed();
            message = " Bạn đã hoàn thành " + certificate.getTotalQuizzesPassed() + "/" + REQUIRED_QUIZZES +
                    " bài trắc nghiệm. Cần " + remaining + " bài nữa để nhận chứng chỉ!";
        }

        return CertificateDTO.builder()
                .certificateCode(certificate.getCertificateCode())
                .totalQuizzesRequired(certificate.getTotalQuizzesRequired())
                .totalQuizzesPassed(certificate.getTotalQuizzesPassed())
                .issuedAt(certificate.getIssuedAt())
                .userName(account != null ? account.getFullName() : "Người dùng")
                .completedQuizzes(completedQuizzes)
                .message(message)
                .build();
    }

    private String generateCertificateCode(Integer userId) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "CERT-" + date + "-" + userId + "-" + uuid;
    }
}