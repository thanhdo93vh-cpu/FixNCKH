package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.TrashBinDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface TrashBinService {
    List<TrashBinDTO> getAllTrashBins();  // Lấy tất cả (kể cả inactive)

    List<TrashBinDTO> getActiveTrashBins();  // Chỉ lấy active

    TrashBinDTO getTrashBinById(Long id);

    TrashBinDTO getTrashBinByName(String name);

    TrashBinDTO createTrashBin(TrashBinDTO dto);

    TrashBinDTO updateTrashBin(Long id, TrashBinDTO dto);

    void deleteTrashBin(Long id);  // Xóa cứng, không liên quan đến isActive
}
