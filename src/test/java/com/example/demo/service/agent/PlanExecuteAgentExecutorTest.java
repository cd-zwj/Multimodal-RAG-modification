package com.example.demo.service.agent;

import com.example.demo.model.dto.MultiTurnChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class PlanExecuteAgentExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private ChatClient deepchatClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ReactAgentExecutor reactAgentExecutor;

    @Test
    void shouldRejectApprovedPlanAfterRedisTtlExpires() throws Exception {
        when(deepchatClient.prompt()).thenThrow(new RuntimeException("llm unavailable"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        PlanExecuteAgentExecutor executor = executor();
        String planId = createPlanAndExtractId(executor);
        when(valueOperations.getAndDelete("agent:plan:s1:" + planId)).thenReturn(null);

        MultiTurnChatRequest approvedRequest = request("执行");
        approvedRequest.setApprovedPlanId(planId);
        List<ServerSentEvent<String>> events = executor.execute(approvedRequest, "u1").collectList().block();

        assertEquals("error", events.get(0).event());
        assertTrue(events.get(0).data().contains("计划不存在或已过期"));
        verify(reactAgentExecutor, never()).execute(any(), anyString());
    }

    @Test
    void shouldConsumeApprovedPlanOnlyOnce() throws Exception {
        when(deepchatClient.prompt()).thenThrow(new RuntimeException("llm unavailable"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        PlanExecuteAgentExecutor executor = executor();
        String planId = createPlanAndExtractId(executor);
        String planJson = """
                {"planId":"%s","sessionId":"s1","originalMessage":"修复问题","planText":"## 执行计划"}
                """.formatted(planId);
        when(valueOperations.getAndDelete("agent:plan:s1:" + planId))
                .thenReturn(planJson)
                .thenReturn(null);
        when(reactAgentExecutor.execute(any(MultiTurnChatRequest.class), eq("u1")))
                .thenReturn(Flux.just(event("token", "done")));

        MultiTurnChatRequest approvedRequest = request("执行");
        approvedRequest.setApprovedPlanId(planId);
        List<ServerSentEvent<String>> firstEvents = executor.execute(approvedRequest, "u1").collectList().block();
        List<ServerSentEvent<String>> secondEvents = executor.execute(approvedRequest, "u1").collectList().block();

        assertEquals("token", firstEvents.get(0).event());
        assertEquals("error", secondEvents.get(0).event());
        assertTrue(secondEvents.get(0).data().contains("计划不存在或已过期"));
        verify(reactAgentExecutor).execute(any(MultiTurnChatRequest.class), eq("u1"));
    }

    @Test
    void shouldReturnErrorWhenPlanCannotBeSaved() {
        when(deepchatClient.prompt()).thenThrow(new RuntimeException("llm unavailable"));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), eq(Duration.ofMinutes(30)));

        List<ServerSentEvent<String>> events = executor().execute(request("修复问题"), "u1").collectList().block();

        assertEquals("error", events.get(0).event());
        assertTrue(events.get(0).data().contains("计划暂时无法保存"));
        assertEquals("done", events.get(1).event());
    }

    @Test
    void shouldPassSessionIdAsConversationIdWhenGeneratingPlan() throws Exception {
        ChatClient.ChatClientRequestSpec requestSpec = org.mockito.Mockito.mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.AdvisorSpec advisorSpec = org.mockito.Mockito.mock(ChatClient.AdvisorSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = org.mockito.Mockito.mock(ChatClient.CallResponseSpec.class);
        when(deepchatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .thenAnswer(invocation -> {
                    invocation.<Consumer<ChatClient.AdvisorSpec>>getArgument(0).accept(advisorSpec);
                    return requestSpec;
                });
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("## 执行计划");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        List<ServerSentEvent<String>> events = executor().execute(request("修复问题"), "u1").collectList().block();

        assertEquals("plan_required", events.get(0).event());
        JsonNode payload = MAPPER.readTree(events.get(0).data());
        assertEquals("## 执行计划", payload.path("planText").asText());
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "agent:plan:s1");
    }
    private PlanExecuteAgentExecutor executor() {
        return new PlanExecuteAgentExecutor(deepchatClient, redisTemplate, reactAgentExecutor);
    }

    private String createPlanAndExtractId(PlanExecuteAgentExecutor executor) throws Exception {
        List<ServerSentEvent<String>> events = executor.execute(request("修复问题"), "u1").collectList().block();
        assertEquals("plan_required", events.get(0).event());
        JsonNode payload = MAPPER.readTree(events.get(0).data());
        String planId = payload.path("planId").asText();
        verify(valueOperations).set(eq("agent:plan:s1:" + planId), anyString(), eq(Duration.ofMinutes(30)));
        return planId;
    }

    private MultiTurnChatRequest request(String message) {
        MultiTurnChatRequest request = new MultiTurnChatRequest();
        request.setUserId("u1");
        request.setSessionId("s1");
        request.setTurnCount(1);
        request.setMessage(message);
        return request;
    }

    private ServerSentEvent<String> event(String event, String data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data)
                .build();
    }
}
