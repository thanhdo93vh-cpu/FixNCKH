package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.HazardNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface HazardNodeRepository
        extends Neo4jRepository<HazardNode, Long> {

    Optional<HazardNode> findByCode(String code);

    Optional<HazardNode> findByName(String name);
}