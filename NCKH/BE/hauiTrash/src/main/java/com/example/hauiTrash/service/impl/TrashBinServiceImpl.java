package com.example.hauiTrash.service.impl;


import com.example.hauiTrash.dto.TrashBinDTO;
import com.example.hauiTrash.entity.TrashBin;
import com.example.hauiTrash.mapper.TrashBinMapper;
import com.example.hauiTrash.repository.TrashBinRepository;
import com.example.hauiTrash.service.TrashBinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrashBinServiceImpl implements TrashBinService {

    private final TrashBinRepository trashBinRepository;
    private final TrashBinMapper trashBinMapper;

    @Override
    public List<TrashBinDTO> getAllTrashBins() {
        log.info("Lấy tất cả thùng rác");
        return trashBinRepository.findAll()
                .stream()
                .map(trashBinMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<TrashBinDTO> getActiveTrashBins() {
        log.info("Lấy thùng rác đang hoạt động");
        return trashBinRepository.findAllByIsActiveTrue()
                .stream()
                .map(trashBinMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public TrashBinDTO getTrashBinById(Long id) {
        log.info("Lấy thùng rác theo ID: {}", id);
        TrashBin bin = trashBinRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thùng rác với ID: " + id));
        return trashBinMapper.toDTO(bin);
    }

    @Override
    public TrashBinDTO getTrashBinByName(String code) {
        log.info("Lấy thùng rác theo mã: {}", code);
        TrashBin bin = trashBinRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thùng rác với mã: " + code));
        return trashBinMapper.toDTO(bin);
    }

    @Override
    @Transactional
    public TrashBinDTO createTrashBin(TrashBinDTO dto) {
        log.info("Tạo mới thùng rác: {}", dto.getCode());

        if (trashBinRepository.existsByCode(dto.getCode())) {
            throw new RuntimeException("Mã thùng rác '" + dto.getCode() + "' đã tồn tại!");
        }

        TrashBin bin = trashBinMapper.toEntity(dto);
        // Set active mặc định nếu null
        if (bin.getIsActive() == null) {
            bin.setIsActive(true);
        }

        TrashBin saved = trashBinRepository.save(bin);
        log.info("Đã tạo thành công thùng rác ID: {}", saved.getId());

        return trashBinMapper.toDTO(saved);
    }

    @Override
    @Transactional
    public TrashBinDTO updateTrashBin(Long id, TrashBinDTO dto) {
        log.info("Cập nhật thùng rác ID: {}", id);

        TrashBin bin = trashBinRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thùng rác với ID: " + id));

        if (!bin.getCode().equals(dto.getCode()) && trashBinRepository.existsByCode(dto.getCode())) {
            throw new RuntimeException("Mã thùng rác '" + dto.getCode() + "' đã tồn tại!");
        }

        bin.setNameTrash(dto.getNameTrash());
        bin.setCode(dto.getCode());
        bin.setDescription(dto.getDescription());
        if (dto.getIsActive() != null) {
            bin.setIsActive(dto.getIsActive());
        }

        TrashBin updated = trashBinRepository.save(bin);
        log.info("Đã cập nhật thành công thùng rác ID: {}", id);

        return trashBinMapper.toDTO(updated);
    }

    @Override
    @Transactional
    public void deleteTrashBin(Long id) {
        log.info("Xóa thùng rác ID: {}", id);
        TrashBin bin = trashBinRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thùng rác với ID: " + id));
        trashBinRepository.delete(bin);
        log.info("Đã xóa thùng rác ID: {}", id);
    }
}