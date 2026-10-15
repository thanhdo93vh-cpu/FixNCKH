package com.example.hauiTrash.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    private String phone;

    @NotBlank
    private String password;

    private String fullName;
}
