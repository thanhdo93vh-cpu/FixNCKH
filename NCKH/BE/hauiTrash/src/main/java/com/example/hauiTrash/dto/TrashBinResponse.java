package com.example.hauiTrash.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashBinResponse {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Boolean isActive;
    private String color;
    private String colorCode;
}
