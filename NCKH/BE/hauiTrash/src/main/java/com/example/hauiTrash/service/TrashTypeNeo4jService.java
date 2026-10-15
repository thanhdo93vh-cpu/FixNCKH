package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.TrashTypeResponse;
import com.example.hauiTrash.entity.neo4j.TrashTypeNode;
import org.springframework.stereotype.Service;

@Service
public interface TrashTypeNeo4jService {

    TrashTypeResponse findByCode(String code);
    TrashTypeResponse findByName(String name);
}