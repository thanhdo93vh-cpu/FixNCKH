package com.example.hauiTrash.repository.neo4j;

import com.example.hauiTrash.entity.neo4j.HuongXuLyNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HuongXuLyNodeRepository
        extends Neo4jRepository<HuongXuLyNode, Long> {
}