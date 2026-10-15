package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.SampleImageResponse;
import com.example.hauiTrash.entity.SampleImage;
import com.example.hauiTrash.repository.SampleImageRepository;
import com.example.hauiTrash.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final SampleImageRepository sampleImageRepository;

    @Override
    public SampleImageResponse getSampleImage(String material) {

        String normalizedMaterial = normalizeMaterial(material);

        if (normalizedMaterial == null) {
            throw new RuntimeException("Không xác định được loại rác");
        }

        SampleImage image = sampleImageRepository
                .findByMaterial(normalizedMaterial)
                .orElseThrow(() ->
                        new RuntimeException("Không tìm thấy ảnh mẫu tương tự"));

        return SampleImageResponse.builder()
                .material(image.getMaterial())
                .imageUrl(image.getImageUrl())
                .build();
    }

    private String normalizeMaterial(String material) {
        if (material == null || material.isBlank()) {
            return null;
        }
        String lower = material.trim().toLowerCase();
        if (lower.contains("plastic") || lower.contains("nhựa")) {
            return "plastic";
        }
        if (lower.contains("paper") || lower.contains("giấy")) {
            return "paper";
        }
        if (lower.contains("glass") || lower.contains("thủy tinh")) {
            return "glass";
        }
        if (lower.contains("metal") || lower.contains("kim loại")) {
            return "metal";
        }
        return null;
    }
}
