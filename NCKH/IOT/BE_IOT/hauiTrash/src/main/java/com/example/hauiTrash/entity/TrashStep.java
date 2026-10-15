package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "trash_step")
public class TrashStep {
   @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // label (vd: plastic, glass)
    @Column(name = "label", nullable = false)
    private String label;

    // tên hiển thị (vd: Chai nhựa)
    @Column(name = "label_display")
    private String labelDisplay;

    // url ảnh minh họa
    @Column(name = "image_url")
    private String imageUrl;

}


