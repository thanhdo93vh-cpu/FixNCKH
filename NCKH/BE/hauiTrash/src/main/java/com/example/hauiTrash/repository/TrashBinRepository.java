package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.TrashBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrashBinRepository extends JpaRepository<TrashBin, Long> {

    Optional<TrashBin> findByCode(String code);

    boolean existsByCode(String code);

    Optional<TrashBin> findByNameTrash(String nameTrash);

    List<TrashBin> findAllByIsActiveTrue();
}