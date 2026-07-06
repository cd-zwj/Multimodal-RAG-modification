package com.example.demo.service;

import com.example.demo.mapper.RagUnitMapper;
import com.example.demo.model.dto.FileDeleteTask;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentDeleteServiceTest {

    @Test
    void shouldRejectDeleteStatusWhenTaskBelongsToAnotherUser() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("delete:task:task-1")).thenReturn(FileDeleteTask.builder()
                .taskId("task-1")
                .userId("owner")
                .fileHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .build());
        DocumentDeleteService service = new DocumentDeleteService(
                mock(RagUnitMapper.class),
                mock(FileDeleteProducer.class),
                redisTemplate,
                mock(DocumentFileService.class)
        );

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.getDeleteStatus("task-1", "other"));

        assertEquals("任务不存在或已过期: task-1", error.getMessage());
    }
}
