package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.entity.Classification;
import com.example.hauiTrash.entity.Detection;
import com.example.hauiTrash.repository.ClassificationRepository;
import com.example.hauiTrash.service.ConflictDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictDetectionServiceImpl implements ConflictDetectionService {

    private final ClassificationRepository classificationRepository;

    @Override
    public boolean hasConflict(Detection detection) {
        List<Classification> classifications = classificationRepository.findByDetectionId(detection.getId());

        if (classifications == null || classifications.size() <= 1) {
            return false;
        }

        Map<String, List<Classification>> groupedByLabel = classifications.stream()
                .collect(Collectors.groupingBy(c -> c.getTrashItem().getLabel()));

        return groupedByLabel.size() >= 2;
    }

    @Override
    public ConflictLabels getConflictLabels(Detection detection) {
        List<Classification> classifications = classificationRepository.findByDetectionId(detection.getId());

        if (classifications == null || classifications.isEmpty()) {
            return null;
        }

        Map<String, Classification> bestByLabel = classifications.stream()
                .collect(Collectors.toMap(
                        c -> c.getTrashItem().getLabel(),
                        c -> c,
                        (a, b) -> a.getConfidence() > b.getConfidence() ? a : b
                ));

        List<Classification> unique = List.copyOf(bestByLabel.values());

        if (unique.size() < 2) {
            return null;
        }

        Classification first = unique.get(0);
        Classification second = unique.get(1);

        return new ConflictLabels(
                first.getTrashItem().getLabel(),
                first.getTrashItem().getLabelDisplay(),
                first.getConfidence(),
                second.getTrashItem().getLabel(),
                second.getTrashItem().getLabelDisplay(),
                second.getConfidence()
        );
    }

    @Override
    public void markForUserChoice(Detection detection) {
        if (hasConflict(detection)) {
            detection.setStatus("NEEDS_USER_CHOICE");
            log.info("Marked detection {} for user choice due to conflict", detection.getId());
        }
    }
}