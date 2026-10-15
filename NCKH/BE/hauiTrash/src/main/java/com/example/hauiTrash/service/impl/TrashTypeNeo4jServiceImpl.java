package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.TrashBinResponse;
import com.example.hauiTrash.dto.TrashTypeResponse;
import com.example.hauiTrash.entity.neo4j.TrashBinNode;
import com.example.hauiTrash.entity.neo4j.TrashTypeNode;
import com.example.hauiTrash.repository.neo4j.TrashTypeNodeRepository;
import com.example.hauiTrash.service.TrashTypeNeo4jService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrashTypeNeo4jServiceImpl
        implements TrashTypeNeo4jService {

    private final TrashTypeNodeRepository repository;

    @Override
    public TrashTypeResponse findByCode(String code) {

        TrashTypeNode type = repository.findByCode(code)
                .orElse(null);

        if (type == null) {
            return null;
        }

        return mapToResponse(type);
    }

    @Override
    public TrashTypeResponse findByName(String name) {

        TrashTypeNode type = repository.findByName(name)
                .orElse(null);

        if (type == null) {
            return null;
        }

        return mapToResponse(type);
    }

    private TrashTypeResponse mapToResponse(TrashTypeNode type) {

        TrashBinNode bin = type.getTrashBin();

        TrashBinResponse binResponse = null;

        if (bin != null) {
            binResponse = TrashBinResponse.builder()
                    .id(bin.getId())
                    .name(bin.getName())
                    .code(bin.getCode())
                    .description(bin.getDescription())
                    .isActive(bin.getIsActive())
                    .color(bin.getColor())
                    .colorCode(bin.getColorCode())
                    .build();
        }

        return TrashTypeResponse.builder()
                .id(type.getId())
                .name(type.getName())
                .code(type.getCode())
                .description(type.getDescription())
                .isActive(type.getIsActive())
                .trashBin(binResponse)
                .build();
    }
}