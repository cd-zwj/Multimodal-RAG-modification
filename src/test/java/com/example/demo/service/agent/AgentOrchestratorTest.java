package com.example.demo.service.agent;

import com.example.demo.model.dto.AgentMode;
import com.example.demo.model.dto.MultiTurnChatRequest;
import com.example.demo.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private AgentModeRouter agentModeRouter;

    @Mock
    private AgentSessionModeService agentSessionModeService;

    @Mock
    private ReactAgentExecutor reactAgentExecutor;

    @Mock
    private PlanExecuteAgentExecutor planExecuteAgentExecutor;

    @Mock
    private ChatSessionService chatSessionService;

    @Test
    void shouldLetModeHintOverrideRouterAndPersistMode() {
        MultiTurnChatRequest request = request("解释这段话");
        request.setModeHint(AgentMode.REACT);
        when(chatSessionService.requireActiveSessionUser("s1")).thenReturn("u1");
        when(reactAgentExecutor.execute(request, "u1")).thenReturn(Flux.just(token("ok")));

        List<ServerSentEvent<String>> events = orchestrator().chat(request).collectList().block();

        assertEquals("route_decision", events.get(0).event());
        assertTrue(events.get(0).data().contains("\"mode\":\"REACT\""));
        verify(agentModeRouter, never()).route(any());
        verify(agentSessionModeService).saveMode(eq("s1"), eq(AgentMode.REACT), any());
        verify(reactAgentExecutor).execute(request, "u1");
    }

    @Test
    void shouldReusePersistedModeForSameSession() {
        MultiTurnChatRequest request = request("继续");
        when(chatSessionService.requireActiveSessionUser("s1")).thenReturn("u1");
        when(agentSessionModeService.getMode("s1")).thenReturn(Optional.of(AgentMode.PLAN_EXECUTE));
        when(planExecuteAgentExecutor.execute(request, "u1")).thenReturn(Flux.just(event("plan_required", "{}")));

        List<ServerSentEvent<String>> events = orchestrator().chat(request).collectList().block();

        assertTrue(events.get(0).data().contains("\"source\":\"session\""));
        verify(agentModeRouter, never()).route(any());
        verify(planExecuteAgentExecutor).execute(request, "u1");
    }

    @Test
    void shouldRouteAutoRequestWhenNoPersistedModeExists() {
        MultiTurnChatRequest request = request("帮我排查并修复这个问题");
        when(chatSessionService.requireActiveSessionUser("s1")).thenReturn("u1");
        when(agentSessionModeService.getMode("s1")).thenReturn(Optional.empty());
        when(agentModeRouter.route(request)).thenReturn(
                AgentRouteDecision.of(AgentMode.PLAN_EXECUTE, 0.9, "复杂任务", "router")
        );
        when(planExecuteAgentExecutor.execute(request, "u1")).thenReturn(Flux.just(event("plan_required", "{}")));

        List<ServerSentEvent<String>> events = orchestrator().chat(request).collectList().block();

        assertTrue(events.get(0).data().contains("\"mode\":\"PLAN_EXECUTE\""));
        verify(agentSessionModeService).saveMode(eq("s1"), eq(AgentMode.PLAN_EXECUTE), any());
        verify(planExecuteAgentExecutor).execute(request, "u1");
    }

    @Test
    void shouldUsePlanExecuteWhenApprovedPlanIdIsPresent() {
        MultiTurnChatRequest request = request("执行");
        request.setApprovedPlanId("plan-1");
        when(chatSessionService.requireActiveSessionUser("s1")).thenReturn("u1");
        when(planExecuteAgentExecutor.execute(request, "u1")).thenReturn(Flux.just(token("done")));

        List<ServerSentEvent<String>> events = orchestrator().chat(request).collectList().block();

        assertTrue(events.get(0).data().contains("\"source\":\"approved_plan\""));
        verify(agentModeRouter, never()).route(any());
        verify(planExecuteAgentExecutor).execute(request, "u1");
    }

    private AgentOrchestrator orchestrator() {
        return new AgentOrchestrator(
                agentModeRouter,
                agentSessionModeService,
                reactAgentExecutor,
                planExecuteAgentExecutor,
                chatSessionService
        );
    }

    private MultiTurnChatRequest request(String message) {
        MultiTurnChatRequest request = new MultiTurnChatRequest();
        request.setUserId("u1");
        request.setSessionId("s1");
        request.setTurnCount(1);
        request.setMessage(message);
        return request;
    }

    private ServerSentEvent<String> token(String data) {
        return event("token", data);
    }

    private ServerSentEvent<String> event(String event, String data) {
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data)
                .build();
    }
}
