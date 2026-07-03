package com.example.demo.service.llm;

import com.example.demo.model.dto.ApiResponse;
import com.example.demo.model.dto.llm.CreateLlmModelRequest;
import com.example.demo.model.dto.llm.CreateLlmProviderRequest;
import com.example.demo.model.dto.llm.LlmDebugRequest;
import com.example.demo.model.dto.llm.LlmDebugResponse;
import com.example.demo.model.dto.llm.LlmModelResponse;
import com.example.demo.model.dto.llm.LlmProviderResponse;
import com.example.demo.model.dto.llm.UpdateLlmModelRequest;
import com.example.demo.model.dto.llm.UpdateLlmProviderRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

public interface LlmProviderApplicationService {

    ApiResponse<LlmProviderResponse> createProvider(CreateLlmProviderRequest request);

    ApiResponse<LlmProviderResponse> updateProvider(String id, UpdateLlmProviderRequest request);

    ApiResponse<Void> deleteProvider(String id);

    ApiResponse<LlmProviderResponse> enableProvider(String id);

    ApiResponse<LlmProviderResponse> disableProvider(String id);

    default ApiResponse<List<LlmProviderResponse>> listProviders() {
        return listProviders(false);
    }

    ApiResponse<List<LlmProviderResponse>> listProviders(boolean includeDisabled);

    ApiResponse<LlmModelResponse> createModel(CreateLlmModelRequest request);

    ApiResponse<LlmModelResponse> updateModel(String id, UpdateLlmModelRequest request);

    ApiResponse<Void> deleteModel(String id);

    ApiResponse<LlmModelResponse> enableModel(String id);

    ApiResponse<LlmModelResponse> disableModel(String id);

    default ApiResponse<List<LlmModelResponse>> listModels() {
        return listModels(false);
    }

    ApiResponse<List<LlmModelResponse>> listModels(boolean includeDisabled);

    ApiResponse<LlmDebugResponse> debug(LlmDebugRequest request);

    Flux<ServerSentEvent<String>> debugStream(LlmDebugRequest request);
}
