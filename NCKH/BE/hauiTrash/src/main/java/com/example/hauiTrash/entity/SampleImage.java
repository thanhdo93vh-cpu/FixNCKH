package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sample_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SampleImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String material;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;
}