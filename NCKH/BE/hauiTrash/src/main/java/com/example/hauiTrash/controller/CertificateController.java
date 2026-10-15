package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.CertificateDTO;
import com.example.hauiTrash.service.CertificateService;
import com.example.hauiTrash.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/certificate")

@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final JwtUtil jwtUtil;


    @PostMapping("/check-and-issue")
    public ResponseEntity<ApiResponse<CertificateDTO>> checkAndIssueCertificate(
            @RequestHeader("Authorization") String authHeader) {

        Integer accountId = jwtUtil.getAccountId(authHeader.substring(7));
        CertificateDTO certificate = certificateService.checkAndIssueCertificate(accountId);

        return ResponseEntity.ok(ApiResponse.<CertificateDTO>builder()
                .message(certificate.getMessage())
                .data(certificate)
                .build());
    }


    @GetMapping("/my-certificate")
    public ResponseEntity<ApiResponse<CertificateDTO>> getMyCertificate(
            @RequestHeader("Authorization") String authHeader) {

        Integer accountId = jwtUtil.getAccountId(authHeader.substring(7));
        CertificateDTO certificate = certificateService.getMyCertificate(accountId);

        return ResponseEntity.ok(ApiResponse.<CertificateDTO>builder()
                .message(certificate.getMessage())
                .data(certificate)
                .build());
    }


    @GetMapping("/eligible")
    public ResponseEntity<ApiResponse<Boolean>> checkEligible(
            @RequestHeader("Authorization") String authHeader) {

        Integer accountId = jwtUtil.getAccountId(authHeader.substring(7));
        boolean isEligible = certificateService.isEligibleForCertificate(accountId);

        String message = isEligible ?
                "Bạn đã đủ điều kiện nhận chứng chỉ. Hãy ấn 'Nhận chứng chỉ' để lưu lại!" :
                "Bạn cần hoàn thành thêm bài trắc nghiệm nữa để nhận chứng chỉ";

        return ResponseEntity.ok(ApiResponse.<Boolean>builder()
                .message(message)
                .data(isEligible)
                .build());
    }
}