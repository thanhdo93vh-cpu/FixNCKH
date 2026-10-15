package com.example.hauiTrash.iot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IotRealtimeResponseDTO {
    private String label;
    private String labelDisplay;
    private Float confidence;
    private Integer x1;
    private Integer y1;
    private Integer x2;
    private Integer y2;
}
