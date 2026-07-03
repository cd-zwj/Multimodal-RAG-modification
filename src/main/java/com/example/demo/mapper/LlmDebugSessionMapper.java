package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.model.llm.LlmDebugSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmDebugSessionMapper extends BaseMapper<LlmDebugSession> {
}
