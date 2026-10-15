package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.TrashItemResponse;
import org.springframework.stereotype.Service;

@Service
public interface TrashItemNeo4jService {

    TrashItemResponse findByLabelOrAlias(String label);

}