package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.*;
import com.example.hauiTrash.entity.*;
import com.example.hauiTrash.repository.*;
import com.example.hauiTrash.service.QuizService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;



@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final UserQuizRepository userQuizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_PASSING_SCORE = 50;
    private static final int REQUIRED_QUIZZES = 5;

    @Override
    public QuizDTO getQuizByStoryId(Long storyId) {
        Quiz quiz = quizRepository.findByStoryId(storyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quiz cho truyện này"));

        // SỬA DÒNG NÀY
        List<Question> questions = questionRepository.findByQuizId(quiz.getId());
        int totalPoints = questions.stream().mapToInt(Question::getPoints).sum();

        List<QuestionDTO> questionDTOs = questions.stream()
                .map(this::convertToQuestionDTO)
                .collect(Collectors.toList());

        return QuizDTO.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .timeLimitMinutes(quiz.getTimeLimitMinutes())
                .passingScore(quiz.getPassingScore() != null ? quiz.getPassingScore() : DEFAULT_PASSING_SCORE)
                .totalPoints(totalPoints)
                .questions(questionDTOs)
                .build();
    }
    @Override
    @Transactional
    public QuizStartDTO startQuiz(Integer accountId, Long storyId) {
        long passedQuizzes = userQuizRepository.countPassedQuizzesByUserId(accountId.longValue());
        if (passedQuizzes >= REQUIRED_QUIZZES) {
            throw new RuntimeException(" Bạn đã hoàn thành đủ 5 bài trắc nghiệm và nhận chứng chỉ! Không cần làm thêm bài nào nữa.");
        }
        // Lấy quiz theo storyId
        Quiz quiz = quizRepository.findByStoryId(storyId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quiz cho truyện này"));
        // Kiểm tra user đã đậu bài này chưa (dùng accountId)
        if (userQuizRepository.existsByUserIdAndQuizIdAndIsPassedTrue(accountId.longValue(), quiz.getId())) {
            throw new RuntimeException("Bạn đã hoàn thành bài trắc nghiệm này rồi!");
        }

        // Xóa attempt cũ nếu có (chưa hoàn thành)
        quizAttemptRepository.findByUserIdAndQuizIdAndIsCompletedFalse(accountId.longValue(), quiz.getId())
                .ifPresent(quizAttemptRepository::delete);

        // Lấy tất cả câu hỏi của quiz
        List<Question> originalQuestions = questionRepository.findByQuizId(quiz.getId());
        if (originalQuestions.isEmpty()) {
            throw new RuntimeException("Quiz chưa có câu hỏi nào!");
        }

        // 1. RANDOM THỨ TỰ CÂU HỎI
        List<Question> shuffledQuestions = new ArrayList<>(originalQuestions);
        Collections.shuffle(shuffledQuestions);

        // Lưu thứ tự câu hỏi đã random
        List<Long> questionOrder = shuffledQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        // 2. RANDOM ĐÁP ÁN cho từng câu
        Map<Long, Map<String, String>> optionsMapping = new HashMap<>();
        List<QuestionDTO> questionDTOs = new ArrayList<>();

        for (Question q : shuffledQuestions) {
            // Random đáp án A, B, C, D
            Map<String, String> shuffledOptions = shuffleOptions(q);
            optionsMapping.put(q.getId(), shuffledOptions);

            // Tạo DTO với đáp án đã random
            questionDTOs.add(QuestionDTO.builder()
                    .id(q.getId())
                    .questionText(q.getQuestionText())
                    .optionA(shuffledOptions.get("A"))
                    .optionB(shuffledOptions.get("B"))
                    .optionC(shuffledOptions.get("C"))
                    .optionD(shuffledOptions.get("D"))
                    .points(q.getPoints())
                    .build());
        }

        // 3. LƯU ATTEMPT VÀO DATABASE
        String questionOrderJson;
        String optionsMappingJson;
        try {
            questionOrderJson = objectMapper.writeValueAsString(questionOrder);
            optionsMappingJson = objectMapper.writeValueAsString(optionsMapping);
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi xử lý dữ liệu: " + e.getMessage());
        }

        QuizAttempt attempt = QuizAttempt.builder()
                .userId(accountId.longValue())
                .quizId(quiz.getId())
                .questionOrder(questionOrderJson)
                .optionsMapping(optionsMappingJson)
                .isCompleted(false)
                .build();
        quizAttemptRepository.save(attempt);

        // 4. TÍNH TỔNG ĐIỂM
        int totalPoints = originalQuestions.stream().mapToInt(Question::getPoints).sum();

        // 5. TRẢ VỀ FE
        return QuizStartDTO.builder()
                .attemptToken(String.valueOf(attempt.getId()))
                .quiz(QuizDTO.builder()
                        .id(quiz.getId())
                        .title(quiz.getTitle())
                        .timeLimitMinutes(quiz.getTimeLimitMinutes())
                        .passingScore(quiz.getPassingScore() != null ? quiz.getPassingScore() : DEFAULT_PASSING_SCORE)
                        .totalPoints(totalPoints)
                        .questions(questionDTOs)
                        .build())
                .build();
    }

    @Override
    @Transactional
    public QuizResultDTO submitQuiz(Integer accountId, QuizSubmitDTO submitData) {
        // 1. LẤY ATTEMPT
        QuizAttempt attempt = quizAttemptRepository.findById(Long.valueOf(submitData.getAttemptToken()))
                .orElseThrow(() -> new RuntimeException("Phiên làm bài không hợp lệ!"));

        // Kiểm tra quyền
        if (!attempt.getUserId().equals(accountId.longValue())) {
            throw new RuntimeException("Bạn không có quyền nộp bài này!");
        }

        // Kiểm tra đã nộp chưa
        if (attempt.getIsCompleted()) {
            throw new RuntimeException("Bạn đã nộp bài này rồi!");
        }

        // Đánh dấu đã hoàn thành
        attempt.setIsCompleted(true);
        quizAttemptRepository.save(attempt);

        // 2. PARSE MAPPING
        Map<Long, Map<String, String>> optionsMapping;
        try {
            optionsMapping = objectMapper.readValue(attempt.getOptionsMapping(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<Long, Map<String, String>>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Lỗi xử lý dữ liệu bài làm");
        }

        // 3. LẤY DANH SÁCH CÂU HỎI
        List<Question> questions = questionRepository.findByQuizId(attempt.getQuizId());
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 4. LẤY QUIZ ĐỂ BIẾT ĐIỂM ĐẬU
        Quiz quiz = quizRepository.findById(attempt.getQuizId()).orElse(null);
        int passingScore = quiz != null && quiz.getPassingScore() != null
                ? quiz.getPassingScore() : DEFAULT_PASSING_SCORE;

        // 5. CHẤM BÀI
        int totalScore = 0;
        int totalPoints = 0;
        Map<Long, Boolean> correctAnswers = new HashMap<>();
        Map<Long, String> correctAnswersList = new HashMap<>();
        Map<Long, String> explanations = new HashMap<>();

        for (Map.Entry<Long, String> answer : submitData.getAnswers().entrySet()) {
            Long questionId = answer.getKey();
            String userAnswer = answer.getValue();

            Question question = questionMap.get(questionId);
            if (question == null) continue;

            totalPoints += question.getPoints();

            // Lấy mapping để biết user chọn đáp án gốc nào
            Map<String, String> mapping = optionsMapping.get(questionId);
            String originalAnswer = mapping.get(userAnswer);
            String correctAnswer = question.getCorrectAnswer();

            boolean isCorrect = originalAnswer != null && originalAnswer.equals(correctAnswer);

            if (isCorrect) {
                totalScore += question.getPoints();
            }

            correctAnswers.put(questionId, isCorrect);
            correctAnswersList.put(questionId, correctAnswer);
            explanations.put(questionId, question.getExplanation());
        }

        // 6. TÍNH PHẦN TRĂM
        double percentage = totalPoints > 0 ? (double) totalScore / totalPoints * 100 : 0;
        boolean isPassed = percentage >= passingScore;

        // 7. LƯU KẾT QUẢ VÀO USER_QUIZ
        String answersJson;
        String mappingJson;
        try {
            answersJson = objectMapper.writeValueAsString(submitData.getAnswers());
            mappingJson = attempt.getOptionsMapping();
        } catch (Exception e) {
            answersJson = "{}";
            mappingJson = "{}";
        }

        UserQuiz userQuiz = UserQuiz.builder()
                .userId(accountId.longValue())
                .quizId(attempt.getQuizId())
                .score(totalScore)
                .totalPoints(totalPoints)
                .percentage(percentage)
                .isPassed(isPassed)
                .answers(answersJson)
                .shuffledMapping(mappingJson)
                .build();
        userQuizRepository.save(userQuiz);

        // 8. TẠO THÔNG BÁO
        String message;
        if (isPassed) {
            message = String.format(" Chúc mừng! Bạn đạt %.1f điểm. Điểm đậu là %d.", percentage, passingScore);
        } else {
            message = String.format("Rất tiếc! Bạn đạt %.1f điểm. Cần %d điểm để đậu. Hãy thử lại nhé!", percentage, passingScore);
        }

        return QuizResultDTO.builder()
                .score(totalScore)
                .totalPoints(totalPoints)
                .percentage(percentage)
                .isPassed(isPassed)
                .message(message)
                .correctAnswers(correctAnswers)
                .correctAnswersList(correctAnswersList)
                .explanation(explanations)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompletedQuizDTO> getCompletedQuizzes(Integer accountId) {
        List<UserQuiz> userQuizzes = userQuizRepository.findByUserId(accountId);

        return userQuizzes.stream()
                .map(uq -> {
                    Quiz quiz = quizRepository.findById(uq.getQuizId()).orElse(null);
                    return CompletedQuizDTO.builder()
                            .quizId(uq.getQuizId())
                            .quizTitle(quiz != null ? quiz.getTitle() : "Không xác định")
                            .score(uq.getScore())
                            .totalPoints(uq.getTotalPoints())
                            .percentage(uq.getPercentage())
                            .isPassed(uq.getIsPassed())
                            .completedAt(uq.getCompletedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }


    private QuestionDTO convertToQuestionDTO(Question q) {
        return QuestionDTO.builder()
                .id(q.getId())
                .questionText(q.getQuestionText())
                .optionA(q.getOptionA())
                .optionB(q.getOptionB())
                .optionC(q.getOptionC())
                .optionD(q.getOptionD())
                .points(q.getPoints())
                .build();
    }

    private Map<String, String> shuffleOptions(Question question) {
        List<Map.Entry<String, String>> options = new ArrayList<>();
        options.add(Map.entry("A", question.getOptionA()));
        options.add(Map.entry("B", question.getOptionB()));
        options.add(Map.entry("C", question.getOptionC()));
        options.add(Map.entry("D", question.getOptionD()));

        Collections.shuffle(options);

        Map<String, String> shuffled = new LinkedHashMap<>();
        for (int i = 0; i < options.size(); i++) {
            char letter = (char) ('A' + i);
            shuffled.put(String.valueOf(letter), options.get(i).getValue());
        }

        return shuffled;
    }
}