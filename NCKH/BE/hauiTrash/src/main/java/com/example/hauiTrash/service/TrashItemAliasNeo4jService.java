package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.TrashItemAliasResponse;
import com.example.hauiTrash.entity.neo4j.TrashItemAliasNode;
import org.springframework.stereotype.Service;

@Service
public interface TrashItemAliasNeo4jService {

    TrashItemAliasResponse findByAlias(String alias);
}