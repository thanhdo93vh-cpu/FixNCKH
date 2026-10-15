package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {



    List<Question> findByQuizId(Long quizId);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.quizId = :quizId")
    Integer countByQuizId(@Param("quizId") Long quizId);
}