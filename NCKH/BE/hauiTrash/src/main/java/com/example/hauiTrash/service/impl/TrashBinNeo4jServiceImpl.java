package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.TrashBinResponse;
import com.example.hauiTrash.entity.neo4j.TrashBinNode;
import com.example.hauiTrash.repository.neo4j.TrashBinNodeRepository;
import com.example.hauiTrash.service.TrashBinNeo4jService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrashBinNeo4jServiceImpl
        implements TrashBinNeo4jService {

    private final TrashBinNodeRepository repository;

    @Override
    public TrashBinResponse findByCode(String code) {

        TrashBinNode bin = repository.findByCode(code)
                .orElse(null);

        if (bin == null) {
            return null;
        }

        return mapToResponse(bin);
    }

    @Override
    public TrashBinResponse findByName(String name) {

        TrashBinNode bin = repository.findByName(name)
                .orElse(null);

        if (bin == null) {
            return null;
        }

        return mapToResponse(bin);
    }

    private TrashBinResponse mapToResponse(TrashBinNode bin) {

        return TrashBinResponse.builder()
                .id(bin.getId())
                .name(bin.getName())
                .code(bin.getCode())
                .description(bin.getDescription())
                .isActive(bin.getIsActive())
                .color(bin.getColor())
                .colorCode(bin.getColorCode())
                .build();
    }
}