package com.example.hauiTrash.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TrashBinDTO {
    private Long id;
    @NotBlank(message = "Tên thùng rác không được để trống")
    @Size(max = 100, message = "Tên thùng rác không quá 100 ký tự")
    private String nameTrash;

    @NotBlank(message = "Mã thùng rác không được để trống")
    @Size(max = 50, message = "Mã thùng rác không quá 50 ký tự")
    private String code;  // giay, nhua, kim_loai, thuy_tinh

    private String description;

    private Boolean isActive;
}
