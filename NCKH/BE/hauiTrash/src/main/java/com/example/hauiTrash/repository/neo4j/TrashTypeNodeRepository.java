package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.TrashTypeNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface TrashTypeNodeRepository
        extends Neo4jRepository<TrashTypeNode, Long> {

    Optional<TrashTypeNode> findByCode(String code);

    Optional<TrashTypeNode> findByName(String name);

    Optional<TrashTypeNode> findByCodeAndIsActiveTrue(String code);
}