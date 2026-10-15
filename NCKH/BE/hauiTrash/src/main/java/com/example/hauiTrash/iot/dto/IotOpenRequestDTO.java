package com.example.hauiTrash.iot.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IotOpenRequestDTO {
    @NotNull
    @Pattern(regexp = "BIN_0[123]")
    private String binCode;
}
