package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {
    List<PointHistory> findByAccountIdOrderByCreatedAtDesc(Integer accountId);
    boolean existsByAccountIdAndActionTypeAndReferenceId(Integer accountId, String actionType, String referenceId);
}