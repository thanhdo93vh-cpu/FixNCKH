
package com.example.hauiTrash.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionRequestDTO {
    @NotNull
    private Integer aiRequestId;

    @NotNull
    private String level;  // SATISFIED / UNSATISFIED

    private String reason;
}