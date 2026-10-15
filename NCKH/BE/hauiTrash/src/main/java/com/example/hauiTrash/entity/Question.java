package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "quiz_id", nullable = false)
    private Long quizId;                // Thuộc quiz nào

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;        // Nội dung câu hỏi

    @Column(name = "option_a", columnDefinition = "TEXT")
    private String optionA;

    @Column(name = "option_b", columnDefinition = "TEXT")
    private String optionB;

    @Column(name = "option_c", columnDefinition = "TEXT")
    private String optionC;

    @Column(name = "option_d", columnDefinition = "TEXT")
    private String optionD;

    @Column(name = "correct_answer", nullable = false)
    private String correctAnswer;       // A, B, C, D

    private Integer points;             // Điểm cho câu này

    private String explanation;         // Giải thích đáp án

    @PrePersist
    protected void onCreate() {
        if (points == null) points = 10;
    }
}