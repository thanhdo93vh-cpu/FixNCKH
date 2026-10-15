package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.UserPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPointsRepository extends JpaRepository<UserPoints, Integer> {
    Optional<UserPoints> findByAccountId(Integer accountId);

    @Query("SELECT u FROM UserPoints u ORDER BY u.totalPoints DESC")
    List<UserPoints> findTopLeaderboard(org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE UserPoints u SET u.totalPoints = u.totalPoints + :points WHERE u.accountId = :accountId")
    int addPoints(@Param("accountId") Integer accountId, @Param("points") Integer points);
}