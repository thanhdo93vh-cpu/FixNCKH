package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.TrashItemAliasNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface TrashItemAliasNodeRepository
        extends Neo4jRepository<TrashItemAliasNode, Long> {

    Optional<TrashItemAliasNode> findByAlias(String alias);

    Optional<TrashItemAliasNode> findByAliasAndIsActiveTrue(String alias);
}