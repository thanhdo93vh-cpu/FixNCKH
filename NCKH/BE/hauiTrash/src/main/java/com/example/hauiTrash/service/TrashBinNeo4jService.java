package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.TrashBinResponse;
import org.springframework.stereotype.Service;

@Service
public interface TrashBinNeo4jService {

    TrashBinResponse findByCode(String code);

    TrashBinResponse findByName(String name);

}