package com.example.hauiTrash.evaluation;

import com.example.hauiTrash.client.LlmClient;
import com.example.hauiTrash.dto.VisualRagResult;
import com.example.hauiTrash.entity.TrashItemKnowledge;
import com.example.hauiTrash.entity.TrashItemMapping;
import com.example.hauiTrash.repository.TrashItemKnowledgeRepository;
import com.example.hauiTrash.repository.TrashItemMappingRepository;
import com.example.hauiTrash.service.RagService;
import com.example.hauiTrash.service.TrashKnowledgeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Evaluation-only, resumable RQ2 Vector RAG runner. */
@Component
@Profile("vector-rag-test")
@RequiredArgsConstructor
public class VectorRagEvaluationRunner implements CommandLineRunner {
    private static final String BUILD_PHASE = "BUILD_AND_FREEZE_CORPUS";
    private static final String BENCHMARK_PHASE = "RUN_BENCHMARK";
    private static final String VALIDATE_PHASE = "VALIDATE_CANDIDATES_READ_ONLY";
    private static final String PROTOCOL = "RQ2_VECTOR_RAG_FULL_94_V1";
    private static final float THRESHOLD = 0.65f;
    private static final int TOP_K = 3;
    private static final int EXPECTED_CORPUS_SIZE = 94;
    private static final List<String> FIXED_LABELS = List.of(
            "corn", "face_mask", "glass", "metal", "paper",
            "pen", "pin", "plastic", "potato", "tomato");

    private final TrashItemMappingRepository mappingRepo;
    private final TrashItemKnowledgeRepository knowledgeRepo;
    private final TrashKnowledgeService knowledgeService;
    private final RagService ragService;
    private final LlmClient llmClient;
    private final ObjectMapper om;
    private final PlatformTransactionManager transactionManager;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(String... args) throws Exception {
        try {
            String phase = environment.getProperty("vector-rag.phase", "").trim();
            if (BUILD_PHASE.equalsIgnoreCase(phase)) {
                buildAndFreezeCorpus();
            } else if (BENCHMARK_PHASE.equalsIgnoreCase(phase)) {
                runBenchmark();
            } else if (VALIDATE_PHASE.equalsIgnoreCase(phase)) {
                validateCandidatesReadOnly();
            } else {
                throw new IllegalArgumentException("Set --vector-rag.phase=" + BUILD_PHASE + ", " + BENCHMARK_PHASE + " or " + VALIDATE_PHASE);
            }
        } finally {
            context.close();
        }
    }

