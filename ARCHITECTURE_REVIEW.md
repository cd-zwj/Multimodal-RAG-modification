# RAG Demo 项目架构改进建议

> 基于代码审查，按优先级从高到低排列

---

## 一、安全 — 最紧迫的问题

### 1.1 凭据硬编码 ✅ 已修复

~~`application.yaml` 中所有敏感凭据均为明文存储，一旦推送到 Git 即泄露。~~

**已采用方案**：创建 `application-dev.yaml` 存放全部真实凭据，`application.yaml` 通过 `${_app.*}` 占位符引用，`application-dev.yaml` 已加入 `.gitignore` 不提交。

```yaml
# application-dev.yaml（不提交 Git）
_app:
  dashscope:
    api-key: sk-xxxx
  datasource:
    password: xxx

# application.yaml（提交 Git）
spring:
  ai:
    dashscope:
      api-key: ${_app.dashscope.api-key}
  datasource:
    password: ${_app.datasource.password}
```

> 后续仍建议轮换已暴露在 Git 历史中的旧密钥。

### 1.2 错误信息泄露

`GlobalExceptionHandler` 第 48 行直接将内部异常信息返回前端：

```java
// 当前代码
return ApiResponse.serverError("服务器繁忙，处理超时，请稍后再试. 错误详情: " + msg);
```

生产环境会暴露堆栈信息、SQL 语句、内部路径等敏感内容。

**建议**：前端只返回通用提示，详细错误仅写日志：

```java
@ExceptionHandler(Exception.class)
public ApiResponse<Object> handleException(Exception e) {
    String traceId = MDC.get("traceId"); // 配合链路追踪
    log.error("系统未处理的异常 [traceId={}]", traceId, e);
    return ApiResponse.serverError("服务器繁忙，请稍后再试");
}
```

### 1.3 无 CSRF / CORS 配置

项目使用 Sa-Token 做认证，但没有 Spring Security 依赖，也未配置 CORS 策略。任何来源都可以调用 API。

**建议**：在 `WebMvcConfig` 中配置 CORS 白名单，限制允许的 Origin。

---

## 二、分层与包结构

### 2.1 包命名不规范

当前 `Controller` 包名大写，不符合 Java 惯例：

```
src/main/java/com/example/demo/
├── Controller/        ← 应为 controller（小写）
├── Config/            ← 应为 config
├── model/
├── mapper/
├── service/
└── exception/
```

### 2.2 职责混乱

| 问题 | 位置 |
|------|------|
| `DateTimeTools` 是 AI 工具类，不是配置 | 放在 `Config/` 包下 |
| `SessionManager` 管理会话状态，属于基础设施 | 放在 `Config/` 包下 |
| `RedisMessage` 是消息模型，不是配置 | 放在 `Config/` 包下 |
| DTO 和实体混在 `model/` 下 | 没有区分 DTO 和领域实体 |

**建议**：按领域职责重新组织：

```
com.example.demo
├── controller/          # 入口层（HTTP）
├── application/         # 编排层（ApplicationService）
├── domain/
│   ├── rag/             # RAG 核心逻辑
│   │   ├── retrieval/
│   │   ├── indexing/
│   │   └── chunking/
│   ├── auth/            # 认证授权
│   └── document/        # 文档管理
├── infrastructure/      # 外部集成
│   ├── dashscope/
│   ├── minio/
│   ├── asr/
│   └── mineru/
├── config/              # 纯配置类
├── dto/                 # 数据传输对象
└── common/              # 通用工具与异常
```

### 2.3 Service 层过大

| 类 | 行数 | 问题 |
|----|------|------|
| `RagRetrievalService` | ~790 行 | 同时负责层次检索、平铺检索、多路召回、关键词回退、引文构建 |
| `AiService` | ~283 行 | 同时负责单轮对话、多轮对话、会话管理、引文格式化、SSE 事件构建 |

**建议**：

