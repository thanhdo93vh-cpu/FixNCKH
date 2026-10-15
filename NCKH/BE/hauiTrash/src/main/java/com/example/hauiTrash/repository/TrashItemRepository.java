package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.TrashItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrashItemRepository extends JpaRepository<TrashItem, Integer> {
    Optional<TrashItem> findByLabel(String label);
    List<TrashItem> findAllByLabelIn(List<String> labels);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TrashItem t where t.id = :id")
    Optional<TrashItem> findByIdForUpdate(@Param("id") Integer id);
    List<TrashItem> findByLabelDisplayIgnoreCase(String labelDisplay);
}