    private void buildAndFreezeCorpus() throws Exception {
        String stamp = timestamp();
        Path blocked = outputDir().resolve("vector_rag_corpus_blocked_" + stamp + ".json");
        List<CandidateSnapshot> candidates = candidates();
        require(candidates.size() == EXPECTED_CORPUS_SIZE,
                "CANDIDATE_COUNT_MISMATCH expected=94 actual=" + candidates.size());

        int reusedKnowledge = 0;
        int createdKnowledge = 0;
        for (CandidateSnapshot candidate : candidates) {
            int itemId = candidate.itemId();
            String label = candidate.label();
            KnowledgeSnapshot before = loadActiveKnowledge(itemId);
            if (isValidReal(before)) {
                reusedKnowledge++;
                System.out.printf("CORPUS_KNOWLEDGE_REUSE item=%d label=%s row=%d%n", itemId, label, before.knowledgeId());
                continue;
            }
            if (before != null) {
                writeBlocker(blocked, "KNOWLEDGE", label, itemId, before,
                        "PREEXISTING_ACTIVE_NON_REAL_ROW_REQUIRES_MANUAL_REVIEW", candidates);
                return;
            }

            knowledgeService.generateKnowledge(itemId, false);
            KnowledgeSnapshot created = loadActiveKnowledge(itemId);
            if (!isValidReal(created)) {
                if (created != null && isFallback(created)) {
                    // Safe: no active row existed immediately before this call, so this row belongs to this attempt.
                    deleteKnowledgeCreatedByCurrentAttempt(created.knowledgeId());
                }
                writeBlocker(blocked, "KNOWLEDGE", label, itemId, created,
                        externalKnowledgeFailure(created), candidates);
                return;
            }
            createdKnowledge++;
            System.out.printf("CORPUS_KNOWLEDGE_CREATED item=%d label=%s row=%d%n", itemId, label, created.knowledgeId());
        }

        List<KnowledgeSnapshot> corpus = corpusRows(candidates);
        require(corpus.size() == EXPECTED_CORPUS_SIZE && corpus.stream().allMatch(this::isValidReal),
                "KNOWLEDGE_CORPUS_INCOMPLETE");
        System.out.println("KNOWLEDGE_CORPUS_COMPLETE=94/94");

        int reusedEmbeddings = 0;
        int createdEmbeddings = 0;
        for (KnowledgeSnapshot knowledge : corpus) {
            if (validEmbedding(knowledge.embedding())) {
                reusedEmbeddings++;
                System.out.printf("CORPUS_EMBEDDING_REUSE row=%d item=%d%n", knowledge.knowledgeId(), knowledge.itemId());
                continue;
            }
            CandidateSnapshot mapping = candidateFor(candidates, knowledge.itemId());
            float[] embedding;
            try {
                embedding = llmClient.embedText(toProductionEmbeddingText(knowledge, mapping));
            } catch (Exception e) {
                writeBlocker(blocked, "EMBEDDING", knowledge.label(),
                        knowledge.itemId(), knowledge, "EMBEDDING_EXTERNAL_FAILURE: " + safeMessage(e), candidates);
                return;
            }
            if (embedding == null || embedding.length == 0) {
                writeBlocker(blocked, "EMBEDDING", knowledge.label(),
                        knowledge.itemId(), knowledge, "EMBEDDING_EMPTY_OR_EXTERNAL_FAILURE", candidates);
                return;
            }
            saveEmbedding(knowledge.knowledgeId(), embedding);
            createdEmbeddings++;
            System.out.printf("CORPUS_EMBEDDING_CREATED row=%d item=%d dimension=%d%n",
                    knowledge.knowledgeId(), knowledge.itemId(), embedding.length);
        }

        corpus = corpusRows(candidates);
        validateFrozenCorpus(corpus);
        ragService.loadEmbeddingsToCache();
        Set<Integer> expectedKnowledgeIds = corpus.stream().map(KnowledgeSnapshot::knowledgeId).collect(java.util.stream.Collectors.toSet());
        Set<Integer> cachedKnowledgeIds = ragCacheKnowledgeIds();
        int cacheCount = cachedKnowledgeIds.size();
        require(cacheCount == EXPECTED_CORPUS_SIZE, "CACHE_COUNT_MISMATCH expected=94 actual=" + cacheCount);
        require(cachedKnowledgeIds.equals(expectedKnowledgeIds),
                "CACHE_CORPUS_MISMATCH extra=" + difference(cachedKnowledgeIds, expectedKnowledgeIds)
                        + " missing=" + difference(expectedKnowledgeIds, cachedKnowledgeIds));

        Path manifest = outputDir().resolve("vector_rag_corpus_manifest_" + stamp + ".json");
        refuseOverwrite(manifest);
        Map<String, Object> manifestData = manifest(corpus, cacheCount, stamp,
                reusedKnowledge, createdKnowledge, reusedEmbeddings, createdEmbeddings);
        om.writerWithDefaultPrettyPrinter().writeValue(manifest.toFile(), manifestData);
        System.out.println("CORPUS_MANIFEST=" + manifest);
        System.out.println("CORPUS_MANIFEST_SHA256=" + sha256(manifest));
        System.out.println("CORPUS_READY_FOR_BENCHMARK");
    }

