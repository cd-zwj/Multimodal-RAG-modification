package com.example.demo.Controller;

import com.example.demo.model.dto.ApiResponse;
import com.example.demo.model.dto.llm.CreateLlmModelRequest;
import com.example.demo.model.dto.llm.LlmModelResponse;
import com.example.demo.model.dto.llm.UpdateLlmModelRequest;
import com.example.demo.service.llm.LlmOpsMetricsService;
import com.example.demo.service.llm.LlmProviderApplicationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmModelControllerTest {

    @Test
    void shouldDelegateCreateModel() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);
        CreateLlmModelRequest request = new CreateLlmModelRequest();
        LlmModelResponse response = response();
        when(service.createModel(request)).thenReturn(ApiResponse.success(response));

        ApiResponse<LlmModelResponse> result = controller.createModel(request);

        assertEquals(200, result.getCode());
        assertEquals("qwen-plus-prod", result.getData().getModelCode());
    }

    @Test
    void shouldDelegateUpdateModel() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);
        UpdateLlmModelRequest request = new UpdateLlmModelRequest();
        LlmModelResponse response = response();
        response.setDisplayName("Qwen Plus Latest");
        when(service.updateModel("m1", request)).thenReturn(ApiResponse.success(response));

        ApiResponse<LlmModelResponse> result = controller.updateModel("m1", request);

        assertEquals(200, result.getCode());
        assertEquals("Qwen Plus Latest", result.getData().getDisplayName());
    }

    @Test
    void shouldDelegateDeleteModel() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);
        when(service.deleteModel("m1")).thenReturn(ApiResponse.success("删除成功"));

        ApiResponse<Void> result = controller.deleteModel("m1");

        assertEquals(200, result.getCode());
        assertEquals("删除成功", result.getMessage());
    }

    @Test
    void shouldDelegateListModels() {
        LlmProviderApplicationService service = mock(LlmProviderApplicationService.class);
        LlmProviderController controller = controller(service);
        when(service.listModels(false)).thenReturn(ApiResponse.success(List.of(response())));

        ApiResponse<List<LlmModelResponse>> result = controller.listModels(false);

        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    private LlmModelResponse response() {
        LlmModelResponse response = new LlmModelResponse();
        response.setId("m1");
        response.setProviderId("p1");
        response.setProviderCode("custom-openai");
        response.setProviderName("自定义 OpenAI");
        response.setModelCode("qwen-plus-prod");
        response.setDisplayName("Qwen Plus");
        response.setRemoteModelName("qwen-plus");
        return response;
    }

    private LlmProviderController controller(LlmProviderApplicationService service) {
        return new LlmProviderController(service, new LlmOpsMetricsService());
    }
}
