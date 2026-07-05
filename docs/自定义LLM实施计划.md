# 自定义 LLM 接入与在线调试实施计划

## 1. 目标

实现一套可配置、可调试、可统一管理的 LLM 接入能力，满足以下核心需求：

- 支持用户自定义 `URL`、`API Key`、请求格式
- 支持在线调试，能够查看请求、响应、耗时和错误信息
- 配置创建成功后可被系统统一注册和调用
- 创建信息持久化到数据库
- 运行时具备缓存能力，但不以静态共享 `Map` 作为唯一数据源

## 2. 总体结论

当前思路的大方向是正确的，但需要做两点调整：

1. 数据库应作为配置主数据源，运行时内存注册表只作为缓存层
2. 不建议一开始就完全开放“任意请求格式”，应优先支持 `OpenAI-compatible`，再补充通用 `HTTP Adapter`

推荐采用：

- `数据库配置中心`
- `运行时 Registry + Cache`
- `统一调用接口`
- `在线调试会话记录`

## 3. 范围定义

### 3.1 第一阶段范围

第一阶段只实现文本对话模型接入，不扩展到：

- Embedding
- Rerank
- Function Calling / Tools
- 多模型路由与自动 fallback

第一阶段需要支持：

- 新增 LLM 配置
- 修改 LLM 配置
- 启用 / 停用配置
- 在线调试
- 统一查询可用模型
- 统一调用指定模型

### 3.2 第二阶段扩展

第二阶段再考虑：

- 多模型路由
- fallback 策略
- 租户级隔离
- 限流配额
- Embedding / Rerank 配置

## 4. 设计原则

### 4.1 数据库为准，缓存为辅

配置成功创建后，必须先落库，再同步到运行时注册中心。

不建议只使用静态共享 `Map`，原因如下：

- 多实例部署时无法同步
- 应用重启后内存丢失
- 配置变更无法可靠刷新
- 无法做审计、版本回滚和权限追踪

正确方式：

- 数据库存储完整配置
- 应用启动时加载启用配置
- 运行时用 `ConcurrentHashMap` / `Caffeine` 做本地缓存
- 配置变更时主动刷新缓存

### 4.2 协议优先级

推荐按两层协议实现：

1. `OPENAI_COMPATIBLE`
2. `GENERIC_HTTP`

原因：

- 大部分第三方模型服务兼容 OpenAI 协议
- Spring AI 对 OpenAI 风格接入支持成熟
- 非标准格式只在确实需要时走通用适配器，降低第一版复杂度

### 4.3 调试链路独立

在线调试不应只是一个“测试是否成功”的按钮，而应该记录完整调试会话：

- 输入参数
- 渲染后的最终请求
- 脱敏后的请求头
- 原始响应
- 解析结果
- HTTP 状态码
- 耗时
- 错误分类

## 5. 架构方案

## 5.0 技术栈约束

为避免实现歧义，第一版技术选型明确如下：

- Java: `Java 21`
- Spring Boot: 当前项目现有版本
- HTTP 客户端: `Spring WebClient`
- JSON 处理: `Jackson`
- 响应式流处理: `Project Reactor`
- SSE / 流式输出: `ServerSentEvent + Flux`

选择 `WebClient` 的原因：

- 当前项目已经使用 `Flux`
- 更适合处理流式响应
- 更容易统一超时、鉴权头、错误映射和日志拦截

第一版不使用：

- `RestTemplate`
- `OkHttp` 作为主实现

说明：

- 若后续确有特殊兼容性问题，可在 `Generic HTTP` 适配层内部替换实现，但对上仍保持统一接口

## 5.1 逻辑分层

建议拆成以下模块：

- `Config Management`
  - 配置增删改查
  - 启停
  - 版本管理
- `Registry`
  - 启动加载
  - 内存缓存
  - 配置刷新
- `Client Factory`
  - 根据协议类型动态创建客户端
- `Debug Service`
  - 构造调试请求
  - 执行调用
  - 记录调试结果
- `Unified Llm Service`
  - 对上层业务暴露统一调用接口

## 5.2 推荐运行链路

