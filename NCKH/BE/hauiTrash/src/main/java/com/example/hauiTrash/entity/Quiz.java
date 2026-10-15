package com.example.hauiTrash.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private Long storyId;
    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;
    @Column(name = "passing_score")
    private Integer passingScore;
    @Column(name = "is_active")
    private Boolean isActive;
    @PrePersist
    protected void onCreate() {
        if (isActive == null) isActive = true;
        if (passingScore == null) passingScore = 50;
        if (timeLimitMinutes == null) timeLimitMinutes = 30;
    }
}