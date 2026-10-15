package com.example.hauiTrash.dto;

import com.example.hauiTrash.entity.UserRank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountInfo {
    private Integer id;
    private String fullName;
    private String phone;
    private String email;
    private String role;
    private Integer totalPoints;
    private UserRank rank;
    private String rankDisplayName;
}