### 配置创建链路

1. 前端提交配置
2. 后端校验字段合法性
3. 后端加密保存密钥
4. 持久化到数据库
5. 刷新运行时注册中心
6. 返回配置详情

### 正式调用链路

1. 上层业务传入 `providerCode` 或 `modelCode`
2. `Registry` 获取当前启用配置
3. `Factory` 构建对应客户端
4. 发起请求
5. 返回统一响应结构

### 在线调试链路

1. 用户选择配置
2. 输入测试消息和可选参数
3. 后端渲染请求模板
4. 执行真实 HTTP 调用
5. 记录调试会话
6. 返回调试结果

## 6. 数据模型设计

至少拆成三张核心表。

## 6.1 `llm_provider_config`

用于存储接入配置主信息。

建议字段：

- `id`
- `provider_code`
- `provider_name`
- `protocol_type`
- `base_url`
- `api_key_ciphertext`
- `default_headers_json`
- `request_template_json`
- `response_mapping_json`
- `stream_config_json`
- `connect_timeout_ms`
- `read_timeout_ms`
- `status`
- `version`
- `remark`
- `created_by`
- `created_at`
- `updated_by`
- `updated_at`

说明：

- `protocol_type` 取值建议为 `OPENAI_COMPATIBLE` / `GENERIC_HTTP`
- `version` 用于缓存刷新和并发更新控制
- `status` 用于启停控制
- `stream_config_json` 用于定义是否流式、流式事件格式和解析规则

## 6.2 `llm_model_config`

用于存储模型级配置。

建议字段：

- `id`
- `provider_id`
- `model_code`
- `display_name`
- `remote_model_name`
- `default_params_json`
- `capabilities_json`
- `status`
- `sort_order`
- `created_at`
- `updated_at`

说明：

- 一个 provider 下可以挂多个模型
- `default_params_json` 保存温度、topP、maxTokens 等默认参数
- `capabilities_json` 可标记 `chat`、`stream`、`json_mode` 等能力

## 6.3 `llm_debug_session`

用于存储调试记录。

建议字段：

- `id`
- `provider_id`
- `model_id`
- `debug_request_json`
- `resolved_request_json`
- `masked_headers_json`
- `raw_response_text`
- `parsed_response_json`
- `http_status`
- `latency_ms`
- `success`
- `error_code`
- `error_message`
- `created_by`
- `created_at`

说明：

- `resolved_request_json` 表示模板渲染后的最终请求
- `raw_response_text` 便于定位解析失败问题
- `masked_headers_json` 只保留脱敏后的请求头

## 7. 运行时注册中心设计

## 7.1 不建议的方案

不建议直接使用“统一静态共享 `Map`”作为唯一注册方式：

```text
static Map<String, Object> PROVIDERS = new ConcurrentHashMap<>();
```

问题：

- 只适合单机
- 变更不可追踪
- 难以做配置回滚
- 无法跨节点一致刷新

## 7.2 推荐方案

实现 `LlmProviderRegistry`：

- 启动时从数据库加载所有启用配置
- 将配置构造成运行时对象
- 按 `providerCode`、`modelCode` 建立索引
- 本地缓存放入 `ConcurrentHashMap`

建议缓存结构：

- `providerCode -> RuntimeProviderDefinition`
- `modelCode -> RuntimeModelDefinition`

其中：

- `RuntimeProviderDefinition` 保存 provider 基础配置和解密后的运行时参数
- `RuntimeModelDefinition` 保存模型默认参数和能力标签

## 7.3 刷新机制

配置变更后，必须触发注册中心刷新。

第一版可用：

- 更新数据库后直接刷新当前应用实例缓存

后续多实例版可扩展：

- Redis Pub/Sub
- RabbitMQ 广播
- 定时增量刷新

## 8. 客户端工厂设计

建议实现 `LlmClientFactory`，按协议类型创建客户端。

### 8.1 OpenAI Compatible

适用于：

- OpenAI 风格接口
- 兼容 OpenAI Chat Completions 的第三方平台

实现建议：

