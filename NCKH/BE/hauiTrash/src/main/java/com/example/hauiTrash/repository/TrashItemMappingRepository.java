package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.TrashItemMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrashItemMappingRepository extends JpaRepository<TrashItemMapping, Integer> {

    @Modifying
    @Query("update TrashItemMapping m set m.isActive=false where m.trashItem.id=:itemId and m.isActive=true")
    int deactivateAll(@Param("itemId") Integer itemId);
    Optional<TrashItemMapping> findActiveByTrashItemId(Integer trashItemId);

    @Query("""
        select m from TrashItemMapping m
        left join fetch m.trashType
        where m.isActive = true and m.trashItem.id in :ids
    """)
    List<TrashItemMapping> findActiveByTrashItemIdIn(@Param("ids") List<Integer> ids);
}