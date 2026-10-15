
package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.FeedbackQuestionDTO;
import com.example.hauiTrash.dto.OptionDTO;
import com.example.hauiTrash.entity.Classification;
import com.example.hauiTrash.entity.Detection;
import com.example.hauiTrash.entity.TrashItem;
import com.example.hauiTrash.repository.ClassificationRepository;
import com.example.hauiTrash.repository.DetectionRepository;
import com.example.hauiTrash.repository.TrashItemRepository;
import com.example.hauiTrash.service.FeedbackQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackQuestionServiceImpl implements FeedbackQuestionService {

    private final DetectionRepository detectionRepository;
    private final ClassificationRepository classificationRepository;
    private final TrashItemRepository trashItemRepository;

    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.7f;

    @Override
    @Transactional(readOnly = true)
    public FeedbackQuestionDTO generateQuestion(Integer detectionId) {
        Detection detection = detectionRepository.findById(detectionId)
                .orElseThrow(() -> new RuntimeException("Detection not found: " + detectionId));

        List<Classification> classifications = classificationRepository.findByDetectionId(detectionId);

        // Không có classification -> hỏi đúng/sai cơ bản
        if (classifications.isEmpty()) {
            return createBasicConfirmQuestion(detection);
        }

        // Lấy các label khác nhau
        Map<String, List<Classification>> groupedByLabel = classifications.stream()
                .collect(Collectors.groupingBy(c -> c.getTrashItem().getLabel()));

        // TH1: Chỉ có 1 label
        if (groupedByLabel.size() == 1) {
            String label = groupedByLabel.keySet().iterator().next();
            List<Classification> sameLabel = groupedByLabel.get(label);

            float maxConfidence = (float) sameLabel.stream()
                    .mapToDouble(Classification::getConfidence)
                    .max()
                    .orElse(0.0f);

            return createSingleConfirmQuestion(detection, label, maxConfidence);
        }

        // TH2: Có 2 label khác nhau (l1 ≠ l2)
        List<Map.Entry<String, List<Classification>>> sorted = new ArrayList<>(groupedByLabel.entrySet());
        sorted.sort((a, b) -> {
            float confA = (float) a.getValue().stream().mapToDouble(Classification::getConfidence).max().orElse(0);
            float confB = (float) b.getValue().stream().mapToDouble(Classification::getConfidence).max().orElse(0);
            return Float.compare(confB, confA);
        });

        String label1 = sorted.get(0).getKey();
        String label2 = sorted.get(1).getKey();

        float conf1 = (float) sorted.get(0).getValue().stream().mapToDouble(Classification::getConfidence).max().orElse(0);
        float conf2 = (float) sorted.get(1).getValue().stream().mapToDouble(Classification::getConfidence).max().orElse(0);

        return createMultipleChoiceQuestion(detection, label1, conf1, label2, conf2);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackQuestionDTO> generateQuestionsForAiRequest(Integer aiRequestId) {
        List<Detection> detections = detectionRepository.findByAiRequest_Id(aiRequestId);

        List<FeedbackQuestionDTO> questions = new ArrayList<>();
        for (Detection detection : detections) {
            questions.add(generateQuestion(detection.getId()));
        }
        return questions;
    }

    // ==================== PRIVATE METHODS ====================

    private FeedbackQuestionDTO createBasicConfirmQuestion(Detection detection) {
        return FeedbackQuestionDTO.builder()
                .detectionId(detection.getId())
                .cropImageUrl(detection.getCropUrl())
                .originalLabel(detection.getLabel())
                .originalConfidence(detection.getConfidence())
                .questionType("SINGLE_CONFIRM")
                .instruction("Kết quả này có đúng với rác trong ảnh không?")
                .options(List.of(
                        OptionDTO.builder().code("YES").label("Đúng").build(),
                        OptionDTO.builder().code("NO").label("Sai").build()
                ))
                .build();
    }

    private FeedbackQuestionDTO createSingleConfirmQuestion(Detection detection, String label, Float confidence) {
        String labelDisplay = getLabelDisplay(label);

        String instruction;
        if (confidence >= HIGH_CONFIDENCE_THRESHOLD) {
            instruction = String.format("AI phát hiện: %s (%.0f%%)\nKết quả này có đúng không?",
                    labelDisplay, confidence * 100);
        } else {
            instruction = String.format("AI không chắc chắn lắm...\nCó thể là: %s (%.0f%%)\nBạn thấy đây là rác gì?",
                    labelDisplay, confidence * 100);
        }

        return FeedbackQuestionDTO.builder()
                .detectionId(detection.getId())
                .cropImageUrl(detection.getCropUrl())
                .originalLabel(label)
                .originalConfidence(confidence)
                .questionType("SINGLE_CONFIRM")
                .instruction(instruction)
                .options(List.of(
                        OptionDTO.builder().code("YES").label("Đúng").confidence(confidence).build(),
                        OptionDTO.builder().code("NO").label("Sai, bỏ qua").build(),
                        OptionDTO.builder().code("CUSTOM").label("Khác (tự nhập)").build()
                ))
                .build();
    }

    private FeedbackQuestionDTO createMultipleChoiceQuestion(Detection detection,
                                                             String label1, Float conf1,
                                                             String label2, Float conf2) {
        String labelDisplay1 = getLabelDisplay(label1);
        String labelDisplay2 = getLabelDisplay(label2);

        String instruction = String.format(
                "AI phân vân giữa 2 loại rác:\n\n" +
                        "■ A. %s (%.0f%%)\n" +
                        "■ B. %s (%.0f%%)\n" +
                        "■ C. Khác (tự nhập)\n\n" +
                        "Bạn chọn đáp án đúng nhất?",
                labelDisplay1, conf1 * 100,
                labelDisplay2, conf2 * 100
        );

        return FeedbackQuestionDTO.builder()
                .detectionId(detection.getId())
                .cropImageUrl(detection.getCropUrl())
                .originalLabel(detection.getLabel())
                .originalConfidence(detection.getConfidence())
                .questionType("MULTIPLE_CHOICE")
                .instruction(instruction)
                .options(List.of(
                        OptionDTO.builder().code("A").label(labelDisplay1).confidence(conf1).build(),
                        OptionDTO.builder().code("B").label(labelDisplay2).confidence(conf2).build(),
                        OptionDTO.builder().code("CUSTOM").label("Khác (tự nhập)").build()
                ))
                .build();
    }

    private String getLabelDisplay(String label) {
        Optional<TrashItem> item = trashItemRepository.findByLabel(label);
        if (item.isPresent() && item.get().getLabelDisplay() != null) {
            return item.get().getLabelDisplay();
        }
        // Fallback: thay underscore bằng space và viết hoa chữ cái đầu
        String[] words = label.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}