- 优先复用 Spring AI OpenAI Chat 接入能力
- 支持动态 `baseUrl`
- 支持动态 `apiKey`
- 支持动态默认参数
- 支持普通响应和流式响应两种调用方式

### 8.2 Generic HTTP

适用于：

- 非标准接口
- 请求体字段不符合 OpenAI 规范
- 响应结构需要自定义解析

实现建议：

- 使用统一 HTTP Client
- `request_template_json` 负责拼装请求
- `response_mapping_json` 负责提取最终文本

## 8.3 模板规范

第一版模板规范必须固定，不允许前后端各自理解。

### 请求模板渲染规则

- 模板格式：`Handlebars` 风格占位符
- 变量语法：`{{variable}}`
- 嵌套变量：`{{params.temperature}}`
- 不支持任意脚本执行
- 不支持运行时表达式求值

不采用：

- `${message}`
- JSONPath 作为请求模板语言
- 自定义脚本

原因：

- `{{variable}}` 更直观
- 前后端容易统一
- 调试面板可直接预览渲染结果
- 安全边界更清晰

### 第一版可用变量

- `message`
- `systemPrompt`
- `model`
- `stream`
- `params.temperature`
- `params.topP`
- `params.maxTokens`
- `context.userId`
- `context.sessionId`

### `request_template_json` 示例

```json
{
  "model": "{{model}}",
  "messages": [
    {
      "role": "system",
      "content": "{{systemPrompt}}"
    },
    {
      "role": "user",
      "content": "{{message}}"
    }
  ],
  "temperature": "{{params.temperature}}",
  "stream": "{{stream}}"
}
```

说明：

- 渲染后再做 JSON 类型矫正
- 例如 `"true"` 要转成 `true`
- 数字字符串要转成数值类型

## 8.4 响应映射规范

第一版 `response_mapping_json` 不使用复杂脚本，使用“字段路径 + 模式配置”。

建议字段：

- `mode`
- `contentPath`
- `finishReasonPath`
- `errorMessagePath`
- `streamChunkPath`
- `streamDoneFlagPath`

其中：

- 普通响应路径格式采用点路径，如 `choices.0.message.content`
- 数组下标使用数字索引
- 不引入完整 JSONPath 语法

原因：

- 实现成本更低
- 可读性更高
- 前端配置更简单

### 普通响应映射示例

```json
{
  "mode": "JSON",
  "contentPath": "choices.0.message.content",
  "finishReasonPath": "choices.0.finish_reason",
  "errorMessagePath": "error.message"
}
```

### SSE 响应映射示例

```json
{
  "mode": "SSE",
  "streamChunkPath": "choices.0.delta.content",
  "finishReasonPath": "choices.0.finish_reason",
  "streamDoneFlagPath": "[DONE]"
}
```

说明：

- `mode=JSON` 表示一次性响应
- `mode=SSE` 表示服务端事件流
- `streamDoneFlagPath` 用于识别流终止标记
- 若服务商返回自定义结束标志，可在此配置

## 9. 在线调试设计

## 9.1 调试目标

在线调试至少解决三个问题：

1. 当前配置能不能连通
2. 当前请求模板是否正确
3. 当前响应解析规则是否正确

## 9.2 调试输入

前端调试面板建议支持：

- 测试消息
- 可选系统提示词
- 可选模型参数覆盖
- 可选自定义变量
- 是否开启流式调试

## 9.3 调试输出

后端返回：

- 渲染后的最终请求体
- 脱敏后的请求头
- 原始响应
- 解析结果
- HTTP 状态码
- 耗时
- 错误原因

如果是流式调试，还应额外返回：

- 流事件列表
- 已拼接文本
- 首 token 时间
- 完成时间

## 9.4 流式调试设计

第一版必须明确支持 SSE 流式调试。

### 后端处理方式

- 使用 `WebClient` 订阅流式响应
- 按事件逐条解析
- 将每个 chunk 写入调试会话缓存对象
- 结束后再持久化完整调试记录

### 前端展示方式

在线调试面板在流式模式下应包含三块区域：

1. `实时输出区`
   - 按 token / chunk 逐步追加文本
