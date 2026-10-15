package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.LeaderboardDTO;
import com.example.hauiTrash.dto.PointHistoryDTO;
import com.example.hauiTrash.dto.UserPointsDTO;
import com.example.hauiTrash.entity.*;
import com.example.hauiTrash.repository.AccountRepository;
import com.example.hauiTrash.repository.PointHistoryRepository;
import com.example.hauiTrash.repository.UserPointsRepository;
import com.example.hauiTrash.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private final UserPointsRepository userPointsRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final AccountRepository accountRepository;

    private static final int LEVEL_UP_POINTS = 100; // 100 điểm = 1 level
    @Override
    public UserPointsDTO getMyPoints() {
        // Lấy trực tiếp từ JwtFilter
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Account account = (Account) auth.getPrincipal();
        return getUserPoints(account.getId());
    }
    @Override
    @Transactional(readOnly = true)
    public UserPointsDTO getUserPoints(Integer accountId) {
        UserPoints up = userPointsRepository.findByAccountId(accountId)
                .orElse(UserPoints.builder()
                        .accountId(accountId)
                        .totalPoints(0)
                        .build());
        int totalPoints = up.getTotalPoints();
        UserRank rank = UserRank.fromPoints(totalPoints);
        return UserPointsDTO.builder()
                .totalPoints(totalPoints)
                .rank(rank)
                .rankDisplayName(rank.getDisplayName())
                .build();
    }
    @Override
    @Transactional(readOnly = true)
    public List<PointHistoryDTO> getPointHistory(Integer accountId) {
        return pointHistoryRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardDTO> getLeaderboard(Pageable pageable) {
        List<UserPoints> topUsers = userPointsRepository.findTopLeaderboard(pageable);
        List<LeaderboardDTO> result = new java.util.ArrayList<>();
        int rank = 1;
        for (UserPoints up : topUsers) {
            var account = accountRepository.findById(up.getAccountId()).orElse(null);
            UserRank userRank = UserRank.fromPoints(up.getTotalPoints());
            result.add(LeaderboardDTO.builder()
                    .rank(rank++)
                    .accountId(up.getAccountId())
                    .fullName(account != null ? account.getFullName() : "Unknown")
                    .avatarUrl(account != null ? account.getAvatarUrl() : null)
                    .totalPoints(up.getTotalPoints())
                    .rankDisplayName(userRank.getDisplayName())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public void addPoints(Integer accountId, String actionType, String referenceId, String description) {
        // 1. Xác định hành động và số điểm
        PointActionType action;
        try {
            action = PointActionType.valueOf(actionType);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid action type: {}", actionType);
            return;
        }
        int pointsToAdd = action.getPoints();
        // 2. Kiểm tra duplicate (tránh cộng 2 lần cho cùng 1 detection)
        if (referenceId != null) {
            boolean exists = pointHistoryRepository.existsByAccountIdAndActionTypeAndReferenceId(
                    accountId, actionType, referenceId);
            if (exists) {
                log.debug("Already added points for accountId: {}, action: {}, referenceId: {}",
                        accountId, actionType, referenceId);
                return;
            }
        }
        // 3. Lưu lịch sử cộng điểm
        PointHistory history = PointHistory.builder()
                .accountId(accountId)
                .points(pointsToAdd)
                .actionType(actionType)
                .referenceId(referenceId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
        pointHistoryRepository.save(history);
        // 4. Cập nhật hoặc tạo mới UserPoints
        UserPoints up = userPointsRepository.findByAccountId(accountId)
                .orElse(UserPoints.builder()
                        .accountId(accountId)
                        .totalPoints(0)
                        .build());

        int newTotal = up.getTotalPoints() + pointsToAdd;
        up.setTotalPoints(newTotal);
        up.setUpdatedAt(LocalDateTime.now());
        userPointsRepository.save(up);

        log.info("Added {} points to accountId: {}, action: {}, new total: {}",
                pointsToAdd, accountId, actionType, newTotal);
    }
    @Override
    public List<PointHistoryDTO> getMyPointHistory() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Account account = (Account) auth.getPrincipal();
        return getPointHistory(account.getId());
    }
    private PointHistoryDTO toDTO(PointHistory history) {
        return PointHistoryDTO.builder()
                .points(history.getPoints())
                .actionType(history.getActionType())
                .description(history.getDescription())
                .createdAt(history.getCreatedAt())
                .build();
    }
}