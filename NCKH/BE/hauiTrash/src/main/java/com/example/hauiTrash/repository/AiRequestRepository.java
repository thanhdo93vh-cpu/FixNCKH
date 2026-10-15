package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiRequestRepository extends JpaRepository<AiRequest, Integer> {
    @Query("""
        select distinct r
        from AiRequest r
        left join fetch r.detections d
        left join fetch r.account a
        where r.id = :id
    """)
    Optional<AiRequest> findByIdWithDetections(Integer id);

    @Query("""
        select distinct r
        from AiRequest r
        left join fetch r.detections d
        where r.account.id = :accountId
        order by r.createdAt desc
    """)
    java.util.List<AiRequest> findHistoryByAccountId(@Param("accountId") Integer accountId);
}
