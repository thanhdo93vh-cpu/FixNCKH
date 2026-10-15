package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.TrashItemAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrashItemAliasRepository extends JpaRepository<TrashItemAlias, Integer> {
    Optional<TrashItemAlias> findByAlias(String alias);
    Optional<TrashItemAlias> findByAliasIgnoreCase(String alias);
}