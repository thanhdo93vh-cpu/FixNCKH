package com.example.hauiTrash.mapper;

import com.example.hauiTrash.dto.TrashBinDTO;
import com.example.hauiTrash.entity.TrashBin;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TrashBinMapper {

    TrashBinMapper INSTANCE = Mappers.getMapper(TrashBinMapper.class);

    // Tạo mới - bỏ qua id
    @Mapping(target = "id", ignore = true)
    TrashBin toEntity(TrashBinDTO dto);

    // Tạo mới với default active = true
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isActive", expression = "java(dto.getIsActive() != null ? dto.getIsActive() : true)")
    TrashBin toEntityWithDefaultActive(TrashBinDTO dto);

    // ===== UPDATE =====

    // Cập nhật - bỏ qua id (quan trọng để tránh bug)
    @Mapping(target = "id", ignore = true)
    void updateEntity(TrashBinDTO dto, @MappingTarget TrashBin entity);

    // Cập nhật - chỉ update các field không null
    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityNonNull(TrashBinDTO dto, @MappingTarget TrashBin entity);



    // Entity -> DTO (có id)
    TrashBinDTO toDTO(TrashBin entity);
}