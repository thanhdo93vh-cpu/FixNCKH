package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.UserQuiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserQuizRepository extends JpaRepository<UserQuiz, Long> {

    Optional<UserQuiz> findByUserIdAndQuizId(Integer accountId, Long quizId);

    List<UserQuiz> findByUserId(Integer accountId);

    List<UserQuiz> findByUserIdAndIsPassedTrue(Integer accountId);

    @Query("SELECT COUNT(uq) FROM UserQuiz uq WHERE uq.userId = :userId AND uq.isPassed = true")
    long countPassedQuizzesByUserId(@Param("userId") Long userId);

    @Query("SELECT uq.quizId FROM UserQuiz uq WHERE uq.userId = :userId AND uq.isPassed = true")
    List<Long> findPassedQuizIdsByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndQuizIdAndIsPassedTrue(Long userId, Long quizId);
}