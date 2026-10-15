package com.example.hauiTrash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeaderboardDTO {
    private Integer rank;
    private Integer accountId;
    private String fullName;
    private String avatarUrl;
    private Integer totalPoints;
    private String rankDisplayName;
}