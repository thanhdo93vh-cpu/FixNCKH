package com.example.hauiTrash.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashItemResponse {
    private String label;
    private String labelDisplay;
    private Boolean isActive;

    private String material;
    private String note;
    private String action;

    private String trashType;
    private String trashTypeCode;

    private TrashBinResponse trashBin;

}
