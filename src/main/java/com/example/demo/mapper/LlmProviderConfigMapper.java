package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.model.llm.LlmProviderConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LlmProviderConfigMapper extends BaseMapper<LlmProviderConfig> {

    @Select("SELECT * FROM llm_provider_config WHERE status = 'ENABLED' ORDER BY provider_code")
    List<LlmProviderConfig> selectEnabledProviders();

    @Select("SELECT * FROM llm_provider_config ORDER BY provider_code")
    List<LlmProviderConfig> selectAllProviders();

    @Select("SELECT * FROM llm_provider_config WHERE provider_code = #{providerCode} LIMIT 1")
    LlmProviderConfig selectByProviderCode(@Param("providerCode") String providerCode);
}
