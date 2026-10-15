package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    Optional<QuizAttempt> findByUserIdAndQuizIdAndIsCompletedFalse(Long userId, Long quizId);

    void deleteByUserIdAndQuizId(Long userId, Long quizId);

    boolean existsByUserIdAndQuizIdAndIsCompletedFalse(Long userId, Long quizId);
}