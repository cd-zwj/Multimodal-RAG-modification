package com.example.demo.service.agent;

import com.example.demo.model.dto.AgentMode;
import com.example.demo.model.dto.MultiTurnChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentModeRouterTest {

    @Test
    void shouldParseRouterJsonDecision() {
        AgentModeRouter router = new AgentModeRouter(mock(org.springframework.ai.chat.client.ChatClient.class));

        AgentRouteDecision decision = router.parseDecision("""
                {"mode":"PLAN_EXECUTE","confidence":0.86,"reason":"需要先计划再执行"}
                """);

        assertEquals(AgentMode.PLAN_EXECUTE, decision.getMode());
        assertTrue(decision.getConfidence() > 0.8);
        assertEquals("需要先计划再执行", decision.getReason());
        assertEquals("router", decision.getSource());
    }

    @Test
    void shouldFallbackToReactWhenRouterJsonIsInvalid() {
        AgentModeRouter router = new AgentModeRouter(mock(org.springframework.ai.chat.client.ChatClient.class));

        AgentRouteDecision decision = router.parseDecision("PLAN_EXECUTE");

        assertEquals(AgentMode.REACT, decision.getMode());
        assertEquals("fallback", decision.getSource());
    }

    @Test
    void shouldPassSessionIdAsConversationIdWhenRouting() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(org.mockito.ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any()))
                .thenAnswer(invocation -> {
                    invocation.<Consumer<ChatClient.AdvisorSpec>>getArgument(0).accept(advisorSpec);
                    return requestSpec;
                });
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("""
                {"mode":"REACT","confidence":0.8,"reason":"普通问答"}
                """);
        AgentModeRouter router = new AgentModeRouter(chatClient);

        AgentRouteDecision decision = router.route(request("s1", "解释一下"));

        assertEquals(AgentMode.REACT, decision.getMode());
        verify(advisorSpec).param(ChatMemory.CONVERSATION_ID, "agent:route:s1");
    }

    private MultiTurnChatRequest request(String sessionId, String message) {
        MultiTurnChatRequest request = new MultiTurnChatRequest();
        request.setSessionId(sessionId);
        request.setMessage(message);
        request.setTurnCount(1);
        return request;
    }
}