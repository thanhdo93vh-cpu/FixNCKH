package com.example.hauiTrash.client;

import com.example.hauiTrash.config.GeminiProperties;
import com.example.hauiTrash.dto.GeminiGenerateContentRequest;
import com.example.hauiTrash.dto.GeminiGenerateContentResponse;
import com.example.hauiTrash.dto.GeminiTrashItemResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.text.Normalizer;
import java.util.Locale;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Primary
@Component
@RequiredArgsConstructor
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final WebClient geminiWebClient;
    private final GeminiProperties props;
    private final ObjectMapper om = new ObjectMapper();

    // =========================================================
    // 1) SUGGEST TRASH TYPE - SINGLE -> WRAP -> BATCH
    // =========================================================
    @Override
    public TypeSuggestion suggestTrashType(String label, String labelDisplay) {
        TypeSuggestBatchResult r = suggestTrashTypeBatch(
                List.of(new TypeSuggestItemInput(n(label), n(labelDisplay)))
        );
        if (r != null && r.getItems() != null && !r.getItems().isEmpty() && r.getItems().get(0) != null) {
            return normalizeTypeSuggestion(r.getItems().get(0));
        }
        return TypeSuggestion.builder().trashTypeCode("UNKNOWN").confidence(0f).reason("empty_result").build();
    }

    // =========================================================
    // 2) SUGGEST TRASH TYPE - BATCH (CALL GEMINI 1 LẦN) - FIX IDX
    // =========================================================
    @Data
    public static class TypeSuggestBatchInput {
        private Integer idx;
        private String label;
        private String labelDisplay;
    }

    @Data
    public static class TypeSuggestBatchResponse {
        private List<Item> items;

        @Data
        public static class Item {
            private Integer idx;
            private String trashTypeCode;
            private Float confidence;
            private String reason;
        }
    }

    @Override
    public TypeSuggestBatchResult suggestTrashTypeBatch(List<TypeSuggestItemInput> items) {
        if (items == null || items.isEmpty()) {
            TypeSuggestBatchResult empty = new TypeSuggestBatchResult();
            empty.setItems(List.of());
            return empty;
        }

        // sanitize + keep order + attach idx (KHÔNG distinct để giữ 1-1)
        List<TypeSuggestBatchInput> norm = new ArrayList<>();
        int i = 0;
        for (TypeSuggestItemInput it : items) {
            if (it == null) continue;
            String label = n(it.getLabel()).trim();
            String disp = n(it.getLabelDisplay()).trim();
            if (label.isBlank() && disp.isBlank()) continue;

            TypeSuggestBatchInput x = new TypeSuggestBatchInput();
            x.setIdx(i++);
            x.setLabel(label);
            x.setLabelDisplay(disp);
            norm.add(x);

            if (norm.size() >= 60) break;
        }

        if (norm.isEmpty()) {
            TypeSuggestBatchResult empty = new TypeSuggestBatchResult();
            empty.setItems(List.of());
            return empty;
        }

        String itemsJson;
        try { itemsJson = om.writeValueAsString(norm); }
        catch (Exception e) { itemsJson = norm.toString(); }

        String system = """
Bạn là chuyên gia phân loại rác thải tại Việt Nam.

NHIỆM VỤ:
- Với MỖI item trong danh sách, dựa vào label kỹ thuật (YOLO) và labelDisplay (tiếng Việt do người dùng nhập),
  hãy xác định loại rác phù hợp theo thực tế phân loại rác tại Việt Nam.

CHỈ trả về JSON hợp lệ. KHÔNG markdown. KHÔNG thêm chữ ngoài JSON.
CHỈ TRẢ VỀ DUY NHẤT 1 JSON OBJECT (không lặp lại JSON).

Giá trị hợp lệ của trashTypeCode:
- ORGANIC
- INORGANIC
- RECYCLE
- HAZARDOUS
- MEDICAL
- UNKNOWN

confidence ∈ [0,1]

BẮT BUỘC:
- Output phải trả đủ items tương ứng input.
- Mỗi item output PHẢI có idx giống input để map chính xác.
- Không được đổi idx, không được tạo idx mới.
- Nếu không chắc: trashTypeCode="UNKNOWN", confidence thấp.

Schema output:
{
  "items": [
    {"idx":0, "trashTypeCode":"RECYCLE", "confidence":0.0, "reason":"..."}
  ]
}
""";

        String user = """
items: %s

Trả JSON đúng schema (đủ item, đúng idx):
{
  "items": [
    {"idx":0,"trashTypeCode":"RECYCLE","confidence":0.0,"reason":"Giải thích ngắn gọn tiếng Việt"}
  ]
}
""".formatted(itemsJson);

        try {
            String json = callGeminiJson(system, user);
            TypeSuggestBatchResponse resp = om.readValue(json, TypeSuggestBatchResponse.class);

            // map by idx
            Map<Integer, TypeSuggestion> byIdx = new HashMap<>();
            if (resp != null && resp.getItems() != null) {
                for (var it : resp.getItems()) {
                    if (it == null || it.getIdx() == null) continue;

                    TypeSuggestion s = TypeSuggestion.builder()
                            .trashTypeCode(it.getTrashTypeCode())
                            .confidence(it.getConfidence())
                            .reason(it.getReason())
                            .build();

                    byIdx.put(it.getIdx(), normalizeTypeSuggestion(s));
                }
            }

            // build output đúng thứ tự norm
            List<TypeSuggestion> out = new ArrayList<>();
            for (var in : norm) {
                TypeSuggestion s = byIdx.get(in.getIdx());
                if (s == null) {
                    s = TypeSuggestion.builder()
                            .trashTypeCode("UNKNOWN").confidence(0f).reason("missing_item")
                            .build();
                }
                out.add(s);
            }

            TypeSuggestBatchResult r = new TypeSuggestBatchResult();
            r.setItems(out);
            return r;

        } catch (Exception e) {
            log.error("suggestTrashTypeBatch parse/error", e);
            TypeSuggestBatchResult fb = new TypeSuggestBatchResult();
            fb.setItems(norm.stream()
                    .map(x -> TypeSuggestion.builder().trashTypeCode("UNKNOWN").confidence(0f).reason("llm_error").build())
                    .collect(Collectors.toList()));
            return fb;
        }
    }

    private TypeSuggestion normalizeTypeSuggestion(TypeSuggestion r) {
        if (r == null) return TypeSuggestion.builder().trashTypeCode("UNKNOWN").confidence(0f).reason("null").build();
        if (r.getTrashTypeCode() == null || r.getTrashTypeCode().isBlank()) r.setTrashTypeCode("UNKNOWN");
        if (r.getConfidence() == null) r.setConfidence(0f);
        if (r.getReason() == null) r.setReason("");
        return r;
    }

    // =========================================================
    // 3) LABEL DISPLAY - SINGLE -> WRAP -> BATCH
    // =========================================================
    @Override
    public String generateLabelDisplay(String label) {
        Map<String, String> map = generateLabelDisplayBatch(List.of(label));
        String k = n(label).trim();
        return map.getOrDefault(k, k.replace('_', ' ').trim());
    }

    // =========================================================
    // 4) LABEL DISPLAY - BATCH (CALL GEMINI 1 LẦN) - FIX IDX + NO DISTINCT
    // =========================================================
    @Data
    public static class LabelDisplayBatchInput {
        private Integer idx;
        private String label;
    }

    @Data
    public static class BatchLabelDisplayResult {
        private List<Item> items;

        @Data
        public static class Item {
            private Integer idx;
            private String label;
            private String labelDisplay;
        }
    }

    @Override
    public Map<String, String> generateLabelDisplayBatch(List<String> labels) {
        if (labels == null || labels.isEmpty()) return Map.of();

        // keep order, attach idx; limit to protect token
        List<LabelDisplayBatchInput> norm = new ArrayList<>();
        int idx = 0;
        for (String lb : labels) {
            String s = n(lb).trim();
            if (s.isBlank()) continue;

            LabelDisplayBatchInput in = new LabelDisplayBatchInput();
            in.setIdx(idx++);
            in.setLabel(s);
            norm.add(in);

            if (norm.size() >= 120) break;
        }

        if (norm.isEmpty()) return Map.of();

        String labelsJson;
        try { labelsJson = om.writeValueAsString(norm); }
        catch (Exception e) { labelsJson = norm.toString(); }

String system = """
        Bạn tạo tên hiển thị (labelDisplay) BẮT BUỘC bằng TIẾNG VIỆT cho các nhãn YOLO.
        
         =====================
         LUẬT CỐT LÕI (BẮT BUỘC TUÂN THỦ)
         =====================
         - labelDisplay PHẢI là tiếng Việt 100%.
         - CẤM tuyệt đối giữ tiếng Anh dưới mọi hình thức.
         - Nếu labelDisplay còn chứa chữ cái tiếng Anh (a–z, A–Z) → OUTPUT ĐƯỢC COI LÀ SAI.
         - Chỉ xử lý đúng các label có trong input, KHÔNG thêm label mới.
        
         =====================
         QUY TẮC ĐẶT TÊN
         =====================
         - Độ dài: 2–5 từ.
         - Ngắn gọn, nghĩa phổ thông tại Việt Nam.
         - Viết hoa chữ cái đầu mỗi cụm từ (vd: "Chai nhựa", "Rác cây xanh").
         - Ưu tiên: danh từ hoặc cụm danh từ quen thuộc.
        
         =====================
         ÁNH XẠ ƯU TIÊN (PHẢI DÙNG ĐÚNG NẾU TRÙNG)
         =====================
         - plant_waste -> Rác cây xanh
         - food_waste -> Rác thực phẩm
         - organic_waste -> Rác hữu cơ
         - cigarette_butt -> Mẩu thuốc lá
         - face-mask -> Khẩu trang
         - plastic_bottle -> Chai nhựa
         - glass -> Thủy tinh
         - paper -> Giấy vụn
         - cardboard -> Bìa carton
         - battery -> Pin
         - syringe -> Kim tiêm
         - medical_waste -> Rác y tế
         - electronic -> Rác điện tử
         - diapers -> Tã/bỉm
         - styrofoam -> Xốp
         - cloth -> Vải vụn
         - soft_waste -> Rác mềm
        
         =====================
         XỬ LÝ LABEL MƠ HỒ
         =====================
         - Nếu label không nằm trong danh sách ánh xạ:
           + BẮT BUỘC dịch sang tiếng Việt theo nghĩa gần nhất.
           + TUYỆT ĐỐI không trả lại tiếng Anh.
           + Không được copy label gốc.
        
         =====================
         OUTPUT
         =====================
         - CHỈ trả về 1 JSON object hợp lệ.
         - KHÔNG markdown.
         - KHÔNG thêm bất kỳ chữ nào ngoài JSON.
        
         Schema:
         {
           "items": [
             {"idx": 0, "label": "plant_waste", "labelDisplay": "Rác cây xanh"}
           ]
         }
        
        """;

        String user = """
        items: %s

        Trả JSON đúng schema (đủ item, đúng idx).
        LƯU Ý: labelDisplay bắt buộc là tiếng Việt.
        {
            "items": [
            {"idx":0,"label":"plastic_bottle","labelDisplay":"Chai nhựa"}
  ]
        }
        """.formatted(labelsJson);
                try {
                    String json = callGeminiJson(system, user);
                    BatchLabelDisplayResult r = om.readValue(json, BatchLabelDisplayResult.class);

                    // map by idx
                    Map<Integer, BatchLabelDisplayResult.Item> byIdx = new HashMap<>();
                    if (r != null && r.getItems() != null) {
                        for (var it : r.getItems()) {
                            if (it == null || it.getIdx() == null) continue;
                            byIdx.put(it.getIdx(), it);
                        }
                    }

                    // build label->labelDisplay (ưu tiên output; fallback underscore)
                    Map<String, String> map = new HashMap<>();
                    for (var in : norm) {
                        var it = byIdx.get(in.getIdx());
                        String label = in.getLabel();
                        String display = null;

                        if (it != null) {
                            display = n(it.getLabelDisplay()).trim();
                            if (display.isBlank()) display = null;
                        }

                        if (display == null) display = label.replace('_', ' ').trim();
                        map.put(label, display);
                    }

                    return map;

                } catch (Exception e) {
                    log.error("generateLabelDisplayBatch parse/error", e);
                    Map<String, String> map = new HashMap<>();
                    for (var in : norm) map.put(in.getLabel(), in.getLabel().replace('_', ' ').trim());
                    return map;
                }
            }

            // =========================================================
            // 5) GENERATE KNOWLEDGE (FIX: extract first json + detect empty)
            // =========================================================
            @Override
            public KnowledgeGenResult generateKnowledge(String label, String labelDisplay, String trashTypeName) {
                String system = """
Bạn là chuyên gia môi trường tại Việt Nam. Viết cho người dùng phổ thông ở Việt Nam.

QUY TẮC BẮT BUỘC:
- material chỉ mô tả vật liệu của VẬT THỂ theo labelDisplay, KHÔNG viết định nghĩa chung.
- CẤM suy luận material từ trashType (trashType chỉ để gợi ý cách xử lý).
- CẤM dùng thuật ngữ kỹ thuật/ký hiệu vật liệu như: PET, HDPE, LDPE, PP, PS, PVC, ABS, polymer, polyme...
  TRỪ KHI labelDisplay có chứa đúng các ký hiệu đó.
- Nếu không chắc vật liệu: ghi theo dạng "Vật thể này là <labelDisplay>." và mô tả đơn giản.

YÊU CẦU NỘI DUNG:
- material/note/action/impact/toxicity: tối đa 2 câu.
- safeSteps: 3–6 bước, mỗi bước 8–18 từ, dạng hướng dẫn chi tiết.

CHỈ trả về JSON hợp lệ. KHÔNG markdown. KHÔNG thêm chữ ngoài JSON.
CHỈ TRẢ VỀ DUY NHẤT 1 JSON OBJECT (không lặp lại JSON).
""";

        String user = """
label: %s
labelDisplay: %s
trashType: %s

RÀNG BUỘC RIÊNG:
- material phải bắt đầu bằng: "Vật thể này chủ yếu là ..."
- material phải nhắc lại labelDisplay đúng 1 lần.
- Không được xuất hiện: PET/HDPE/LDPE/PP/polyme/polymer (trừ khi labelDisplay có).

Trả JSON đúng schema:
{
  "material": "string",
  "note": "string",
  "action": "string",
  "impact": "string",
  "toxicity": "string",
  "safeSteps": ["string"],
  "model": "%s"
}
""".formatted(n(label), n(labelDisplay), n(trashTypeName), props.getModel());

        String json = callGeminiJson(system, user);

        try {
            // Nếu model trả "{}" hoặc "" -> coi như empty
            if (json == null || json.isBlank() || "{}".equals(json.trim())) {
                throw new IllegalArgumentException("empty_json");
            }

            KnowledgeGenResult r = om.readValue(json, KnowledgeGenResult.class);

            // guard null -> fallback từng phần (không quăng hết)
            if (r.getModel() == null) r.setModel(props.getModel());
            if (r.getMaterial() == null || r.getMaterial().isBlank()) r.setMaterial("Không xác định");
            if (r.getNote() == null || r.getNote().isBlank()) r.setNote("Không đốt rác. Làm sạch sơ bộ và phân loại theo hướng dẫn địa phương.");
            if (r.getAction() == null || r.getAction().isBlank()) r.setAction("Phân loại đúng nhóm rác. Nếu có thể tái chế, hãy đưa tới điểm thu gom/tái chế.");
            if (r.getImpact() == null || r.getImpact().isBlank()) r.setImpact("Phân loại đúng giúp giảm rác chôn lấp và tiết kiệm tài nguyên xử lý.");
            if (r.getToxicity() == null || r.getToxicity().isBlank()) r.setToxicity("Tránh đốt vì có thể sinh khí độc. Nếu là pin/hoá chất, cần thu gom riêng.");
            if (r.getSafeSteps() == null || r.getSafeSteps().isEmpty()) r.setSafeSteps(fbStepsLong());

            return r;

        } catch (Exception e) {
            log.error("generateKnowledge parse fail label={} display={} raw={}", label, labelDisplay, json, e);
            return KnowledgeGenResult.builder()
                    .material("Không xác định")
                    .note("Không đốt rác. Làm sạch sơ bộ và phân loại theo hướng dẫn địa phương.")
                    .action("Phân loại đúng nhóm rác. Nếu có thể tái chế, hãy đưa tới điểm thu gom/tái chế.")
                    .impact("Phân loại đúng giúp giảm rác chôn lấp và tiết kiệm tài nguyên xử lý.")
                    .toxicity("Tránh đốt vì có thể sinh khí độc. Nếu là pin/hoá chất, cần thu gom riêng.")
                    .safeSteps(fbStepsLong())
                    .model(props.getModel())
                    .build();
        }
    }

    private List<String> fbStepsLong() {
        return List.of(
                "Làm sạch sơ bộ, loại bỏ phần bẩn hoặc chất lỏng còn sót lại",
                "Để ráo và nếu cần thì đóng gói gọn để tránh rơi vãi",
                "Bỏ đúng nhóm rác theo thùng hoặc điểm thu gom tại địa phương"
        );
    }

    // =========================================================
    // Core call Gemini generateContent (Developer API)
    // FIX: stripFence + extract FIRST valid JSON (object/array)
    // =========================================================
    private String callGeminiJson(String systemText, String userText) {
        GeminiGenerateContentRequest req = GeminiGenerateContentRequest.builder()
                .systemInstruction(GeminiGenerateContentRequest.SystemInstruction.builder()
                        .parts(List.of(GeminiGenerateContentRequest.Part.builder().text(systemText).build()))
                        .build())
                .contents(List.of(GeminiGenerateContentRequest.Content.builder()
                        .role("user")
                        .parts(List.of(GeminiGenerateContentRequest.Part.builder().text(userText).build()))
                        .build()))
                .generationConfig(GeminiGenerateContentRequest.GenerationConfig.builder()
                        .temperature(props.getTemperature())
                        .responseMimeType("application/json")
                        .build())
                .build();
        try {
            GeminiGenerateContentResponse resp = geminiWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", props.getApiKey())
                            .build(props.getModel()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(req)
                    .retrieve()
                    .bodyToMono(GeminiGenerateContentResponse.class)
                    .block(); // ❗để webclient config timeout lo

            String raw = extractFirstText(resp);
            String noFence = stripFence(raw);

            String firstJson = extractFirstJsonObject(noFence); // dùng object-only
            if (firstJson == null || firstJson.isBlank()) return "{}";
            return firstJson.trim();

        } catch (Exception e) {
            log.error("callGeminiJson failed", e);
            return "{}";
        }
    }

    private String extractFirstJsonObject(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.isEmpty()) return "{}";

        int start = t.indexOf('{');
        if (start < 0) return "{}";

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);

            if (inString) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            } else {
                if (c == '"') { inString = true; continue; }
                if (c == '{') depth++;
                if (c == '}') {
                    depth--;
                    if (depth == 0) return t.substring(start, i + 1).trim();
                }
            }
        }
        return t.substring(start).trim();
    }

    private String extractFirstText(GeminiGenerateContentResponse resp) {
        if (resp == null || resp.getCandidates() == null || resp.getCandidates().isEmpty()) return "{}";
        var c0 = resp.getCandidates().get(0);
        if (c0.getContent() == null || c0.getContent().getParts() == null || c0.getContent().getParts().isEmpty()) return "{}";
        String t = c0.getContent().getParts().get(0).getText();
        return t == null ? "{}" : t;
    }

    private String stripFence(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) t = t.substring(firstNl + 1);
            int last = t.lastIndexOf("```");
            if (last >= 0) t = t.substring(0, last);
        }
        return t.trim();
    }

    /**
     * Extract FIRST JSON value (object {} or array []) from a text.
     * Handles: extra text before/after, and "two json glued".
     */
    private String extractFirstJson(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.isEmpty()) return "{}";

        int objStart = t.indexOf('{');
        int arrStart = t.indexOf('[');

        int start;
        char open, close;

        if (objStart < 0 && arrStart < 0) return "{}";
        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            start = objStart; open = '{'; close = '}';
        } else {
            start = arrStart; open = '['; close = ']';
        }

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < t.length(); i++) {
            char c = t.charAt(i);

            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            } else {
                if (c == '"') {
                    inString = true;
                    continue;
                }
                if (c == open) depth++;
                if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return t.substring(start, i + 1).trim();
                    }
                }
            }
        }

        // không match đủ ngoặc -> trả phần còn lại (đỡ hơn rỗng)
        return t.substring(start).trim();
    }

    private String n(String s) { return s == null ? "" : s; }
    @Override
    public GeminiTrashItemResult generateTrashItem(String userInput) {
        String cleanInput = n(userInput).trim();
        if (cleanInput.isBlank()) {
            return GeminiTrashItemResult.builder()
                    .label("unknown_item")
                    .labelDisplay("Không rõ")
                    .build();
        }

        String system = """
Bạn là chuyên gia chuẩn hóa tên vật thể rác tại Việt Nam.

NHIỆM VỤ:
- Từ tên người dùng nhập bằng tiếng Việt, sinh ra:
  1. label kỹ thuật dạng snake_case, chữ thường, không dấu, không khoảng trắng
  2. labelDisplay tiếng Việt ngắn gọn, tự nhiên, dễ hiểu

QUY TẮC:
- label phải machine-friendly
- labelDisplay phải là tiếng Việt
- Không trả markdown
- Chỉ trả về 1 JSON object hợp lệ

Schema:
{
  "label": "plastic_bottle",
  "labelDisplay": "Chai nhựa"
}
""";

        String user = """
userInput: %s

Trả JSON đúng schema:
{
  "label": "plastic_bottle",
  "labelDisplay": "Chai nhựa"
}
""".formatted(cleanInput);

        try {
            String json = callGeminiJson(system, user);

            if (json == null || json.isBlank() || "{}".equals(json.trim())) {
                throw new IllegalArgumentException("empty_json");
            }

            GeminiTrashItemResult r = om.readValue(json, GeminiTrashItemResult.class);

            String finalLabel = (r != null && r.getLabel() != null && !r.getLabel().isBlank())
                    ? normalizeMachineLabel(r.getLabel())
                    : toSnakeCaseAscii(cleanInput);

            String finalDisplay = (r != null && r.getLabelDisplay() != null && !r.getLabelDisplay().isBlank())
                    ? r.getLabelDisplay().trim()
                    : cleanInput;

            return GeminiTrashItemResult.builder()
                    .label(finalLabel)
                    .labelDisplay(finalDisplay)
                    .build();

        } catch (Exception e) {
            log.error("generateTrashItem parse/error input={}", cleanInput, e);
            return GeminiTrashItemResult.builder()
                    .label(toSnakeCaseAscii(cleanInput))
                    .labelDisplay(cleanInput)
                    .build();
        }
    }
    private String normalizeMachineLabel(String input) {
        if (input == null) return "unknown_item";
        return input.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String toSnakeCaseAscii(String input) {
        if (input == null) return "unknown_item";

        String s = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "d")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return s.isBlank() ? "unknown_item" : s;
    }

    // =========================================================
    // RAG: EMBEDDING via Gemini text-embedding-004
    // =========================================================

    @Data
    public static class EmbedContentRequest {
        private String model;
        private EmbedContent content;

        @Data
        public static class EmbedContent {
            private List<EmbedPart> parts;
        }

        @Data
        public static class EmbedPart {
            private String text;
        }
    }

    @Data
    public static class EmbedContentResponse {
        private EmbeddingValue embedding;

        @Data
        public static class EmbeddingValue {
            private List<Float> values;
        }
    }

    @Override
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        String embeddingModel = props.getEmbeddingModel();

        EmbedContentRequest.EmbedPart part = new EmbedContentRequest.EmbedPart();
        part.setText(text.trim());

        EmbedContentRequest.EmbedContent content = new EmbedContentRequest.EmbedContent();
        content.setParts(List.of(part));

        EmbedContentRequest embedReq = new EmbedContentRequest();
        embedReq.setModel("models/" + embeddingModel);
        embedReq.setContent(content);

        try {
            EmbedContentResponse resp = geminiWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:embedContent")
                            .queryParam("key", props.getApiKey())
                            .build(embeddingModel))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(embedReq)
                    .retrieve()
                    .bodyToMono(EmbedContentResponse.class)
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .block();

            if (resp == null || resp.getEmbedding() == null || resp.getEmbedding().getValues() == null) {
                log.warn("embedText returned empty for: {}", text);
                return new float[0];
            }

            List<Float> values = resp.getEmbedding().getValues();
            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }

            log.info("embedText OK: text='{}' dim={}", text.length() > 50 ? text.substring(0, 50) + "..." : text, result.length);
            return result;

        } catch (Exception e) {
            log.error("embedText failed for: {}", text, e);
            return new float[0];
        }
    }

    // =========================================================
    // RAG: AUGMENTED GENERATION
    // =========================================================
    @Override
    public KnowledgeGenResult generateAugmentedKnowledge(
            String label, String labelDisplay, String trashTypeName,
            List<String> retrievedContexts
    ) {
        String contextBlock = (retrievedContexts != null && !retrievedContexts.isEmpty())
                ? String.join("\n---\n", retrievedContexts)
                : "Không có tri thức liên quan trong cơ sở dữ liệu.";

        String system = """
Bạn là chuyên gia môi trường tại Việt Nam. Viết cho người dùng phổ thông ở Việt Nam.

Bạn được cung cấp TRI THỨC ĐÃ TRUY XUẤT (Retrieved Knowledge) từ cơ sở dữ liệu.
Hãy DỰA TRÊN tri thức này để sinh ra hướng dẫn xử lý rác chính xác và phong phú.

QUY TẮC RAG (Retrieval-Augmented Generation):
1. ƯU TIÊN sử dụng thông tin từ Retrieved Knowledge.
2. NẾU Retrieved Knowledge không đủ, bổ sung từ kiến thức chung nhưng PHẢI GHI RÕ.
3. KHÔNG được bịa thông tin mâu thuẫn với Retrieved Knowledge.
4. Kết hợp nhiều nguồn tri thức nếu có, tổng hợp thành câu trả lời mạch lạc.

QUY TẮC NỘI DUNG:
- material: mô tả vật liệu của VẬT THỂ, KHÔNG dùng thuật ngữ kỹ thuật (PET, HDPE...) trừ khi labelDisplay có.
- material/note/action/impact/toxicity: tối đa 2 câu.
- safeSteps: 3–6 bước, mỗi bước 8–18 từ, dạng hướng dẫn chi tiết.

CHỈ trả về JSON hợp lệ. KHÔNG markdown. KHÔNG thêm chữ ngoài JSON.
CHỈ TRẢ VỀ DUY NHẤT 1 JSON OBJECT.
""";

        String user = """
=== THÔNG TIN VẬT THỂ ===
label: %s
labelDisplay: %s
trashType: %s

=== TRI THỨC ĐÃ TRUY XUẤT (Retrieved Knowledge) ===
%s

=== YÊU CẦU ===
Dựa trên tri thức đã truy xuất ở trên, hãy sinh hướng dẫn xử lý rác.
Nếu tri thức truy xuất phù hợp, tổng hợp và bổ sung chi tiết.
Nếu không phù hợp, dùng kiến thức chung.

Trả JSON đúng schema:
{
  "material": "string",
  "note": "string",
  "action": "string",
  "impact": "string",
  "toxicity": "string",
  "safeSteps": ["string"],
  "model": "%s"
}
""".formatted(n(label), n(labelDisplay), n(trashTypeName), contextBlock, props.getModel());

        try {
            String json = callGeminiJson(system, user);

            if (json == null || json.isBlank() || "{}".equals(json.trim())) {
                throw new IllegalArgumentException("empty_json");
            }

            KnowledgeGenResult r = om.readValue(json, KnowledgeGenResult.class);

            if (r.getModel() == null) r.setModel(props.getModel());
            if (r.getMaterial() == null || r.getMaterial().isBlank()) r.setMaterial("Không xác định");
            if (r.getNote() == null || r.getNote().isBlank()) r.setNote("Không đốt rác. Làm sạch sơ bộ và phân loại theo hướng dẫn địa phương.");
            if (r.getAction() == null || r.getAction().isBlank()) r.setAction("Phân loại đúng nhóm rác. Nếu có thể tái chế, hãy đưa tới điểm thu gom/tái chế.");
            if (r.getImpact() == null || r.getImpact().isBlank()) r.setImpact("Phân loại đúng giúp giảm rác chôn lấp và tiết kiệm tài nguyên xử lý.");
            if (r.getToxicity() == null || r.getToxicity().isBlank()) r.setToxicity("Tránh đốt vì có thể sinh khí độc. Nếu là pin/hoá chất, cần thu gom riêng.");
            if (r.getSafeSteps() == null || r.getSafeSteps().isEmpty()) r.setSafeSteps(fbStepsLong());

            log.info("RAG generateAugmentedKnowledge OK: label={} contexts={}", label, retrievedContexts != null ? retrievedContexts.size() : 0);
            return r;

        } catch (Exception e) {
            log.error("RAG generateAugmentedKnowledge fail label={}", label, e);
            // Fallback to non-RAG generation
            return generateKnowledge(label, labelDisplay, trashTypeName);
        }
    }
}
