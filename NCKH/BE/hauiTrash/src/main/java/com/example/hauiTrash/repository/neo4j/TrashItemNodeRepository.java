package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.TrashItemNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface TrashItemNodeRepository
        extends Neo4jRepository<TrashItemNode, Long> {

    Optional<TrashItemNode> findByLabel(String label);

    Optional<TrashItemNode> findByLabelDisplay(String labelDisplay);

    Optional<TrashItemNode> findByAliases_Alias(String alias);
}