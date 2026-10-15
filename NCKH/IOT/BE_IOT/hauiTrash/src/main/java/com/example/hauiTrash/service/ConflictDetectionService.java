package com.example.hauiTrash.service;

import com.example.hauiTrash.entity.Detection;

public interface ConflictDetectionService {

    /**
     * Kiểm tra detection có conflict giữa các classification không
     */
    boolean hasConflict(Detection detection);

    /**
     * Lấy các label khác nhau từ detection
     */
    ConflictLabels getConflictLabels(Detection detection);

    /**
     * Đánh dấu detection cần user lựa chọn
     */
    void markForUserChoice(Detection detection);

    record ConflictLabels(String label1, String labelDisplay1, Float confidence1,
                          String label2, String labelDisplay2, Float confidence2) {}
}