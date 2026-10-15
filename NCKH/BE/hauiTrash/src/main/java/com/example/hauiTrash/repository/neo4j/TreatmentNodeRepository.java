package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.TreatmentNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface TreatmentNodeRepository
        extends Neo4jRepository<TreatmentNode, Long> {

    Optional<TreatmentNode> findByCode(String code);

    Optional<TreatmentNode> findByName(String name);
}