- `RagRetrievalService` 拆为 `HierarchicalRetriever`、`FlatRetriever`、`KeywordRetriever`、`RerankService`
- `AiService` 拆为 `ChatService`（对话逻辑）和 `SessionApplicationService`（会话管理）

---

## 三、输入校验

### 3.1 手工校验代替框架校验

`RagDocumentApplicationService` 中大量手工判断：

```java
// 当前代码
if (page < 1) {
    return ApiResponse.validationError("页码必须大于 0");
}
if (pageSize < 1 || pageSize > 100) {
    return ApiResponse.validationError("每页大小必须在 1-100 之间");
}
```

**建议**：使用 Jakarta Bean Validation：

```java
// DTO 上加约束
public class PageRequest {
    @Min(value = 1, message = "页码必须大于 0")
    private Integer page;

    @Range(min = 1, max = 100, message = "每页大小必须在 1-100 之间")
    private Integer pageSize;

    @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "无效的 SHA-256 哈希值")
    private String fileHash;
}

// Controller 层启用
public ApiResponse<?> getDocuments(@Valid PageRequest request) { ... }
```

---

## 四、依赖风险

### 4.1 `hutool-all` 过重

`pom.xml` 引入了 `hutool-all`，会拉入 hutool 全部模块（~3MB+）。实际使用范围有限。

**建议**：替换为精确依赖：

```xml
<!-- 只引入实际使用的模块 -->
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-core</artifactId>
    <version>5.8.22</version>
</dependency>
<!-- hutool-json, hutool-crypto 等按需添加 -->
```

### 4.2 `javacv-platform` 拉取全平台二进制

`javacv-platform` 会下载 Linux/Windows/macOS 全部 native 库，导致构建产物体积巨大（数百 MB）。

**建议**：用 classifier 指定目标平台：

```xml
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>javacv-platform</artifactId>
    <version>1.5.10</version>
    <classifier>windows-x86_64</classifier> <!-- 按部署环境选择 -->
</dependency>
```

### 4.3 javax vs jakarta 冲突风险

pom.xml 引入了 `javax.xml.bind:jaxb-api:2.3.1`，但 Spring Boot 3 使用 Jakarta 命名空间。需要确认 Tika/POI 是否真的依赖 javax JAXB，如有冲突应替换为 `jakarta.xml.bind-api`。

---

## 五、异步与可靠性

### 5.1 外部调用无熔断/重试

DashScope、MinerU、阿里云 ASR 三个外部 API 均为裸调用，无任何保护。任一外部服务故障会直接拖垮整个应用。

**建议**：引入 Resilience4j：

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

```java
@CircuitBreaker(name = "dashscope", fallbackMethod = "chatFallback")
@Retry(name = "dashscope")
public String chat(String msg, String userId) { ... }

private String chatFallback(String msg, String userId, Throwable t) {
    return "服务暂时不可用，请稍后再试";
}
```

配合 `application.yaml`：

```yaml
resilience4j:
  circuitbreaker:
    instances:
      dashscope:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

### 5.2 ObjectMapper 重复创建

`AiService.multiTurnChat()` 第 180 行每次请求都 new 一个 ObjectMapper：

```java
citationsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(citations);
```

**建议**：注入 Spring 管理的单例：

```java
private final ObjectMapper objectMapper;

