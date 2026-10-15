package com.example.hauiTrash.dto;

import lombok.Data;

@Data
public class FeedbackRequest {
    private String confirmedLabel; // nhãn user xác nhận hoặc sửa lại
    private String feedbackType;   // CONFIRMED / CORRECTED / REJECTED
    private String comment;
}