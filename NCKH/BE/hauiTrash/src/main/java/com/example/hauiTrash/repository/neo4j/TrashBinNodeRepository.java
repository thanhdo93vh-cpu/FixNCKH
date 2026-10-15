package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.TrashBinNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface TrashBinNodeRepository
        extends Neo4jRepository<TrashBinNode, Long> {

    Optional<TrashBinNode> findByCode(String code);

    Optional<TrashBinNode> findByName(String name);

    Optional<TrashBinNode> findByCodeAndIsActiveTrue(String code);
}