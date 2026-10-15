package com.example.hauiTrash.client;

import com.example.hauiTrash.config.GeminiProperties;
import com.example.hauiTrash.util.KnowledgeText;
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
- material: mô tả vật liệu của vật thể.
- materialDescription: mô tả ngắn gọn, dễ hiểu về vật liệu.
- note/action/impact/toxicity: nội dung phù hợp với dữ liệu được cung cấp.
- KHÔNG sinh trường safeSteps.

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
  "materialDescription": "string",
  "note": "string",
  "action": "string",
  "impact": "string",
  "toxicity": "string",
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
            if (r.getMaterialDescription() == null || r.getMaterialDescription().isBlank()) r.setMaterialDescription("Không xác định");
            if (r.getNote() == null || r.getNote().isBlank()) r.setNote("Không đốt rác. Làm sạch sơ bộ và phân loại theo hướng dẫn địa phương.");
            if (r.getAction() == null || r.getAction().isBlank()) r.setAction("Phân loại đúng nhóm rác. Nếu có thể tái chế, hãy đưa tới điểm thu gom/tái chế.");
            if (r.getImpact() == null || r.getImpact().isBlank()) r.setImpact("Phân loại đúng giúp giảm rác chôn lấp và tiết kiệm tài nguyên xử lý.");
            if (r.getToxicity() == null || r.getToxicity().isBlank()) r.setToxicity("Tránh đốt vì có thể sinh khí độc. Nếu là pin/hoá chất, cần thu gom riêng.");


            return r;

        } catch (Exception e) {
            log.error("generateKnowledge parse fail label={} display={} raw={}", label, labelDisplay, json, e);
            return KnowledgeGenResult.builder()
                    .materialDescription("Không xác định")
                    .note("Không đốt rác. Làm sạch sơ bộ và phân loại theo hướng dẫn địa phương.")
                    .action("Phân loại đúng nhóm rác. Nếu có thể tái chế, hãy đưa tới điểm thu gom/tái chế.")
                    .impact("Phân loại đúng giúp giảm rác chôn lấp và tiết kiệm tài nguyên xử lý.")
                    .toxicity("Tránh đốt vì có thể sinh khí độc. Nếu là pin/hoá chất, cần thu gom riêng.")

                    .model("FALLBACK")
                    .build();
        }
    }



    // =========================================================
    // Core call Gemini generateContent (Developer API)
    // FIX: stripFence + extract FIRST valid JSON (object/array)
    // =========================================================
    private record GeminiJsonResult(String json, String reason) {}

    private String callGeminiJson(String systemText, String userText) {
        return callGeminiJsonResult(systemText, userText).json();
    }

    private GeminiJsonResult callGeminiJsonResult(String systemText, String userText) {
        GeminiGenerateContentRequest req =
                GeminiGenerateContentRequest.builder()
                        .systemInstruction(
                                GeminiGenerateContentRequest.SystemInstruction.builder()
                                        .parts(
                                                List.of(
                                                        GeminiGenerateContentRequest.Part.builder()
                                                                .text(systemText)
                                                                .build()
                                                )
                                        )
                                        .build()
                        )
                        .contents(
                                List.of(
                                          GeminiGenerateContentRequest.Content.builder()
                                                  .role("user")
                                                  .parts(
                                                          List.of(
                                                                  GeminiGenerateContentRequest.Part.builder()
                                                                          .text(userText)
                                                                          .build()
                                                          )
                                                  )
                                                  .build()
                                )
                        )
                                 .generationConfig(
                                         GeminiGenerateContentRequest.GenerationConfig.builder()
                                                 .temperature(props.getTemperature())
                                                 .responseMimeType("application/json")
                                                 .build()
                                 )
                                .build();

        try {
            GeminiGenerateContentResponse resp = geminiWebClient.post()
                    .uri(builder -> builder.path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", props.getApiKey()).build(props.getModel()))
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .bodyValue(req).retrieve().bodyToMono(GeminiGenerateContentResponse.class)
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds())).block();
            String json = extractFirstJsonObject(stripFence(extractFirstText(resp)));
            return new GeminiJsonResult(json == null || json.isBlank() ? "{}" : json.trim(), null);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String reason = "GEMINI_" + e.getStatusCode().value();
            log.warn("Gemini failed | reason={} | model={} | no retry", reason, props.getModel());
            return new GeminiJsonResult("{}", reason);
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            String reason = cause instanceof java.util.concurrent.TimeoutException
                    ? "GEMINI_TIMEOUT" : "GEMINI_CONNECTION_OR_EXCEPTION";
            log.warn("Gemini failed | reason={} | error={} | no retry", reason, cause.getClass().getSimpleName());
            return new GeminiJsonResult("{}", reason);
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
    public KnowledgeGenResult generateAugmentedKnowledge(String label, String labelDisplay,
            String trashTypeName, List<String> retrievedContexts) {
        return generateAugmentedKnowledgeOutcome(label, labelDisplay, trashTypeName, retrievedContexts).knowledge();
    }

    @Override
    public AugmentedKnowledgeOutcome generateAugmentedKnowledgeOutcome(String label, String labelDisplay,
            String trashTypeName, List<String> retrievedContexts) {
        String contextBlock =
                (retrievedContexts != null && !retrievedContexts.isEmpty())
                        ? String.join("\n---\n", retrievedContexts)
                        : "Không có tri thức liên quan trong cơ sở dữ liệu.";

        String system = """
                You express Knowledge Graph facts clearly. KG is the source of truth.
                Never change label, material, trash type, bin, hazard, impact or toxicity from KG.
                Do not output label, trashType, bin, source or reason.
                Preserve all guidance, guidanceDescription, guidanceLuuY and guidanceSteps in action.
                You may supplement materialDescription, note or action only when KG lacks information.
                Never invent hazard or toxicity. Return JSON null for missing information.
                Return one JSON object with only these nullable string fields:
                material, materialDescription, note, action, impact, toxicity, hazard.
                Use Vietnamese. Do not emit generic missing-knowledge messages.
                """;
        String user = "Label: " + n(label) + "\nDisplay: " + n(labelDisplay)
                + "\nTrash type: " + n(trashTypeName) + "\nKG:\n" + contextBlock;

        try {

            GeminiJsonResult response = callGeminiJsonResult(system, user);
            if (response.reason() != null) return new AugmentedKnowledgeOutcome(null, response.reason());
            String json = response.json();
            if (json == null || json.isBlank()) return new AugmentedKnowledgeOutcome(null, "GEMINI_EMPTY_JSON");
            var node = om.readTree(json);
            if (node == null || !node.isObject() || node.isEmpty())
                return new AugmentedKnowledgeOutcome(null, "GEMINI_EMPTY_JSON");
            // Whitelist content fields: identifiers and provenance from Gemini are never parsed.
            var allowed = om.createObjectNode();
            for (String field : List.of("material", "materialDescription", "note", "action", "impact", "toxicity", "hazard")) {
                var value = node.get(field);
                if (value != null && !value.isNull()) {
                    if (!value.isTextual()) return new AugmentedKnowledgeOutcome(null, "GEMINI_INVALID_JSON");
                    allowed.put(field, KnowledgeText.clean(value.textValue()));
                }
            }
            KnowledgeGenResult result = om.treeToValue(allowed, KnowledgeGenResult.class);
            if (KnowledgeText.first(result.getMaterial(), result.getMaterialDescription(), result.getNote(),
                    result.getAction(), result.getImpact(), result.getToxicity(), result.getHazard()) == null)
                return new AugmentedKnowledgeOutcome(null, "GEMINI_EMPTY_JSON");
            result.setModel(props.getModel());
            return new AugmentedKnowledgeOutcome(result, null);
        } catch (Exception e) {
            log.warn("GraphRAG invalid JSON | label={} | no retry", label);
            return new AugmentedKnowledgeOutcome(null, "GEMINI_INVALID_JSON");
        }
    }
}
