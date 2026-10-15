package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.TrashBinResponse;
import com.example.hauiTrash.dto.TrashItemResponse;
import com.example.hauiTrash.entity.neo4j.TrashBinNode;
import com.example.hauiTrash.entity.neo4j.TrashItemNode;
import com.example.hauiTrash.entity.neo4j.TrashTypeNode;
import com.example.hauiTrash.repository.neo4j.TrashItemNodeRepository;
import com.example.hauiTrash.service.TrashItemNeo4jService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrashItemNeo4jServiceImpl implements TrashItemNeo4jService {

    private final TrashItemNodeRepository repository;

    @Override
    public TrashItemResponse findByLabelOrAlias(String label) {

        // 1. Tìm trực tiếp bằng label
        TrashItemNode item = repository.findByLabel(label)
                .orElseGet(() ->
                        // 2. Nếu không có thì tìm bằng alias
                        repository.findByAliases_Alias(label)
                                .orElse(null)
                );

        // Không tìm thấy
        if (item == null) {
            return null;
        }

        // Lấy TrashType
        TrashTypeNode trashType = item.getTrashType();

        // Lấy TrashBin từ TrashType
        TrashBinNode trashBin =
                trashType != null ? trashType.getTrashBin() : null;

        // Map TrashBinNode -> TrashBinResponse
        TrashBinResponse trashBinResponse = null;

        if (trashBin != null) {
            trashBinResponse = TrashBinResponse.builder()
                    .id(trashBin.getId())
                    .name(trashBin.getName())
                    .code(trashBin.getCode())
                    .description(trashBin.getDescription())
                    .isActive(trashBin.getIsActive())
                    .color(trashBin.getColor())
                    .colorCode(trashBin.getColorCode())
                    .build();
        }

        // Map TrashItemNode -> TrashItemResponse
        return TrashItemResponse.builder()
                .label(item.getLabel())
                .labelDisplay(item.getLabelDisplay())
                .isActive(item.getIsActive())
                .material(item.getMaterial())
                .note(item.getNote())
                .action(item.getAction())
                .trashType(
                        trashType != null
                                ? trashType.getName()
                                : null
                )
                .trashTypeCode(
                        trashType != null
                                ? trashType.getCode()
                                : null
                )
                .trashBin(trashBinResponse)
                .build();
    }
}