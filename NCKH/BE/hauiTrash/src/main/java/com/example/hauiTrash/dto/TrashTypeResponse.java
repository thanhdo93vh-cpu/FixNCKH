package com.example.hauiTrash.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashTypeResponse {

    private Long id;
    private String name;
    private String code;
    private String description;
    private Boolean isActive;

    private TrashBinResponse trashBin;
}