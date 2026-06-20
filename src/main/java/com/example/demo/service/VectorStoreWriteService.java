package com.example.demo.service;

import com.example.demo.model.RagNodeType;
import com.example.demo.model.RagUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class VectorStoreWriteService {

    private static final int VECTOR_BATCH_SIZE = 10;
    private static final int VECTOR_ADD_MAX_RETRIES = 3;
    private static final long VECTOR_ADD_RETRY_DELAY_MS = 200L;

    private final VectorStore leafVectorStore;
    private final VectorStore summaryVectorStore;
    private final JedisPooled jedisPooled;
    private final String leafIndexName;
    private final String summaryIndexName;

    public VectorStoreWriteService(
            @Qualifier("leafVectorStore") VectorStore leafVectorStore,
            @Qualifier("summaryVectorStore") VectorStore summaryVectorStore,
            JedisPooled jedisPooled,
            @Value("${spring.ai.vectorstore.redis.index-name:rag-leaf-index}") String leafIndexName,
            @Value("${spring.ai.vectorstore.redis.summary-index-name:rag-summary-index}") String summaryIndexName) {
        this.leafVectorStore = leafVectorStore;
        this.summaryVectorStore = summaryVectorStore;
        this.jedisPooled = jedisPooled;
        this.leafIndexName = leafIndexName;
        this.summaryIndexName = summaryIndexName;
    }

    public void addUnitsToVectorStores(List<RagUnit> units, String filename) {
        List<Document> leafDocuments = new ArrayList<>();
        List<Document> summaryDocuments = new ArrayList<>();

        for (RagUnit unit : units) {
            if (unit.getContent() == null || unit.getContent().isBlank()) {
                continue;
            }

            Map<String, Object> metadata = buildVectorMetadata(unit, filename);
            Document document = new Document(unit.getId(), unit.getContent(), metadata);

            if (unit.getNodeType() == RagNodeType.LEAF || unit.getNodeType() == null) {
                leafDocuments.add(document);
            } else {
                summaryDocuments.add(document);
            }
        }

        if (!leafDocuments.isEmpty()) {
            batchAdd(leafVectorStore, leafDocuments);
            verifyIndexExists(leafIndexName, leafDocuments.get(0).getId());
        }
        if (!summaryDocuments.isEmpty()) {
            batchAdd(summaryVectorStore, summaryDocuments);
            verifyIndexExists(summaryIndexName, summaryDocuments.get(0).getId());
        }
    }

    public void deleteFromVectorStores(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        leafVectorStore.delete(ids);
        summaryVectorStore.delete(ids);
    }

    private void verifyIndexExists(String indexName, String sampleDocumentId) {
        try {
            jedisPooled.ftInfo(indexName);
            log.info("RediSearch index verified: index={}, sampleDocId={}", indexName, sampleDocumentId);
        } catch (redis.clients.jedis.exceptions.JedisDataException e) {
            log.error("RediSearch index missing: index={}, data written but not searchable.", indexName);
            throw new RuntimeException(
                String.format("Redis vector index '%s' missing, document data not searchable. " +
                    "Please ensure Redis has RediSearch module loaded.", indexName), e);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : "";
            log.warn("RediSearch index verification anomaly: index={}, sampleDocId={}, error={}", indexName, sampleDocumentId, message);
        }
    }

    private void batchAdd(VectorStore vectorStore, List<Document> documents) {
        for (int i = 0; i < documents.size(); i += VECTOR_BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + VECTOR_BATCH_SIZE, documents.size()));
            addBatchWithRetry(vectorStore, batch);
        }
    }

    private void addBatchWithRetry(VectorStore vectorStore, List<Document> batch) {
        RuntimeException batchFailure = null;

        for (int attempt = 1; attempt <= VECTOR_ADD_MAX_RETRIES; attempt++) {
            try {
                vectorStore.add(batch);
                return;
            } catch (RuntimeException e) {
                batchFailure = e;
                log.warn("Vector batch write failed, retrying: attempt={}/{}, size={}, firstDocumentId={}",
                        attempt, VECTOR_ADD_MAX_RETRIES, batch.size(), batch.get(0).getId(), e);
                sleepBeforeRetry(attempt);
            }
        }

        if (batch.size() == 1) {
            throw new RuntimeException("Vector write failed, documentId=" + batch.get(0).getId(), batchFailure);
        }

        log.warn("Vector batch write persistently failed, degrading to single writes: size={}, firstDocumentId={}",
                batch.size(), batch.get(0).getId(), batchFailure);

        List<String> failedDocumentIds = new ArrayList<>();
        RuntimeException singleFailure = null;
        for (Document document : batch) {
            try {
                addSingleWithRetry(vectorStore, document);
            } catch (RuntimeException e) {
                failedDocumentIds.add(document.getId());
                singleFailure = e;
            }
        }

        if (!failedDocumentIds.isEmpty()) {
            throw new RuntimeException("Vector write failed, documentIds=" + String.join(",", failedDocumentIds), singleFailure);
        }
    }

    private void addSingleWithRetry(VectorStore vectorStore, Document document) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= VECTOR_ADD_MAX_RETRIES; attempt++) {
            try {
                vectorStore.add(List.of(document));
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("Vector single write failed, retrying: attempt={}/{}, documentId={}",
                        attempt, VECTOR_ADD_MAX_RETRIES, document.getId(), e);
                sleepBeforeRetry(attempt);
            }
        }

        throw new RuntimeException("Vector write failed, documentId=" + document.getId(), lastFailure);
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(VECTOR_ADD_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Vector write retry interrupted", e);
        }
    }

    public Map<String, Object> buildVectorMetadata(RagUnit unit, String filename) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source_id", unit.getSourceId());
        metadata.put("source_type", unit.getSourceType().name());
        metadata.put("unit_id", unit.getId());
        if (unit.getUserId() != null) {
            metadata.put("user_id", unit.getUserId());
        }
        metadata.put("filename", filename);
        metadata.put("node_type", unit.getNodeType() != null ? unit.getNodeType().name() : RagNodeType.LEAF.name());
        metadata.put("tree_level", unit.getTreeLevel() != null ? unit.getTreeLevel() : 0);
        if (unit.getParentId() != null) {
            metadata.put("parent_id", unit.getParentId());
        }
        if (unit.getTitle() != null) {
            metadata.put("title", unit.getTitle());
        }
        if (unit.getChildCount() != null) {
            metadata.put("child_count", unit.getChildCount());
        }
        if (unit.getChunkIndex() != null) {
            metadata.put("chunk_index", unit.getChunkIndex());
        }
        if (unit.getStartTime() != null) {
            metadata.put("start_time", unit.getStartTime());
        }
        if (unit.getEndTime() != null) {
            metadata.put("end_time", unit.getEndTime());
        }
        return metadata;
    }
}
