package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.model.llm.LlmModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LlmModelConfigMapper extends BaseMapper<LlmModelConfig> {

    @Select("SELECT * FROM llm_model_config WHERE status = 'ENABLED' ORDER BY sort_order, model_code")
    List<LlmModelConfig> selectEnabledModels();

    @Select("SELECT * FROM llm_model_config WHERE model_code = #{modelCode} LIMIT 1")
    LlmModelConfig selectByModelCode(@Param("modelCode") String modelCode);

    @Select("SELECT * FROM llm_model_config ORDER BY sort_order, model_code")
    List<LlmModelConfig> selectAllModels();
}