    private void runBenchmark() throws Exception {
        String manifestValue = environment.getProperty("vector-rag.manifest", "").trim();
        require(!manifestValue.isBlank(), "RUN_BENCHMARK_REQUIRES_EXPLICIT_FROZEN_MANIFEST");
        Path manifestPath = Path.of(manifestValue).toAbsolutePath().normalize();
        require(Files.isRegularFile(manifestPath), "MANIFEST_NOT_FOUND " + manifestPath);
        Map<String, Object> frozen = om.readValue(manifestPath.toFile(), new TypeReference<>() {});
        require(PROTOCOL.equals(frozen.get("protocol")), "MANIFEST_PROTOCOL_MISMATCH");

        List<CandidateSnapshot> candidates = candidates();
        List<KnowledgeSnapshot> corpus = corpusRows(candidates);
        validateFrozenCorpus(corpus);
        validateManifestMatchesDatabase(frozen, corpus);
        ragService.loadEmbeddingsToCache();
        Set<Integer> expectedKnowledgeIds = corpus.stream().map(KnowledgeSnapshot::knowledgeId).collect(java.util.stream.Collectors.toSet());
        Set<Integer> cachedKnowledgeIds = ragCacheKnowledgeIds();
        require(cachedKnowledgeIds.size() == EXPECTED_CORPUS_SIZE, "CACHE_NOT_FROZEN_CORPUS_SIZE");
        require(cachedKnowledgeIds.equals(expectedKnowledgeIds),
                "CACHE_NOT_EXACT_FROZEN_CORPUS extra=" + difference(cachedKnowledgeIds, expectedKnowledgeIds)
                        + " missing=" + difference(expectedKnowledgeIds, cachedKnowledgeIds));

        Path goldPath = outputDir().resolve("graphrag_gold.json");
        List<Map<String, Object>> gold = om.readValue(goldPath.toFile(), new TypeReference<>() {});
        List<String> labels = gold.stream().map(g -> String.valueOf(g.get("input_label"))).toList();
        require(labels.equals(FIXED_LABELS) && new HashSet<>(labels).size() == 10, "INVALID_FIXED_BENCHMARK " + labels);

        String stamp = timestamp();
        Path evaluation = outputDir().resolve("vector_rag_evaluation_" + stamp + ".json");
        Path perCaseCsv = outputDir().resolve("vector_rag_per_case_" + stamp + ".csv");
        Path summaryCsv = outputDir().resolve("vector_rag_summary_" + stamp + ".csv");
        Path executionLog = outputDir().resolve("vector_rag_execution_" + stamp + ".log");
        for (Path p : List.of(evaluation, perCaseCsv, summaryCsv, executionLog)) refuseOverwrite(p);
        Files.createFile(executionLog);

        List<Map<String, Object>> cases = new ArrayList<>();
        List<Double> totalLatencies = new ArrayList<>();
        for (int i = 0; i < gold.size(); i++) {
            Map<String, Object> expected = gold.get(i);
            String label = String.valueOf(expected.get("input_label"));
            long started = System.nanoTime();
            BenchmarkInvocation invocation = invokeProductionAndSnapshot(label);
            double totalMs = (System.nanoTime() - started) / 1_000_000d;
            totalLatencies.add(totalMs);
            Map<String, Object> row = caseRow(i, label, expected, invocation.result(), invocation.failure(), totalMs);
            cases.add(row);
            Files.writeString(executionLog, om.writeValueAsString(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
        validateCompletedCases(cases);
        Map<String, Object> summary = benchmarkSummary(stamp, manifestPath, goldPath, cases, totalLatencies);
        summary.put("cases", cases);
        om.writerWithDefaultPrettyPrinter().writeValue(evaluation.toFile(), summary);
        writePerCaseCsv(perCaseCsv, cases);
        writeSummaryCsv(summaryCsv, summary);
        System.out.println("VECTOR_RAG_EVALUATION=" + evaluation);
        System.out.println("VECTOR_RAG_EVALUATION_SHA256=" + sha256(evaluation));
    }

    private List<CandidateSnapshot> candidates() {
        TransactionTemplate tx = readOnlyTransaction();
        List<CandidateSnapshot> result = tx.execute(status -> mappingRepo.findAll().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsActive()) && m.getTrashType() != null)
                .map(m -> new CandidateSnapshot(
                        m.getTrashItem().getId(),
                        m.getTrashItem().getLabel(),
                        m.getTrashItem().getLabelDisplay(),
                        m.getTrashType().getId(),
                        m.getTrashType().getName()))
                .sorted(Comparator.comparing(CandidateSnapshot::itemId))
                .toList());
        return result == null ? List.of() : result;
    }

    private CandidateSnapshot candidateFor(List<CandidateSnapshot> candidates, int itemId) {
        return candidates.stream().filter(m -> m.itemId() == itemId).findFirst()
                .orElseThrow(() -> new IllegalStateException("MISSING_CANDIDATE_MAPPING item=" + itemId));
    }

    private List<KnowledgeSnapshot> corpusRows(List<CandidateSnapshot> candidates) {
        List<KnowledgeSnapshot> rows = new ArrayList<>();
        for (CandidateSnapshot candidate : candidates) {
            KnowledgeSnapshot knowledge = loadActiveKnowledge(candidate.itemId());
            if (knowledge != null) rows.add(knowledge);
        }
        rows.sort(Comparator.comparing(KnowledgeSnapshot::itemId));
        return rows;
    }

    private KnowledgeSnapshot loadActiveKnowledge(int itemId) {
        return readOnlyTransaction().execute(status -> knowledgeRepo.findActiveByTrashItemId(itemId)
                .map(this::snapshotKnowledgeInsideTransaction).orElse(null));
    }

    private KnowledgeSnapshot snapshotKnowledgeInsideTransaction(TrashItemKnowledge k) {
        return new KnowledgeSnapshot(k.getId(), k.getVersion(), k.getTrashItem().getId(),
                k.getTrashItem().getLabel(), k.getTrashItem().getLabelDisplay(), k.getSource(), k.getModel(),
                k.getMaterial(), k.getMaterialDescription(), k.getNote(), k.getAction(), k.getImpact(),
                k.getToxicity(), k.getIsActive(), k.getEmbeddingJson() == null ? null : List.copyOf(k.getEmbeddingJson()));
    }

    private void deleteKnowledgeCreatedByCurrentAttempt(int knowledgeId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TrashItemKnowledge row = knowledgeRepo.findById(knowledgeId)
                    .orElseThrow(() -> new IllegalStateException("CURRENT_ATTEMPT_ROW_MISSING id=" + knowledgeId));
            require(isFallback(row.getSource(), row.getModel()), "REFUSE_TO_DELETE_NON_FALLBACK id=" + knowledgeId);
            knowledgeRepo.delete(row);
            knowledgeRepo.flush();
        });
    }

    private void saveEmbedding(int knowledgeId, float[] embedding) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TrashItemKnowledge row = knowledgeRepo.findById(knowledgeId)
                    .orElseThrow(() -> new IllegalStateException("KNOWLEDGE_NOT_FOUND id=" + knowledgeId));
            require(!validEmbedding(row.getEmbeddingJson()), "REFUSE_TO_OVERWRITE_EXISTING_EMBEDDING id=" + knowledgeId);
            row.setEmbeddingJson(toFloatList(embedding));
            knowledgeRepo.saveAndFlush(row);
        });
    }

    private boolean isFallback(KnowledgeSnapshot k) {
        return k != null && isFallback(k.source(), k.model());
    }

    private boolean isFallback(String source, String model) {
        return "FALLBACK".equalsIgnoreCase(source) || "FALLBACK".equalsIgnoreCase(model);
    }

    private boolean isValidReal(KnowledgeSnapshot k) {
        return k != null && Boolean.TRUE.equals(k.active()) && "AI".equalsIgnoreCase(k.source())
                && !isFallback(k) && !blank(k.model()) && !blank(k.material())
                && !blank(k.materialDescription()) && !blank(k.note()) && !blank(k.action())
                && !blank(k.impact()) && !blank(k.toxicity());
    }

    private boolean validEmbedding(List<Float> embedding) {
        return embedding != null && !embedding.isEmpty() && embedding.stream().allMatch(Objects::nonNull);
    }

    private void validateFrozenCorpus(List<KnowledgeSnapshot> corpus) {
        require(corpus.size() == EXPECTED_CORPUS_SIZE, "REAL_KNOWLEDGE_COUNT_MISMATCH actual=" + corpus.size());
        require(corpus.stream().allMatch(this::isValidReal), "NON_REAL_KNOWLEDGE_IN_CORPUS");
        require(corpus.stream().allMatch(k -> validEmbedding(k.embedding())), "MISSING_OR_INVALID_EMBEDDING");
        Set<Integer> dimensions = new HashSet<>();
        corpus.forEach(k -> dimensions.add(k.embedding().size()));
        require(dimensions.size() == 1, "INCONSISTENT_EMBEDDING_DIMENSIONS " + dimensions);
    }

    private String toProductionEmbeddingText(KnowledgeSnapshot k, CandidateSnapshot mapping) {
        StringBuilder sb = new StringBuilder();
        if (k.label() != null) sb.append("Label: ").append(k.label()).append(". ");
        if (k.labelDisplay() != null) sb.append("Tên: ").append(k.labelDisplay()).append(". ");
        if (k.material() != null) sb.append("Vật liệu: ").append(k.material()).append(". ");
        if (k.materialDescription() != null) sb.append("Vật liệu chi tiết: ").append(k.materialDescription()).append(". ");
        if (k.note() != null) sb.append("Ghi chú: ").append(k.note()).append(". ");
        if (k.action() != null) sb.append("Cách xử lý: ").append(k.action()).append(". ");
        if (mapping.trashTypeName() != null) sb.append("Loại rác: ").append(mapping.trashTypeName()).append(".");
        return sb.toString().trim();
    }

    private Map<String, Object> manifest(List<KnowledgeSnapshot> corpus, int cacheCount, String stamp,
                                         int reusedKnowledge, int createdKnowledge,
                                         int reusedEmbeddings, int createdEmbeddings) throws Exception {
        List<Map<String, Object>> documents = new ArrayList<>();
        for (KnowledgeSnapshot k : corpus) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("knowledge_id", k.knowledgeId()); d.put("knowledge_version", k.version());
            d.put("trash_item_id", k.itemId()); d.put("label", k.label());
            d.put("label_display", k.labelDisplay()); d.put("source", k.source());
            d.put("model", k.model()); d.put("content_hash", contentHash(k));
            d.put("embedding_present", validEmbedding(k.embedding()));
            d.put("embedding_dimension", k.embedding().size());
            documents.add(d);
        }
        String corpusHash = sha256Bytes(om.writeValueAsBytes(documents));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol", PROTOCOL); result.put("candidate_count", EXPECTED_CORPUS_SIZE);
        result.put("knowledge_count", corpus.size()); result.put("embedding_count", corpus.size());
        result.put("cache_count", cacheCount); result.put("timestamp_utc", stamp);
        result.put("corpus_hash", corpusHash); result.put("knowledge_reused", reusedKnowledge);
        result.put("knowledge_created", createdKnowledge); result.put("embeddings_reused", reusedEmbeddings);
        result.put("embeddings_created", createdEmbeddings); result.put("documents", documents);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void validateManifestMatchesDatabase(Map<String, Object> manifest, List<KnowledgeSnapshot> corpus) throws Exception {
        require(((Number) manifest.get("candidate_count")).intValue() == EXPECTED_CORPUS_SIZE, "MANIFEST_CANDIDATE_COUNT_INVALID");
        List<Map<String, Object>> docs = (List<Map<String, Object>>) manifest.get("documents");
        require(docs != null && docs.size() == EXPECTED_CORPUS_SIZE, "MANIFEST_DOCUMENT_COUNT_INVALID");
        Map<Integer, KnowledgeSnapshot> byId = new HashMap<>();
        corpus.forEach(k -> byId.put(k.knowledgeId(), k));
        for (Map<String, Object> doc : docs) {
            int id = ((Number) doc.get("knowledge_id")).intValue();
            KnowledgeSnapshot current = byId.get(id);
            require(current != null, "FROZEN_KNOWLEDGE_MISSING id=" + id);
            require(Objects.equals(doc.get("content_hash"), contentHash(current)), "FROZEN_CONTENT_CHANGED id=" + id);
            require(((Number) doc.get("embedding_dimension")).intValue() == current.embedding().size(),
                    "FROZEN_EMBEDDING_DIMENSION_CHANGED id=" + id);
        }
        String currentHash = sha256Bytes(om.writeValueAsBytes(docs));
        require(Objects.equals(manifest.get("corpus_hash"), currentHash), "MANIFEST_CORPUS_HASH_INVALID");
    }

    private void writeBlocker(Path path, String phase, String label, int itemId, KnowledgeSnapshot k,
                              String reason, List<CandidateSnapshot> candidates) throws Exception {
        refuseOverwrite(path);
        long real = corpusRows(candidates).stream().filter(this::isValidReal).count();
        long embedded = corpusRows(candidates).stream().filter(x -> validEmbedding(x.embedding())).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phase", phase); data.put("failure_label", label); data.put("trash_item_id", itemId);
        data.put("knowledge_id", k == null ? null : k.knowledgeId()); data.put("source", k == null ? null : k.source());
        data.put("model", k == null ? null : k.model()); data.put("reason", reason);
        data.put("real_knowledge_count", real); data.put("missing_knowledge_count", EXPECTED_CORPUS_SIZE - real);
        data.put("embedding_count", embedded); data.put("benchmark_started", false); data.put("timestamp_utc", timestamp());
        om.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
        System.err.println("CORPUS_BLOCKED=" + path);
    }

    private BenchmarkInvocation invokeProductionAndSnapshot(String label) {
        BenchmarkInvocation invocation = new TransactionTemplate(transactionManager).execute(status -> {
            try {
                VisualRagResult r = ragService.retrieve(label, label, null, true);
                if (r == null) return new BenchmarkInvocation(null, null);
                KnowledgeSnapshot retrieved = r.getKnowledge() == null ? null : snapshotKnowledgeInsideTransaction(r.getKnowledge());
                KnowledgeSnapshot generated = r.getAugmentedKnowledge() == null ? null : transientKnowledgeSnapshot(r.getAugmentedKnowledge());
                String predictedType = r.getTrashType() == null ? null : r.getTrashType().getCode();
                String predictedBin = r.getTrashItem() == null || r.getTrashItem().getTrashBin() == null
                        ? null : r.getTrashItem().getTrashBin().getCode();
                String retrievedLabel = r.getTrashItem() == null ? null : r.getTrashItem().getLabel();
                return new BenchmarkInvocation(new ResultSnapshot(r.getRagSource(), r.getRagSimilarityScore(),
                        retrieved, generated, retrievedLabel, predictedType, predictedBin), null);
            } catch (Exception e) {
                return new BenchmarkInvocation(null, e.getClass().getSimpleName() + ": " + safeMessage(e));
            }
        });
        return invocation == null ? new BenchmarkInvocation(null, "TRANSACTION_RETURNED_NULL") : invocation;
    }

    private KnowledgeSnapshot transientKnowledgeSnapshot(TrashItemKnowledge k) {
        return new KnowledgeSnapshot(k.getId(), k.getVersion(), null, null, null, k.getSource(), k.getModel(),
                k.getMaterial(), k.getMaterialDescription(), k.getNote(), k.getAction(), k.getImpact(),
                k.getToxicity(), k.getIsActive(), null);
    }

    private Map<String, Object> caseRow(int index, String label, Map<String, Object> expected,
                                        ResultSnapshot r, String thrownFailure, double totalMs) {
        boolean semantic = r != null && "RAG_SEMANTIC".equals(r.ragSource());
        boolean fallback = r != null && "FALLBACK_DB".equals(r.ragSource());
        boolean generated = r != null && r.generated() != null;
        String predictedType = r == null ? null : r.predictedType();
        String predictedBin = r == null ? null : r.predictedBin();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("case_id", String.format("RQ2_%02d_%s", index + 1, label)); row.put("input_label", label);
        row.put("query_embedding_success", semantic ? true : null); row.put("retrieval_mode", r == null ? null : r.ragSource());
        row.put("semantic_retrieval_success", semantic); row.put("exact_fallback_used", fallback);
        row.put("retrieved_knowledge_ids", r == null || r.retrieved() == null ? null : List.of(r.retrieved().knowledgeId()));
        row.put("retrieved_labels", r == null || r.retrievedLabel() == null ? null : List.of(r.retrievedLabel()));
        row.put("similarity_scores", r == null || r.score() == null ? null : List.of(r.score()));
        row.put("top_k", TOP_K); row.put("threshold", THRESHOLD);
        row.put("raw_retrieved_context", knowledgeMap(r == null ? null : r.retrieved()));
        row.put("raw_generated_response", knowledgeMap(r == null ? null : r.generated()));
        row.put("generation_success", generated);
        row.put("failure_reason", thrownFailure != null ? thrownFailure : generated ? null : "GENERATION_NOT_AVAILABLE");
        row.put("predicted_type", predictedType); row.put("expected_type", expected.get("expected_trash_type"));
        row.put("type_correct", predictedType == null ? null : predictedType.equals(String.valueOf(expected.get("expected_trash_type"))));
        row.put("predicted_bin", predictedBin); row.put("expected_bin", expected.get("expected_bin"));
        row.put("bin_correct", predictedBin == null ? null : predictedBin.equals(String.valueOf(expected.get("expected_bin"))));
        row.put("retrieval_latency_ms", null); row.put("generation_latency_ms", null); row.put("total_latency_ms", totalMs);
        return row;
    }

    private Map<String, Object> benchmarkSummary(String stamp, Path manifest, Path gold,
                                                  List<Map<String, Object>> cases, List<Double> latencies) throws Exception {
        long retrieval = cases.stream().filter(x -> Boolean.TRUE.equals(x.get("semantic_retrieval_success"))).count();
        long generation = cases.stream().filter(x -> Boolean.TRUE.equals(x.get("generation_success"))).count();
        long typeN = cases.stream().filter(x -> x.get("type_correct") != null).count();
        long typeOk = cases.stream().filter(x -> Boolean.TRUE.equals(x.get("type_correct"))).count();
        long binN = cases.stream().filter(x -> x.get("bin_correct") != null).count();
        long binOk = cases.stream().filter(x -> Boolean.TRUE.equals(x.get("bin_correct"))).count();
        Collections.sort(latencies);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("status", "COMPLETE"); s.put("protocol", PROTOCOL); s.put("timestamp_utc", stamp);
        s.put("manifest_path", manifest.toString()); s.put("manifest_sha256", sha256(manifest));
        s.put("benchmark_path", gold.toString()); s.put("benchmark_sha256", sha256(gold));
        s.put("cases_count", cases.size()); s.put("retrieval_success", retrieval + "/10");
        s.put("generation_availability", generation + "/10");
        s.put("type_accuracy", typeN == 0 ? null : (double) typeOk / typeN);
        s.put("bin_accuracy", binN == 0 ? null : (double) binOk / binN);
        s.put("retrieval_p50_ms", null); s.put("retrieval_p95_ms", null);
        s.put("generation_p50_ms", null); s.put("generation_p95_ms", null);
        s.put("total_p50_ms", percentile(latencies, .5)); s.put("total_p95_ms", percentile(latencies, .95));
        s.put("top_k_observation_limitation", "Production VisualRagResult exposes only the top-1 knowledge, label and score although semanticSearch uses Top-K=3");
        s.put("face_mask_mismatch", "Benchmark input face_mask is preserved; production data also contains distinct face-mask and no evaluator normalization is applied");
        return s;
    }

    private void validateCompletedCases(List<Map<String, Object>> cases) {
        List<String> labels = cases.stream().map(x -> String.valueOf(x.get("input_label"))).toList();
        require(cases.size() == 10 && labels.equals(FIXED_LABELS) && new HashSet<>(labels).size() == 10,
                "INVALID_COMPLETED_CASES " + labels);
    }

    private Map<String, Object> knowledgeMap(KnowledgeSnapshot k) {
        if (k == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("knowledge_id", k.knowledgeId()); m.put("source", k.source()); m.put("model", k.model());
        m.put("material", k.material()); m.put("materialDescription", k.materialDescription());
        m.put("note", k.note()); m.put("action", k.action());
        m.put("impact", k.impact()); m.put("toxicity", k.toxicity());
        return m;
    }

    private String contentHash(KnowledgeSnapshot k) throws Exception {
        return sha256Bytes(om.writeValueAsBytes(knowledgeMap(k)));
    }

    private String externalKnowledgeFailure(KnowledgeSnapshot k) {
        if (k == null) return "PRODUCTION_GENERATION_CREATED_NO_ACTIVE_ROW";
        if (isFallback(k)) return "PRODUCTION_GENERATION_RETURNED_FALLBACK; POSSIBLE_GEMINI_429_503_TIMEOUT_OR_EMPTY_JSON";
        return "PRODUCTION_GENERATION_RETURNED_INVALID_REAL_CONTENT";
    }

    private Set<Integer> ragCacheKnowledgeIds() throws Exception {
        Class<?> type = ragService.getClass(); Field field = null;
        while (type != null && field == null) {
            try { field = type.getDeclaredField("embeddingCache"); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        require(field != null, "RAG_CACHE_FIELD_NOT_FOUND");
        field.setAccessible(true);
        Set<Integer> result = new TreeSet<>();
        for (Object key : ((Map<?, ?>) field.get(ragService)).keySet()) result.add((Integer) key);
        return result;
    }

    private Set<Integer> difference(Set<Integer> left, Set<Integer> right) {
        Set<Integer> result = new TreeSet<>(left); result.removeAll(right); return result;
    }

    private List<Float> toFloatList(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) result.add(value);
        return result;
    }

    private void writePerCaseCsv(Path path, List<Map<String, Object>> cases) throws Exception {
        List<String> columns = new ArrayList<>(cases.getFirst().keySet());
        List<String> lines = new ArrayList<>(); lines.add(String.join(",", columns));
        for (Map<String, Object> row : cases) {
            List<String> values = new ArrayList<>();
            for (String column : columns) {
                Object value = row.get(column);
                values.add(csv(value instanceof Map<?, ?> || value instanceof List<?> ? om.writeValueAsString(value) : value));
            }
            lines.add(String.join(",", values));
        }
        Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private void writeSummaryCsv(Path path, Map<String, Object> summary) throws Exception {
        List<String> columns = summary.entrySet().stream().filter(e -> !"cases".equals(e.getKey())).map(Map.Entry::getKey).toList();
        List<String> values = new ArrayList<>(); for (String column : columns) values.add(csv(summary.get(column)));
        Files.write(path, List.of(String.join(",", columns), String.join(",", values)),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private String csv(Object value) {
        if (value == null) return "";
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }

    private Path outputDir() throws Exception {
        Path path = Path.of("..", "..", "reports", "actual_comparison").toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }

    private String timestamp() {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
    }

    private String sha256(Path path) throws Exception { return sha256Bytes(Files.readAllBytes(path)); }
    private String sha256Bytes(byte[] bytes) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private double percentile(List<Double> values, double q) {
        double position = (values.size() - 1) * q; int low = (int) position, high = Math.min(low + 1, values.size() - 1);
        return values.get(low) + (values.get(high) - values.get(low)) * (position - low);
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String safeMessage(Exception e) { return e.getMessage() == null ? e.getClass().getName() : e.getMessage(); }
    private void refuseOverwrite(Path path) { require(!Files.exists(path), "REFUSE_TO_OVERWRITE " + path); }
    private void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private TransactionTemplate readOnlyTransaction() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx;
    }

    private void validateCandidatesReadOnly() {
        List<CandidateSnapshot> snapshots = candidates();
        require(snapshots.size() == EXPECTED_CORPUS_SIZE,
                "CANDIDATE_COUNT_MISMATCH expected=94 actual=" + snapshots.size());
        require(snapshots.stream().allMatch(c -> c.itemId() > 0 && !blank(c.label())
                        && c.trashTypeId() != null && !blank(c.trashTypeName())),
                "CANDIDATE_SNAPSHOT_HAS_MISSING_REQUIRED_SCALAR");
        snapshots.forEach(c -> System.out.printf("CANDIDATE itemId=%d label=%s labelDisplay=%s trashTypeId=%d trashType=%s%n",
                c.itemId(), c.label(), c.labelDisplay(), c.trashTypeId(), c.trashTypeName()));
        System.out.println("READ_ONLY_CANDIDATE_SCAN=PASS count=" + snapshots.size());
    }

    private record CandidateSnapshot(int itemId, String label, String labelDisplay,
                                     Integer trashTypeId, String trashTypeName) {}

    private record KnowledgeSnapshot(Integer knowledgeId, Integer version, Integer itemId,
                                     String label, String labelDisplay, String source, String model,
                                     String material, String materialDescription, String note, String action,
                                     String impact, String toxicity, Boolean active, List<Float> embedding) {}

    private record ResultSnapshot(String ragSource, Float score, KnowledgeSnapshot retrieved,
                                  KnowledgeSnapshot generated, String retrievedLabel,
                                  String predictedType, String predictedBin) {}

    private record BenchmarkInvocation(ResultSnapshot result, String failure) {}
}
