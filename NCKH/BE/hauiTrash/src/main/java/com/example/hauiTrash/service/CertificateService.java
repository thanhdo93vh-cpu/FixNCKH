package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.CertificateDTO;
import org.springframework.stereotype.Service;

@Service
public interface CertificateService {

    // Kiểm tra và cấp chứng chỉ (nếu đủ 5 bài)
    CertificateDTO checkAndIssueCertificate(Integer userId);

    // Lấy chứng chỉ của user
    CertificateDTO getMyCertificate(Integer userId);

    // Kiểm tra xem đã đủ điều kiện nhận chứng chỉ chưa
    boolean isEligibleForCertificate(Integer userId);
}