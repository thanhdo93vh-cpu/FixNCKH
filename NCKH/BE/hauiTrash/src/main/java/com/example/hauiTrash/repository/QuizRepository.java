package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByStoryId(Long storyId);

    List<Quiz> findByIsActiveTrue();

    Optional<Quiz> findByIdAndIsActiveTrue(Long id);
}