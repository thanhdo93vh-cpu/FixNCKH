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
        Account account = getCurrentAccountOrNull();
        AiRequest aiRequest = AiRequest.builder()
                .cloudinaryUrl(cloudinaryUrl)
                .account(account)
                .createdAt(Instant.now())
                .build();
        AiRequest saved = aiRequestRepository.save(aiRequest);

        return new AiRequestCreateResponseDTO(
                saved.getId(),
                saved.getCloudinaryUrl(),
                Timestamp.from(saved.getCreatedAt()),
                saved.getFinishedAt() != null ? Timestamp.from(saved.getFinishedAt()) : null,
                saved.getAccount() != null ? saved.getAccount().getId() : null
        );
    }

    private Account getCurrentAccountOrNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;

        Object principal = auth.getPrincipal();
        if (principal == null || "anonymousUser".equals(principal)) return null;

        if (principal instanceof Account acc) {
            return acc; //  JwtFilter set principal = acc
        }

        return null;
    }

}
