package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.SampleImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SampleImageRepository
        extends JpaRepository<SampleImage, Long> {

    Optional<SampleImage> findByMaterial(String material);
}