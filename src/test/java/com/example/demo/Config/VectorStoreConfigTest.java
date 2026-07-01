package com.example.demo.Config;

import io.micrometer.observation.ObservationRegistry;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class VectorStoreConfigTest {

    @Test
    void leafVectorStoreShouldUseConfiguredMilvusCollection() {
        VectorStoreConfig config = new VectorStoreConfig();

        MilvusVectorStore store = (MilvusVectorStore) config.leafVectorStore(
                Mockito.mock(EmbeddingModel.class),
                Mockito.mock(MilvusServiceClient.class),
                false,
                "default",
                "rag_leaf_vectors",
                1024,
                "IVF_FLAT",
                "COSINE",
                "{\"nlist\":1024}",
                observationProvider()
        );

        assertInstanceOf(MilvusVectorStore.class, store);
        assertEquals("rag_leaf_vectors", ReflectionTestUtils.getField(store, "collectionName"));
        assertEquals("default", ReflectionTestUtils.getField(store, "databaseName"));
        assertEquals(1024, ReflectionTestUtils.getField(store, "embeddingDimension"));
        assertEquals(IndexType.IVF_FLAT, ReflectionTestUtils.getField(store, "indexType"));
        assertEquals(MetricType.COSINE, ReflectionTestUtils.getField(store, "metricType"));
    }

    @Test
    void summaryVectorStoreShouldUseSeparateMilvusCollection() {
        VectorStoreConfig config = new VectorStoreConfig();

        MilvusVectorStore store = (MilvusVectorStore) config.summaryVectorStore(
                Mockito.mock(EmbeddingModel.class),
                Mockito.mock(MilvusServiceClient.class),
                false,
                "default",
                "rag_summary_vectors",
                1024,
                "IVF_FLAT",
                "COSINE",
                "{\"nlist\":1024}",
                observationProvider()
        );

        assertInstanceOf(MilvusVectorStore.class, store);
        assertEquals("rag_summary_vectors", ReflectionTestUtils.getField(store, "collectionName"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ObservationRegistry> observationProvider() {
        ObjectProvider<ObservationRegistry> observationProvider = Mockito.mock(ObjectProvider.class);
        when(observationProvider.getIfAvailable(any(Supplier.class)))
                .thenAnswer(invocation -> invocation.<Supplier<ObservationRegistry>>getArgument(0).get());
        return observationProvider;
    }
}
