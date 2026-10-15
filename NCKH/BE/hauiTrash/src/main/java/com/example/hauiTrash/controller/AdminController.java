package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.StoryDetailDTO;
import com.example.hauiTrash.dto.StoryRequest;
import com.example.hauiTrash.dto.TrashBinDTO;
import com.example.hauiTrash.entity.Question;
import com.example.hauiTrash.entity.Quiz;
import com.example.hauiTrash.repository.QuestionRepository;
import com.example.hauiTrash.repository.QuizRepository;
import com.example.hauiTrash.service.QuizService;
import com.example.hauiTrash.service.StoryService;
import com.example.hauiTrash.service.TrashBinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminController {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final StoryService storyService;
    private final QuizService quizService;
    private final TrashBinService  trashBinService;
    // ==================== STORY MANAGEMENT ====================

    @PostMapping("/stories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoryDetailDTO>> createStory(@Valid @RequestBody StoryRequest request) {
        StoryDetailDTO story = storyService.createStory(request);
        return ResponseEntity.ok(ApiResponse.<StoryDetailDTO>builder()
                .message("Tạo câu chuyện thành công")
                .data(story)
                .build());
    }

    @PutMapping("/stories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StoryDetailDTO>> updateStory(
            @PathVariable Long id,
            @Valid @RequestBody StoryRequest request) {
        StoryDetailDTO story = storyService.updateStory(id, request);
        return ResponseEntity.ok(ApiResponse.<StoryDetailDTO>builder()
                .message("Cập nhật câu chuyện thành công")
                .data(story)
                .build());
    }

    @DeleteMapping("/stories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable Long id) {
        storyService.deleteStory(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Xóa câu chuyện thành công")
                .build());
    }

    // ==================== QUIZ MANAGEMENT ====================

    // Lấy danh sách tất cả quiz
    @GetMapping("/quizzes")
    public ResponseEntity<ApiResponse<List<Quiz>>> getAllQuizzes() {
        List<Quiz> quizzes = quizRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<Quiz>>builder()
                .message("Lấy danh sách quiz thành công")
                .data(quizzes)
                .build());
    }

    // Lấy quiz theo ID
    @GetMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<Quiz>> getQuizById(@PathVariable Long id) {
        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quiz"));
        return ResponseEntity.ok(ApiResponse.<Quiz>builder()
                .message("Lấy quiz thành công")
                .data(quiz)
                .build());
    }

    // Thêm quiz mới
    @PostMapping("/quizzes")
    public ResponseEntity<ApiResponse<Quiz>> createQuiz(@RequestBody Quiz quiz) {
        // Kiểm tra storyId đã có quiz chưa
        if (quizRepository.findByStoryId(quiz.getStoryId().longValue()).isPresent()) {
            throw new RuntimeException("Story này đã có quiz rồi!");
        }

        Quiz savedQuiz = quizRepository.save(quiz);
        return ResponseEntity.ok(ApiResponse.<Quiz>builder()
                .message("Thêm quiz thành công")
                .data(savedQuiz)
                .build());
    }

    // Cập nhật quiz
    @PutMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<Quiz>> updateQuiz(
            @PathVariable Long id,
            @RequestBody Quiz quizUpdate) {

        Quiz quiz = quizRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quiz"));

        quiz.setTitle(quizUpdate.getTitle());
        quiz.setStoryId(quizUpdate.getStoryId());
        quiz.setTimeLimitMinutes(quizUpdate.getTimeLimitMinutes());
        quiz.setPassingScore(quizUpdate.getPassingScore());
        quiz.setIsActive(quizUpdate.getIsActive());

        Quiz savedQuiz = quizRepository.save(quiz);
        return ResponseEntity.ok(ApiResponse.<Quiz>builder()
                .message("Cập nhật quiz thành công")
                .data(savedQuiz)
                .build());
    }

    // Xóa quiz
    @DeleteMapping("/quizzes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuiz(@PathVariable Long id) {
        if (!quizRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy quiz");
        }
        quizRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Xóa quiz thành công")
                .data(null)
                .build());
    }

    // ==================== QUESTION MANAGEMENT ====================

    // Lấy danh sách câu hỏi của quiz
    @GetMapping("/quizzes/{quizId}/questions")
    public ResponseEntity<ApiResponse<List<Question>>> getQuestionsByQuiz(@PathVariable Long quizId) {
        List<Question> questions = questionRepository.findByQuizId(quizId);
        return ResponseEntity.ok(ApiResponse.<List<Question>>builder()
                .message("Lấy danh sách câu hỏi thành công")
                .data(questions)
                .build());
    }

    // Thêm câu hỏi vào quiz
    @PostMapping("/quizzes/{quizId}/questions")
    public ResponseEntity<ApiResponse<Question>> addQuestion(
            @PathVariable Long quizId,
            @RequestBody Question question) {

        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy quiz"));

        question.setQuizId(quizId);
        Question savedQuestion = questionRepository.save(question);

        return ResponseEntity.ok(ApiResponse.<Question>builder()
                .message("Thêm câu hỏi thành công")
                .data(savedQuestion)
                .build());
    }

    // Cập nhật câu hỏi
    @PutMapping("/questions/{id}")
    public ResponseEntity<ApiResponse<Question>> updateQuestion(
            @PathVariable Long id,
            @RequestBody Question questionUpdate) {

        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy câu hỏi"));

        question.setQuestionText(questionUpdate.getQuestionText());
        question.setOptionA(questionUpdate.getOptionA());
        question.setOptionB(questionUpdate.getOptionB());
        question.setOptionC(questionUpdate.getOptionC());
        question.setOptionD(questionUpdate.getOptionD());
        question.setCorrectAnswer(questionUpdate.getCorrectAnswer());
        question.setPoints(questionUpdate.getPoints());
        question.setExplanation(questionUpdate.getExplanation());

        Question savedQuestion = questionRepository.save(question);
        return ResponseEntity.ok(ApiResponse.<Question>builder()
                .message("Cập nhật câu hỏi thành công")
                .data(savedQuestion)
                .build());
    }

    // Xóa câu hỏi
    @DeleteMapping("/questions/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable Long id) {
        if (!questionRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy câu hỏi");
        }
        questionRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Xóa câu hỏi thành công")
                .data(null)
                .build());
    }
    // ==================== TRASH BIN MANAGEMENT ====================

    // Lấy tất cả thùng rác (kể cả inactive)
    @GetMapping("/trash-bins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TrashBinDTO>>> getAllTrashBins() {
        List<TrashBinDTO> bins = trashBinService.getAllTrashBins();
        return ResponseEntity.ok(ApiResponse.<List<TrashBinDTO>>builder()
                .message("Lấy danh sách thùng rác thành công")
                .data(bins)
                .build());
    }

    // Lấy thùng rác theo ID
    @GetMapping("/trash-bins/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TrashBinDTO>> getTrashBinById(@PathVariable Long id) {
        TrashBinDTO bin = trashBinService.getTrashBinById(id);
        return ResponseEntity.ok(ApiResponse.<TrashBinDTO>builder()
                .message("Lấy thùng rác thành công")
                .data(bin)
                .build());
    }

    // Lấy thùng rác theo mã (giay, nhua, kim_loai, thuy_tinh)
    @GetMapping("/trash-bins/code/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TrashBinDTO>> getTrashBinByName(@PathVariable String name) {
        TrashBinDTO bin = trashBinService.getTrashBinByName(name);
        return ResponseEntity.ok(ApiResponse.<TrashBinDTO>builder()
                .message("Lấy thùng rác thành công")
                .data(bin)
                .build());
    }

    // Tạo mới thùng rác
    @PostMapping("/trash-bins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TrashBinDTO>> createTrashBin(@Valid @RequestBody TrashBinDTO dto) {
        TrashBinDTO created = trashBinService.createTrashBin(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TrashBinDTO>builder()
                        .message("Tạo thùng rác thành công")
                        .data(created)
                        .build());
    }

    // Cập nhật thùng rác
    @PutMapping("/trash-bins/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TrashBinDTO>> updateTrashBin(
            @PathVariable Long id,
            @Valid @RequestBody TrashBinDTO dto) {
        TrashBinDTO updated = trashBinService.updateTrashBin(id, dto);
        return ResponseEntity.ok(ApiResponse.<TrashBinDTO>builder()
                .message("Cập nhật thùng rác thành công")
                .data(updated)
                .build());
    }

    // Xóa thùng rác
    @DeleteMapping("/trash-bins/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTrashBin(@PathVariable Long id) {
        trashBinService.deleteTrashBin(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Xóa thùng rác thành công")
                .build());
    }
}