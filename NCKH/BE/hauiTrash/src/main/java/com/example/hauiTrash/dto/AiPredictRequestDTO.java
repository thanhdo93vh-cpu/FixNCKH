package com.example.hauiTrash.dto;

import lombok.Data;

@Data
public class AiPredictRequestDTO {
    private Integer aiRequestId; // id của ai_request trong DB
    private String imageUrl; // url ảnh gốc (cloudinary)
    private Float conf = 0.25f; //conf = ngưỡng độ tin cậy tối thiểu
    private Float iou = 0.6f; //Quyết định 2 bounding box có bị coi là trùng nhau không
}
//IoU = diện tích giao nhau / diện tích hợp