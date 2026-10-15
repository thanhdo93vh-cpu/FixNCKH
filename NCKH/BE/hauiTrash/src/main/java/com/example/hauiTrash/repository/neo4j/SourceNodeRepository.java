package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.SourceNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface SourceNodeRepository
        extends Neo4jRepository<SourceNode, Long> {

    Optional<SourceNode> findByCode(String code);

    Optional<SourceNode> findByName(String name);
}