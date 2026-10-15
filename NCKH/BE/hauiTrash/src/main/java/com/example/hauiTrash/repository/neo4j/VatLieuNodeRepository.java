package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.VatLieuNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface VatLieuNodeRepository
        extends Neo4jRepository<VatLieuNode, Long> {

    Optional<VatLieuNode> findByCode(String code);

    Optional<VatLieuNode> findByName(String name);
}