package com.example.demo.service.llm;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class LlmOpsMetricsService {

    private final ConcurrentHashMap<String, Stats> statsByModel = new ConcurrentHashMap<>();

    public void recordSuccess(RuntimeLlmProvider provider, long latencyMs, String content) {
        stats(provider).recordSuccess(latencyMs, estimateTokens(content), "");
    }

    public void recordFailure(RuntimeLlmProvider provider, long latencyMs, String errorMessage) {
        stats(provider).recordFailure(latencyMs, safeError(errorMessage));
    }

    public List<Map<String, Object>> snapshots() {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (Stats stats : statsByModel.values()) {
            snapshots.add(stats.snapshot());
        }
        snapshots.sort(Comparator.comparing(item -> String.valueOf(item.get("modelCode"))));
        return snapshots;
    }

    private Stats stats(RuntimeLlmProvider provider) {
        String modelCode = provider.getProviderCode() + ":" + provider.getDefaultModel();
        return statsByModel.computeIfAbsent(modelCode, ignored -> new Stats(provider.getProviderCode(), provider.getDefaultModel()));
    }

    private long estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, Math.round(content.length() / 4.0d));
    }

    private String safeError(String message) {
        if (message == null || message.isBlank()) {
            return "调用失败";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static final class Stats {
        private final String providerCode;
        private final String modelCode;
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failureCount = new AtomicLong();
        private final AtomicLong totalLatencyMs = new AtomicLong();
        private final AtomicLong totalEstimatedTokens = new AtomicLong();
        private volatile String lastError = "";
        private volatile String lastUpdatedAt = "";

        private Stats(String providerCode, String modelCode) {
            this.providerCode = providerCode;
            this.modelCode = modelCode;
        }

        private void recordSuccess(long latencyMs, long estimatedTokens, String error) {
            successCount.incrementAndGet();
            totalLatencyMs.addAndGet(Math.max(latencyMs, 0));
            totalEstimatedTokens.addAndGet(Math.max(estimatedTokens, 0));
            lastError = error;
            lastUpdatedAt = Instant.now().toString();
        }

        private void recordFailure(long latencyMs, String error) {
            failureCount.incrementAndGet();
            totalLatencyMs.addAndGet(Math.max(latencyMs, 0));
            lastError = error;
            lastUpdatedAt = Instant.now().toString();
        }

        private Map<String, Object> snapshot() {
            long success = successCount.get();
            long failure = failureCount.get();
            long total = success + failure;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("providerCode", providerCode);
            data.put("modelCode", modelCode);
            data.put("successCount", success);
            data.put("failureCount", failure);
            data.put("successRate", total == 0 ? 0 : Math.round(success * 10000.0d / total) / 100.0d);
            data.put("averageLatencyMs", total == 0 ? 0 : totalLatencyMs.get() / total);
            data.put("estimatedTokens", totalEstimatedTokens.get());
            data.put("estimatedCost", 0);
            data.put("circuitBreakerOpenCount", 0);
            data.put("lastError", lastError);
            data.put("lastUpdatedAt", lastUpdatedAt);
            return data;
        }
    }
}
