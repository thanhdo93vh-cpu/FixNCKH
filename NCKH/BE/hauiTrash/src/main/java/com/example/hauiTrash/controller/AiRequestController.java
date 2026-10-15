package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.AiRequestCreateResponseDTO;
import com.example.hauiTrash.service.AiRequestService;
import com.example.hauiTrash.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ai_request")
@CrossOrigin(origins = "*")
public class AiRequestController {

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private AiRequestService aiRequestService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AiRequestCreateResponseDTO> createAiRequest(
            @RequestParam("file") MultipartFile file) {

        long totalStart = System.currentTimeMillis();

        // 1. Đọc ảnh gốc để sau này gửi trực tiếp sang Python
        byte[] imageBytes;

        try {
            imageBytes = file.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Không thể đọc file ảnh: " + e.getMessage());
        }

        System.out.println("[INFO] File name: " + file.getOriginalFilename());
        System.out.println("[INFO] File size: " + file.getSize() + " bytes");
        System.out.println("[INFO] File size: " + (file.getSize() / 1024.0) + " KB");

        // 2. Upload ảnh lên Cloudinary
        long uploadStart = System.currentTimeMillis();

        String cloudinaryUrl = cloudinaryService.uploadImage(file);

        System.out.println("[TIME] Cloudinary upload: "
                + (System.currentTimeMillis() - uploadStart) + " ms");

        // 3. Lưu URL Cloudinary vào MySQL
        long requestStart = System.currentTimeMillis();

        AiRequestCreateResponseDTO response =
                aiRequestService.createAiRequest(cloudinaryUrl);

        System.out.println("[TIME] createAiRequest service: "
                + (System.currentTimeMillis() - requestStart) + " ms");



        System.out.println("[TIME] Controller total: "
                + (System.currentTimeMillis() - totalStart) + " ms");

        return ResponseEntity.ok(response);
    }
}

