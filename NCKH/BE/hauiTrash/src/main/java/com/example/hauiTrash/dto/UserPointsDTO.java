package com.example.hauiTrash.dto;

import com.example.hauiTrash.entity.UserRank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserPointsDTO {
    private Integer totalPoints;
    private UserRank rank;
    private String rankDisplayName;
}