package com.example.demo.Controller;

import com.example.demo.model.dto.ApiResponse;
import com.example.demo.model.dto.llm.CreateLlmProviderRequest;
import com.example.demo.model.dto.llm.LlmDebugRequest;
import com.example.demo.model.dto.llm.LlmDebugResponse;
import com.example.demo.model.dto.llm.LlmProviderResponse;
import com.example.demo.model.dto.llm.UpdateLlmProviderRequest;
import com.example.demo.model.llm.LlmAuthType;
import com.example.demo.model.llm.LlmProtocolType;
import com.example.demo.service.llm.LlmProviderApplicationService;
import com.example.demo.service.llm.LlmOpsMetricsService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmProviderControllerTest {

    @Test
    void shouldDelegateCreateProvider() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);
        CreateLlmProviderRequest request = new CreateLlmProviderRequest();
        request.setProviderCode("custom-openai");
        request.setProviderName("自定义 OpenAI");
        request.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        request.setAuthType(LlmAuthType.BEARER);
        request.setEndpointUrl("https://example.com/v1/chat/completions");
        request.setApiKey("sk-test");
        request.setDefaultModel("qwen-plus");

        LlmProviderResponse response = new LlmProviderResponse();
        response.setId("p1");
        response.setProviderCode("custom-openai");

        when(service.createProvider(request)).thenReturn(ApiResponse.success(response));

        ApiResponse<LlmProviderResponse> result = controller.createProvider(request);

        assertEquals(200, result.getCode());
        assertEquals("p1", result.getData().getId());
    }

    @Test
    void shouldDelegateDebugProvider() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);
        LlmDebugRequest request = new LlmDebugRequest();
        request.setProviderCode("custom-openai");
        request.setMessage("你好");
        request.setSystemPrompt("你是一个助手");
        request.setStream(false);
        request.setParams(Map.of("temperature", 0.7));

        LlmDebugResponse response = new LlmDebugResponse();
        response.setSuccess(true);
        response.setParsedContent("测试成功");

        when(service.debug(request)).thenReturn(ApiResponse.success(response));

        ApiResponse<LlmDebugResponse> result = controller.debug(request);

        assertEquals(200, result.getCode());
        assertEquals("测试成功", result.getData().getParsedContent());
    }

    @Test
    void shouldDelegateListProviders() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);

        LlmProviderResponse response = new LlmProviderResponse();
        response.setId("p1");
        response.setProviderCode("custom-openai");
        when(service.listProviders(false)).thenReturn(ApiResponse.success(List.of(response)));

        ApiResponse<List<LlmProviderResponse>> result = controller.listProviders(false);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
        assertEquals("custom-openai", result.getData().get(0).getProviderCode());
    }

    @Test
    void shouldDelegateUpdateProvider() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);
        UpdateLlmProviderRequest request = new UpdateLlmProviderRequest();
        request.setProviderName("更新后的 Provider");
        request.setProtocolType(LlmProtocolType.OPENAI_COMPATIBLE);
        request.setAuthType(LlmAuthType.BEARER);
        request.setEndpointUrl("https://example.com/v1/chat/completions");
        request.setDefaultModel("qwen-plus");

        LlmProviderResponse response = new LlmProviderResponse();
        response.setId("p1");
        response.setProviderName("更新后的 Provider");
        when(service.updateProvider("p1", request)).thenReturn(ApiResponse.success(response));

        ApiResponse<LlmProviderResponse> result = controller.updateProvider("p1", request);

        assertEquals(200, result.getCode());
        assertEquals("更新后的 Provider", result.getData().getProviderName());
    }

    @Test
    void shouldDelegateDeleteProvider() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);
        when(service.deleteProvider("p1")).thenReturn(ApiResponse.success("删除成功"));

        ApiResponse<Void> result = controller.deleteProvider("p1");

        assertEquals(200, result.getCode());
        assertEquals("删除成功", result.getMessage());
    }

    private LlmProviderController controller(LlmProviderApplicationService service) {
        return new LlmProviderController(service, new LlmOpsMetricsService());
    }
}
