package com.example.hauiTrash.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRank {
    BRONZE(" Đồng", 0, 50),
    SILVER(" Bạc", 51, 150),
    GOLD(" Vàng", 151, 350),
    PLATINUM(" Bạch Kim", 351, 600),
    DIAMOND(" Kim Cương", 601, Integer.MAX_VALUE);

    private final String displayName;
    private final int minPoints;
    private final int maxPoints;

    public static UserRank fromPoints(int points) {
        for (UserRank rank : values()) {
            if (points >= rank.minPoints && points <= rank.maxPoints) {
                return rank;
            }
        }
        return BRONZE;
    }
}