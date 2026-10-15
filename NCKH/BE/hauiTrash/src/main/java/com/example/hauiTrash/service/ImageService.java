package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.SampleImageResponse;
import org.springframework.stereotype.Service;

@Service
public interface ImageService {
    SampleImageResponse getSampleImage(String material);
}
