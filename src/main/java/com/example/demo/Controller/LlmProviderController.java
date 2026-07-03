package com.example.demo.Controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.demo.model.dto.ApiResponse;
import com.example.demo.model.dto.llm.CreateLlmModelRequest;
import com.example.demo.model.dto.llm.CreateLlmProviderRequest;
import com.example.demo.model.dto.llm.LlmDebugRequest;
import com.example.demo.model.dto.llm.LlmDebugResponse;
import com.example.demo.model.dto.llm.LlmModelResponse;
import com.example.demo.model.dto.llm.LlmProviderResponse;
import com.example.demo.model.dto.llm.UpdateLlmModelRequest;
import com.example.demo.model.dto.llm.UpdateLlmProviderRequest;
import com.example.demo.service.llm.LlmProviderApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/llm")
@RequiredArgsConstructor
@Tag(name = "自定义LLM管理", description = "Provider 和模型配置创建、编辑、删除与在线调试")
public class LlmProviderController {

    private final LlmProviderApplicationService llmProviderApplicationService;

    @SaCheckPermission("llm:provider:create")
    @PostMapping("/providers")
    public ApiResponse<LlmProviderResponse> createProvider(@Valid @RequestBody CreateLlmProviderRequest request) {
        return llmProviderApplicationService.createProvider(request);
    }

    @SaCheckPermission("llm:provider:update")
    @PutMapping("/providers/{id}")
    public ApiResponse<LlmProviderResponse> updateProvider(@PathVariable String id,
                                                           @Valid @RequestBody UpdateLlmProviderRequest request) {
        return llmProviderApplicationService.updateProvider(id, request);
    }

    @SaCheckPermission("llm:provider:delete")
    @DeleteMapping("/providers/{id}")
    public ApiResponse<Void> deleteProvider(@PathVariable String id) {
        return llmProviderApplicationService.deleteProvider(id);
    }

    @SaCheckPermission("llm:provider:enable")
    @PostMapping("/providers/{id}/enable")
    public ApiResponse<LlmProviderResponse> enableProvider(@PathVariable String id) {
        return llmProviderApplicationService.enableProvider(id);
    }

    @SaCheckPermission("llm:provider:disable")
    @PostMapping("/providers/{id}/disable")
    public ApiResponse<LlmProviderResponse> disableProvider(@PathVariable String id) {
        return llmProviderApplicationService.disableProvider(id);
    }

    @SaCheckPermission("llm:debug")
    @GetMapping("/providers")
    public ApiResponse<List<LlmProviderResponse>> listProviders(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return llmProviderApplicationService.listProviders(includeDisabled);
    }

    public ApiResponse<List<LlmProviderResponse>> listProviders() {
        return llmProviderApplicationService.listProviders();
    }

    @SaCheckPermission("llm:model:create")
    @PostMapping("/models")
    public ApiResponse<LlmModelResponse> createModel(@Valid @RequestBody CreateLlmModelRequest request) {
        return llmProviderApplicationService.createModel(request);
    }

    @SaCheckPermission("llm:model:update")
    @PutMapping("/models/{id}")
    public ApiResponse<LlmModelResponse> updateModel(@PathVariable String id,
                                                     @Valid @RequestBody UpdateLlmModelRequest request) {
        return llmProviderApplicationService.updateModel(id, request);
    }

    @SaCheckPermission("llm:model:delete")
    @DeleteMapping("/models/{id}")
    public ApiResponse<Void> deleteModel(@PathVariable String id) {
        return llmProviderApplicationService.deleteModel(id);
    }

    @SaCheckPermission("llm:model:enable")
    @PostMapping("/models/{id}/enable")
    public ApiResponse<LlmModelResponse> enableModel(@PathVariable String id) {
        return llmProviderApplicationService.enableModel(id);
    }

    @SaCheckPermission("llm:model:disable")
    @PostMapping("/models/{id}/disable")
    public ApiResponse<LlmModelResponse> disableModel(@PathVariable String id) {
        return llmProviderApplicationService.disableModel(id);
    }

    @SaCheckPermission("llm:debug")
    @GetMapping("/models")
    public ApiResponse<List<LlmModelResponse>> listModels(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return llmProviderApplicationService.listModels(includeDisabled);
    }

    public ApiResponse<List<LlmModelResponse>> listModels() {
        return llmProviderApplicationService.listModels();
    }

    @SaCheckPermission("llm:debug")
    @PostMapping("/debug")
    public ApiResponse<LlmDebugResponse> debug(@Valid @RequestBody LlmDebugRequest request) {
        return llmProviderApplicationService.debug(request);
    }

    @SaCheckPermission("llm:debug")
    @PostMapping(value = "/debug/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> debugStream(@Valid @RequestBody LlmDebugRequest request) {
        return llmProviderApplicationService.debugStream(request);
    }
}