// 或通过构造器注入
public AiService(..., ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
}
```

### 5.3 Jedis 同步阻塞 vs Lettuce 异步

项目使用 Jedis（同步阻塞 I/O），但聊天接口是流式 `Flux<ServerSentEvent>`。高并发时 Jedis 会因线程池耗尽而阻塞。

**建议**：切换到 Lettuce（Spring Boot 默认），或保持 Jedis 但确保连接池足够大并监控等待时间。

---

## 六、可观测性

当前只有 `@Slf4j` + Actuator，缺少关键的可观测性基础设施：

### 6.1 链路追踪

一次聊天请求涉及：查询改写 → 子查询生成 → 向量检索 → Rerank → LLM 调用，没有 Trace ID 无法串联日志。

**建议**：引入 Micrometer Tracing + Zipkin：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

所有日志自动携带 `traceId`，方便跨服务排查。

### 6.2 业务指标

RAG 系统的调优依赖数据，建议埋点：

| 指标 | 用途 |
|------|------|
| 检索命中率（hit/miss） | 判断知识库覆盖度 |
| Rerank 前后分数分布 | 调整 `hit-score-threshold` |
| 层次检索 vs 平铺回退比例 | 验证层次索引效果 |
| 平均检索耗时 | 优化性能瓶颈 |
| LLM 首 Token 延迟（TTFT） | 优化用户体验 |

使用 Micrometer + Prometheus + Grafana 搭建监控面板。

### 6.3 外部调用监控

DashScope/MinIO/ASR 调用的成功率、延迟、错误类型需要单独监控，便于快速定位是"自己的问题"还是"第三方的问题"。

---

## 七、测试

### 7.1 现状

当前测试文件仅 6 个，且 `DemoApplicationTests` 为空壳。核心检索和对话逻辑缺乏测试覆盖。

### 7.2 优先级排序

| 优先级 | 测试目标 | 理由 |
|--------|---------|------|
| P0 | `RagRetrievalService` | 层次检索 fallback 逻辑复杂，分支多，最容易出 bug |
| P0 | `AiService.multiTurnChat` | 引文构建、SSE 事件组装直接影响前端展示 |
| P1 | `QueryRewriteService` | 查询改写质量直接影响检索效果 |
| P1 | `RetrievalSubQueryService` | 子查询生成质量 |
| P2 | 文档处理链路（Processor → Chunker → Indexing） | 完整的文档入库流程 |
| P2 | 认证授权 | 权限校验逻辑 |

### 7.3 缺失的关键集成测试

需要一条端到端测试链路覆盖：

```
文档上传 → 格式解析 → 文本切片 → 向量化入库 → 检索召回 → Rerank → LLM 生成回答
```

---

## 八、配置管理

### 8.1 缺少多环境配置

所有配置集中在单个 `application.yaml`，开发/测试/生产共用一套配置。

**建议**：

```
application.yaml            # 公共配置
application-dev.yaml        # 开发环境
application-test.yaml       # 测试环境
application-prod.yaml       # 生产环境（凭据用环境变量）
```

### 8.2 API 无版本号

所有接口路径如 `/ai/chat`、`/ai/session/list` 没有版本前缀。接口变更时无法做灰度兼容。

**建议**：统一加 `/api/v1/` 前缀。

### 8.3 `hutool-all` 等大依赖

（已在第四节详述，此处不重复）

---

## 九、改进行动清单

按紧急程度排序：

### 立即行动（安全红线）

- [ ] 凭据外部化（环境变量），并轮换已泄露的密钥
- [ ] 修复 `GlobalExceptionHandler` 的错误信息泄露
- [ ] 配置 CORS 白名单

### 短期改进（1-2 周）

- [ ] 接入 Resilience4j 熔断器
- [ ] 用 Bean Validation 替换手工校验
- [ ] 注入单例 `ObjectMapper`
- [ ] 拆分 `hutool-all` 为精确依赖
- [ ] 添加链路追踪（Micrometer Tracing）

### 中期优化（1-2 月）

- [ ] 重构包结构（controller/service/domain/infrastructure）
- [ ] 拆分过大的 Service 类
- [ ] 补充核心模块单元测试
- [ ] 建立 RAG 调优指标监控面板
- [ ] 拆分多环境配置
- [ ] API 路径加版本号

### 长期演进

- [ ] 考虑 Jedis → Lettuce 迁移
- [ ] 视频/ASR 处理异步化，评估是否需要拆分微服务
- [ ] 引入 OpenAPI/Swagger 自动生成 API 文档
- [ ] Docker 化部署（Dockerfile + docker-compose）
