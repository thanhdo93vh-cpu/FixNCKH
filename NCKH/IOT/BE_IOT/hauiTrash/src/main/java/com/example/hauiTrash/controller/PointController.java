package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.LeaderboardDTO;
import com.example.hauiTrash.dto.PointHistoryDTO;
import com.example.hauiTrash.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;
    // GET /api/v1/points/leaderboard?page=0&size=10
    @GetMapping("/leaderboard")// lấy bảng rank
    public ResponseEntity<ApiResponse<List<LeaderboardDTO>>> getLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        List<LeaderboardDTO> leaderboard = pointService.getLeaderboard(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.<List<LeaderboardDTO>>builder()
                .message("Lấy bảng xếp hạng thành công")
                .data(leaderboard)
                .build());
    }
    @GetMapping("/history")// khác lấy lịch sử detect
    public ResponseEntity<ApiResponse<List<PointHistoryDTO>>> getMyHistory() {
        List<PointHistoryDTO> history = pointService.getMyPointHistory();
        return ResponseEntity.ok(ApiResponse.<List<PointHistoryDTO>>builder()
                .message("Lấy lịch sử điểm thành công")
                .data(history)
                .build());
    }
}