2. `事件流区`
   - 展示每条 SSE event / data
3. `最终汇总区`
   - 展示拼接结果、耗时、状态、结束原因

### 第一版是否需要“实时 token 流”

需要。

原因：

- 否则流式调试和非流式调试没有本质差异
- 无法判断服务商是否真正支持 streaming
- 无法定位卡顿、首包慢、半路断流等问题

### 流式调试接口建议

可保留两种方案：

1. `POST /llm/debug`
   - 非流式，返回完整结果
2. `POST /llm/debug/stream`
   - 流式，SSE 返回调试事件

如果只保留一个接口，则建议由请求参数 `stream=true/false` 控制。

## 9.5 错误分类

建议统一错误分类：

- `CONFIG_ERROR`
- `AUTH_ERROR`
- `NETWORK_ERROR`
- `TIMEOUT_ERROR`
- `PROTOCOL_ERROR`
- `PARSE_ERROR`
- `REMOTE_SERVICE_ERROR`

这样便于前端展示，也便于后续统计。

## 9.6 错误分类与 HTTP 状态映射

为避免实现分歧，建议统一映射规则如下：

| HTTP 状态 / 场景 | 业务错误分类 |
| --- | --- |
| 400 | `CONFIG_ERROR` 或 `PROTOCOL_ERROR` |
| 401 / 403 | `AUTH_ERROR` |
| 404 | `CONFIG_ERROR` |
| 408 | `TIMEOUT_ERROR` |
| 429 | `REMOTE_SERVICE_ERROR` |
| 500-599 | `REMOTE_SERVICE_ERROR` |
| 网关超时 / 读取超时 / Reactor timeout | `TIMEOUT_ERROR` |
| DNS 失败 / 连接拒绝 / TLS 建连失败 | `NETWORK_ERROR` |
| 响应体无法解析 | `PARSE_ERROR` |
| 响应结构不符合配置映射 | `PROTOCOL_ERROR` |

补充规则：

- 后端自身参数校验失败返回 `400`
- 未授权访问调试接口返回 `403`
- provider 被停用返回 `409`
- 配置不存在返回 `404`

## 9.7 调试记录详情页建议

调试记录详情页建议固定展示以下内容：

- 基本信息
  - provider
  - model
  - 调试人
  - 调试时间
- 请求信息
  - 原始输入
  - 渲染后请求
  - 脱敏请求头
- 响应信息
  - 原始响应
  - 解析结果
  - 流式事件列表
- 性能信息
  - HTTP 状态码
  - 总耗时
  - 首 token 时间
- 错误信息
  - 错误分类
  - 错误消息

## 10. 对外接口设计

建议第一版至少提供以下接口。

### 配置管理

- `POST /llm/providers`
  - 新增 provider
- `PUT /llm/providers/{id}`
  - 更新 provider
- `GET /llm/providers/{id}`
  - 查询详情
- `GET /llm/providers`
  - 分页查询
- `POST /llm/providers/{id}/enable`
  - 启用
- `POST /llm/providers/{id}/disable`
  - 停用

### 模型管理

- `POST /llm/models`
  - 新增模型配置
- `PUT /llm/models/{id}`
  - 更新模型配置
- `GET /llm/models`
  - 查询模型列表

### 调试接口

- `POST /llm/debug`
  - 在线调试指定 provider / model
- `POST /llm/debug/stream`
  - 流式调试指定 provider / model
- `GET /llm/debug/{id}`
  - 查看调试详情

### 统一调用接口

- `POST /llm/chat`
  - 对上层业务提供统一调用入口

## 11. 安全方案

## 11.1 密钥存储

API Key 不允许明文入库。

推荐方式：

- 数据库存储密文
- 主密钥放环境变量
- 页面只返回掩码值
- 调试日志中不回显真实密钥

## 11.2 权限控制

配置管理和调试接口应具备权限隔离：

- 只有管理员可以新增 / 编辑 / 启停
- 普通用户不能查看真实密钥
- 调试接口建议仅授权内部运营或管理员

## 11.3 审计日志

必须记录：

