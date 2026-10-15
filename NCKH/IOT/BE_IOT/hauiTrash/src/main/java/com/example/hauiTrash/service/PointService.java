package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.LeaderboardDTO;
import com.example.hauiTrash.dto.PointHistoryDTO;
import com.example.hauiTrash.dto.UserPointsDTO;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface PointService {
    UserPointsDTO getMyPoints();
    UserPointsDTO getUserPoints(Integer accountId);
    List<PointHistoryDTO> getPointHistory(Integer accountId);
    List<LeaderboardDTO> getLeaderboard(Pageable pageable);
    List<PointHistoryDTO> getMyPointHistory();
    void addPoints(Integer accountId, String actionType, String referenceId, String description);
}