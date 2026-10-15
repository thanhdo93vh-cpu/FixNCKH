package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "detections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Detection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ai_request_id", nullable = false)
    private AiRequest aiRequest;

    @Column(name = "label", length = 120)
    private String label;

    @Column(name = "label_display", length = 180)
    private String labelDisplay;

    @Column(name = "confidence")
    private Float confidence;

    @Column(name = "annotated_url", length = 1000)
    private String annotatedUrl;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "x1")
    private Integer x1;

    @Column(name = "y1")
    private Integer y1;

    @Column(name = "x2")
    private Integer x2;

    @Column(name = "y2")
    private Integer y2;

    @Column(name = "crop_url", length = 1000)
    private String cropUrl;

    @OneToMany(mappedBy = "detection", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Classification> classifications = new ArrayList<>();
}