- 谁创建了配置
- 谁修改了配置
- 谁启用了配置
- 谁执行了调试

## 11.4 限流

调试接口建议单独限流，因为它会直接访问外部模型服务。

建议至少按以下维度限流：

- 用户级
- IP 级
- provider 级

## 12. 测试计划

第一版至少覆盖以下测试。

### 单元测试

- DTO 参数校验
- 请求模板渲染
- 响应映射解析
- 密钥加解密
- Registry 刷新逻辑

### 集成测试

- 新增配置后可被查询
- 启用配置后可进入注册中心
- 调试接口能正确记录调试会话
- 停用配置后统一调用不可用

### 回归测试

- 不影响现有 AI 能力
- 不影响已有 Spring AI 调用链路

## 13. 实施顺序

建议严格按以下顺序推进。

### 第一阶段：后端最小闭环

1. 建表
2. 实体 / DTO / Mapper
3. 配置管理接口
4. `LlmProviderRegistry`
5. `LlmClientFactory`
6. 统一调用接口

### 第二阶段：在线调试

1. 调试 DTO
2. 调试服务
3. 调试记录表写入
4. 调试接口

### 第三阶段：前端页面

1. 配置列表页
2. 新增 / 编辑表单
3. 在线调试面板
4. 调试结果展示

## 13.1 前端交互细化

### 配置列表页

需要支持：

- 按 provider 名称搜索
- 按协议类型筛选
- 按启用状态筛选
- 快速启用 / 停用
- 进入调试
- 查看最近一次调试结果

### 新增 / 编辑表单

表单按协议类型动态切换字段：

- `OPENAI_COMPATIBLE`
  - `baseUrl`
  - `apiKey`
  - `model`
  - 默认参数
  - 是否支持流式
- `GENERIC_HTTP`
  - `url`
  - `method`
  - `headers`
  - `requestTemplate`
  - `responseMapping`
  - 流式配置

必须提供：

- JSON 校验
- 模板预览
- 映射规则校验

### 在线调试面板

调试面板建议拆成四栏：

1. 左侧配置栏
   - 选择 provider / model
   - 选择是否流式
   - 输入消息与参数
2. 中间请求栏
   - 展示原始输入
   - 展示渲染后的请求体
3. 中间响应栏
   - 非流式时展示完整响应
   - 流式时实时展示 token / chunk
4. 右侧历史栏
   - 展示最近调试记录
   - 点击后可回看详情并对比

### 历史调试记录交互

建议支持：

- 最近 N 条记录列表
- 成功 / 失败状态标记
- 点击后展开详情
- 与当前调试结果进行左右对比

### 流式返回展示建议

流式模式下，前端应明确区分三类信息：

- `增量文本`
- `原始 SSE 事件`
- `最终汇总结果`

不建议只显示“最后完整文本”，否则无法体现调试价值。

### 第四阶段：增强能力

1. 多实例缓存刷新
2. fallback / routing
3. 模型能力标签扩展
4. 配额与成本统计

## 14. 第一版验收标准

满足以下条件即可认为第一版完成：

- 可以新增一条 OpenAI-compatible 模型配置
- 配置可持久化到数据库
- 配置启用后可被系统统一调用
- 在线调试可返回响应结果
- 调试记录可查询
- API Key 不明文存储
- 配置修改后注册中心可刷新

## 15. 当前建议的最终方案

结合当前项目现状，建议采用以下落地路径：

1. 第一版只做 `OPENAI_COMPATIBLE + GENERIC_HTTP`
2. 配置主数据全部落库
3. 内存 `Registry` 只做运行时缓存
4. 在线调试单独建调试记录表
5. 上层业务统一走 `CustomLlmService`

这样做的优点是：

- 架构清晰
- 便于后续扩展
- 对现有系统侵入较小
- 调试能力完整
- 多实例演进路径明确

---

如果下一步进入实现阶段，建议先从：

1. 数据表 SQL
2. Java 实体与 DTO
3. `LlmProviderRegistry`
4. `POST /llm/providers`
5. `POST /llm/debug`

这五部分开始。
