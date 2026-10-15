package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.TrashStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrashStepRepository extends JpaRepository<TrashStep, Long> {
    List<TrashStep> findByLabelIgnoreCase(String label);

}
