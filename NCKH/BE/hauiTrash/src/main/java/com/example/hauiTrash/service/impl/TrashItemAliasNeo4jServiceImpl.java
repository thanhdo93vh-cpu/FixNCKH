package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.TrashItemAliasResponse;
import com.example.hauiTrash.entity.neo4j.TrashItemAliasNode;
import com.example.hauiTrash.repository.neo4j.TrashItemAliasNodeRepository;
import com.example.hauiTrash.service.TrashItemAliasNeo4jService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrashItemAliasNeo4jServiceImpl
        implements TrashItemAliasNeo4jService {

    private final TrashItemAliasNodeRepository repository;

    @Override
    public TrashItemAliasResponse findByAlias(String alias) {

        TrashItemAliasNode result =
                repository.findByAlias(alias).orElse(null);

        if (result == null) {
            return null;
        }

        return TrashItemAliasResponse.builder()
                .id(result.getId())
                .alias(result.getAlias())
                .aliasLabel(result.getAliasLabel())
                .aliasDisplay(result.getAliasDisplay())
                .aliasSource(result.getAliasSource())
                .isActive(result.getIsActive())
                .build();
    }
}