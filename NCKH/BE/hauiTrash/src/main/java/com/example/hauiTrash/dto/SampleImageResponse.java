package com.example.hauiTrash.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleImageResponse {
    private String material;
    private String imageUrl;
}