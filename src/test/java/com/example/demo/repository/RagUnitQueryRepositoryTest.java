package com.example.demo.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.demo.mapper.RagUnitMapper;
import com.example.demo.model.RagNodeType;
import com.example.demo.model.RagUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagUnitQueryRepositoryTest {

    @Mock
    private RagUnitMapper ragUnitMapper;

    @Test
    void searchLeafUnitsByKeyword_usesFullTextResultsBeforeLikeFallback() {
        RagUnit unit = leaf("leaf-1");
        when(ragUnitMapper.searchLeafUnitsByFullText("鼻鼽", "u1", 5)).thenReturn(List.of(unit));
        RagUnitQueryRepository repository = new RagUnitQueryRepository(ragUnitMapper);

        List<RagUnit> results = repository.searchLeafUnitsByKeyword(" 鼻鼽 ", "u1", 5);

        assertEquals(List.of(unit), results);
        verify(ragUnitMapper).searchLeafUnitsByFullText("鼻鼽", "u1", 5);
        verify(ragUnitMapper, never()).selectList(any(QueryWrapper.class));
    }

    @Test
    void searchLeafUnitsByKeyword_fallsBackToLikeWhenFullTextUnavailable() {
        RagUnit unit = leaf("leaf-2");
        when(ragUnitMapper.searchLeafUnitsByFullText("长度外推", "u1", 10))
                .thenThrow(new RuntimeException("fulltext index missing"));
        when(ragUnitMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(unit));
        RagUnitQueryRepository repository = new RagUnitQueryRepository(ragUnitMapper);

        List<RagUnit> results = repository.searchLeafUnitsByKeyword("长度外推", "u1", 10);

        assertSame(unit, results.get(0));
        verify(ragUnitMapper).searchLeafUnitsByFullText("长度外推", "u1", 10);
        verify(ragUnitMapper).selectList(any(QueryWrapper.class));
    }

    private static RagUnit leaf(String id) {
        RagUnit unit = new RagUnit();
        unit.setId(id);
        unit.setNodeType(RagNodeType.LEAF);
        unit.setUserId("u1");
        return unit;
    }
}
