package com.example.hauiTrash.iot.dto;

import lombok.Data;

@Data
public class IotPredictRequestDTO {
    private Integer aiRequestId;
    private String imageUrl;
    private Float conf = 0.25f;
    private Float iou = 0.6f;
}
