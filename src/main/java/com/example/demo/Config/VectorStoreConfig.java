package com.example.demo.Config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.FieldName;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.NumericField;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class VectorStoreConfig {

    @Bean
    public JedisPooled jedisPooled(JedisConnectionFactory jedisConnectionFactory) {
        DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .ssl(jedisConnectionFactory.isUseSsl())
                .clientName(jedisConnectionFactory.getClientName())
                .timeoutMillis(jedisConnectionFactory.getTimeout())
                .password(jedisConnectionFactory.getPassword())
                .build();
        return new JedisPooled(
                new HostAndPort(jedisConnectionFactory.getHostName(), jedisConnectionFactory.getPort()),
                clientConfig
        );
    }

    @Bean
    @Primary
    public VectorStore leafVectorStore(EmbeddingModel embeddingModel,
                                       JedisPooled jedisPooled,
                                       @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema,
                                       @Value("${spring.ai.vectorstore.redis.index-name:rag-leaf-index}") String indexName,
                                       @Value("${spring.ai.vectorstore.redis.prefix:rag-leaf-prefix}") String prefix,
                                       ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        RedisVectorStore store = RedisVectorStore.builder(jedisPooled, embeddingModel)
                .initializeSchema(initializeSchema)
                .observationRegistry(observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP))
                .indexName(indexName)
                .prefix(prefix)
                .metadataFields(defaultMetadataFields())
                .build();
        ensureIndexExists(jedisPooled, indexName, prefix, embeddingModel.dimensions());
        return store;
    }

    @Bean
    public VectorStore summaryVectorStore(EmbeddingModel embeddingModel,
                                          JedisPooled jedisPooled,
                                          @Value("${spring.ai.vectorstore.redis.initialize-schema:true}") boolean initializeSchema,
                                          @Value("${spring.ai.vectorstore.redis.summary-index-name:rag-summary-index}") String indexName,
                                          @Value("${spring.ai.vectorstore.redis.summary-prefix:rag-summary-prefix}") String prefix,
                                          ObjectProvider<ObservationRegistry> observationRegistryProvider) {
        RedisVectorStore store = RedisVectorStore.builder(jedisPooled, embeddingModel)
                .initializeSchema(initializeSchema)
                .observationRegistry(observationRegistryProvider.getIfAvailable(() -> ObservationRegistry.NOOP))
                .indexName(indexName)
                .prefix(prefix)
                .metadataFields(defaultMetadataFields())
                .build();
        ensureIndexExists(jedisPooled, indexName, prefix, embeddingModel.dimensions());
        return store;
    }

    void ensureIndexExists(JedisPooled jedisPooled, String indexName, String prefix, int dimensions) {
        try {
            Map<String, Object> info = jedisPooled.ftInfo(indexName);
            if (!isCompatibleJsonIndex(info, prefix)) {
                log.warn("RediSearch index {} schema/prefix is incompatible, recreating without deleting documents", indexName);
                jedisPooled.ftDropIndex(indexName);
                createJsonVectorIndex(jedisPooled, indexName, prefix, dimensions);
            }
            log.info("RediSearch index exists: {}", indexName);
            return;
        } catch (JedisDataException e) {
            String lowerMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (isRediSearchMissing(lowerMsg)) {
                log.error("RediSearch module not loaded. Please install redis-stack.", e);
                throw new RuntimeException("Redis missing RediSearch module, vector search unavailable.", e);
            }
            if (!lowerMsg.contains("unknown index")) {
                throw e;
            }
        }
        log.warn("RediSearch index {} missing, attempting auto-create...", indexName);
        try {
            createJsonVectorIndex(jedisPooled, indexName, prefix, dimensions);
            log.info("RediSearch index auto-created: {}", indexName);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (isRediSearchMissing(msg.toLowerCase())) {
                log.error("RediSearch module not loaded. Please install redis-stack.", e);
                throw new RuntimeException("Redis missing RediSearch module, vector search unavailable.", e);
            }
            log.error("RediSearch index creation failed: index={}, error={}", indexName, msg);
            throw new RuntimeException(String.format("RediSearch index '%s' creation failed: %s", indexName, msg), e);
        }
    }

    private void createJsonVectorIndex(JedisPooled jedisPooled, String indexName, String prefix, int dimensions) {
        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("DIM", dimensions);
        vectorAttrs.put("DISTANCE_METRIC", "COSINE");

        List<SchemaField> fields = new ArrayList<>();
        fields.add(TextField.of(FieldName.of("$.content").as("content")));
        fields.add(new VectorField(
                FieldName.of("$.embedding").as("embedding"),
                VectorField.VectorAlgorithm.HNSW,
                vectorAttrs
        ));
        fields.add(TagField.of(FieldName.of("$.source_id").as("source_id")));
        fields.add(TagField.of(FieldName.of("$.source_type").as("source_type")));
        fields.add(TagField.of(FieldName.of("$.unit_id").as("unit_id")));
        fields.add(TagField.of(FieldName.of("$.user_id").as("user_id")));
        fields.add(TagField.of(FieldName.of("$.node_type").as("node_type")));
        fields.add(TagField.of(FieldName.of("$.parent_id").as("parent_id")));
        fields.add(TextField.of(FieldName.of("$.filename").as("filename")));
        fields.add(TextField.of(FieldName.of("$.title").as("title")));
        fields.add(NumericField.of(FieldName.of("$.tree_level").as("tree_level")));
        fields.add(NumericField.of(FieldName.of("$.child_count").as("child_count")));
        fields.add(NumericField.of(FieldName.of("$.chunk_index").as("chunk_index")));
        fields.add(NumericField.of(FieldName.of("$.start_time").as("start_time")));
        fields.add(NumericField.of(FieldName.of("$.end_time").as("end_time")));

        jedisPooled.ftCreate(
                indexName,
                FTCreateParams.createParams()
                        .on(IndexDataType.JSON)
                        .addPrefix(prefix),
                fields
        );
    }

    private boolean isCompatibleJsonIndex(Map<String, Object> info, String prefix) {
        String indexDefinition = String.valueOf(info.get("index_definition"));
        Object attributes = info.get("attributes");
        return indexDefinition.contains("key_type, JSON")
                && indexDefinition.contains(prefix)
                && String.valueOf(attributes).contains("$.embedding")
                && String.valueOf(attributes).contains("$.user_id");
    }

    private boolean isRediSearchMissing(String lowerMsg) {
        return lowerMsg.contains("unknown command")
                || lowerMsg.contains("unknown subcommand")
                || (lowerMsg.contains("module") && lowerMsg.contains("not loaded"));
    }

    static List<RedisVectorStore.MetadataField> defaultMetadataFields() {
        return List.of(
                RedisVectorStore.MetadataField.tag("source_id"),
                RedisVectorStore.MetadataField.tag("source_type"),
                RedisVectorStore.MetadataField.tag("unit_id"),
                RedisVectorStore.MetadataField.tag("user_id"),
                RedisVectorStore.MetadataField.tag("node_type"),
                RedisVectorStore.MetadataField.tag("parent_id"),
                RedisVectorStore.MetadataField.text("filename"),
                RedisVectorStore.MetadataField.text("title"),
                RedisVectorStore.MetadataField.numeric("tree_level"),
                RedisVectorStore.MetadataField.numeric("child_count"),
                RedisVectorStore.MetadataField.numeric("chunk_index"),
                RedisVectorStore.MetadataField.numeric("start_time"),
                RedisVectorStore.MetadataField.numeric("end_time")
        );
    }
}
