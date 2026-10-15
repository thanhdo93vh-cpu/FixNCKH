package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.TrashType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrashTypeRepository extends JpaRepository<TrashType, Integer> {
    Optional<TrashType> findByCode(String code);
}