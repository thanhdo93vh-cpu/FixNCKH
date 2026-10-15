package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.RuleNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface RuleNodeRepository
        extends Neo4jRepository<RuleNode, Long> {

    Optional<RuleNode> findByCode(String code);

    Optional<RuleNode> findByName(String name);
}