package com.example.demo.service.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmOpsMetricsServiceTest {

    @Test
    void shouldAggregateSuccessFailureLatencyAndTokenEstimate() {
        LlmOpsMetricsService service = new LlmOpsMetricsService();
        RuntimeLlmProvider provider = RuntimeLlmProvider.builder()
                .providerCode("custom-openai")
                .defaultModel("gpt-test")
                .build();

        service.recordSuccess(provider, 120, "abcdefghijkl");
        service.recordFailure(provider, 80, "timeout");

        List<Map<String, Object>> snapshots = service.snapshots();

        assertThat(snapshots).hasSize(1);
        Map<String, Object> metrics = snapshots.get(0);
        assertThat(metrics.get("providerCode")).isEqualTo("custom-openai");
        assertThat(metrics.get("modelCode")).isEqualTo("gpt-test");
        assertThat(metrics.get("successCount")).isEqualTo(1L);
        assertThat(metrics.get("failureCount")).isEqualTo(1L);
        assertThat(metrics.get("successRate")).isEqualTo(50.0d);
        assertThat(metrics.get("averageLatencyMs")).isEqualTo(100L);
        assertThat(metrics.get("estimatedTokens")).isEqualTo(3L);
        assertThat(metrics.get("lastError")).isEqualTo("timeout");
    }
}
