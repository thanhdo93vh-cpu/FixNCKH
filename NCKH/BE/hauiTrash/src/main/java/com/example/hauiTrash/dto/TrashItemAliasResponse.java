package com.example.hauiTrash.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrashItemAliasResponse {

    private Long id;
    private String alias;
    private String aliasLabel;
    private String aliasDisplay;
    private String aliasSource;
    private Boolean isActive;
}

