package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.AiRequestCreateResponseDTO;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.entity.AiRequest;
import com.example.hauiTrash.repository.AccountRepository;
import com.example.hauiTrash.repository.AiRequestRepository;
import com.example.hauiTrash.service.AiRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
@Service
@RequiredArgsConstructor
public class AiRequestServiceImpl implements AiRequestService {

    private final AiRequestRepository aiRequestRepository;

    private final AccountRepository accountRepository;
    @Override
    public AiRequestCreateResponseDTO createAiRequest(String cloudinaryUrl) {

        long totalStart = System.currentTimeMillis();

        // 1. Tạo AiRequest
        long buildStart = System.currentTimeMillis();

        AiRequest aiRequest = AiRequest.builder()
                .cloudinaryUrl(cloudinaryUrl)
                .account(null)
                .createdAt(Instant.now())
                .build();

        System.out.println("[TIME] Build AiRequest: "
                + (System.currentTimeMillis() - buildStart) + " ms");

        // 2. Lưu vào MySQL
        long saveStart = System.currentTimeMillis();

        AiRequest saved = aiRequestRepository.save(aiRequest);

        System.out.println("[TIME] MySQL save: "
                + (System.currentTimeMillis() - saveStart) + " ms");

        // 3. Tạo response
        long responseStart = System.currentTimeMillis();

        AiRequestCreateResponseDTO response = new AiRequestCreateResponseDTO(
                saved.getId(),
                saved.getCloudinaryUrl(),
                Timestamp.from(saved.getCreatedAt()),
                saved.getFinishedAt() != null
                        ? Timestamp.from(saved.getFinishedAt())
                        : null,
                null
        );

        System.out.println("[TIME] Build response: "
                + (System.currentTimeMillis() - responseStart) + " ms");

        // 4. Tổng thời gian Service
        System.out.println("[TIME] TOTAL createAiRequest: "
                + (System.currentTimeMillis() - totalStart) + " ms");

        return response;
    }

}
