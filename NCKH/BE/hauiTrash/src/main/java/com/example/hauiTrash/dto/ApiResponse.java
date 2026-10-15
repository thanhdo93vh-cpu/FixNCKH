package com.example.hauiTrash.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private String message;
    private T data;
}
