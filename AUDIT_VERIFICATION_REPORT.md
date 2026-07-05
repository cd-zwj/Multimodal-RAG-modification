# 审计问题验证报告

> 生成日期: 2026-06-14
> 验证范围: D:\rag\demo 全量源码 (含 frontend/)
> 审计来源: 第三方代码审计报告（S/P/C/A/Q/B/F/T 分类）

---

## 验证结论总览

| 验证结果 | 数量 | 占比 |
|----------|------|------|
| ✅ 完全确认 | 12 | 46% |
| ⚠️ 部分确认（严重度低于报告所述） | 4 | 15% |
| ❌ 不确认（问题不存在） | 2 | 8% |
| 🔍 需运行时验证 | 1 | 4% |
| 📝 风格/技术债务（确认但不紧急） | 5 | 19% |
| 🟢 代码中无此问题 | 1 | 4% |

**26 个问题中 18 个存在实际风险。3 个最高优修复项：S-2、P-3、P-7。**

---

## 🔴 严重 — 安全漏洞

### S-2: Actuator 端点未鉴权

**结论**: ⚠️ 部分确认（风险被夸大）

| 项目 | 状态 |
|------|------|
| `/actuator/**` 在 SaToken 排除列表中 | ✅ 确认 — SaTokenConfig.java:41 |
| `show-details: always` | ✅ 确认 — application.yaml:237 |
| "可泄露环境变量和内部配置" | ❌ 夸大 — 未配置 `management.endpoints.web.exposure.include`，Spring Boot 3.x 默认仅暴露 `health` 端点。不会泄露 `env`/`configprops`/`beans` |

**修复建议**: 限缩暴露为 `health,info,metrics`，设置 `show-details: when_authorized`。

**实际风险等级**: 🔴 → 🟠（仅 health 详情泄露，非完整配置泄露）

---

### S-3: userId 作为请求参数传递

**结论**: ⚠️ 部分确认（严重度低于报告所述）

| 文件 | 行号 | 说明 |
|------|------|------|
| AiController.java | 39 | `userId` 作为 `@RequestParam` 传入 |
| UploadController.java | 56-57 | `userId` 作为 `@RequestParam` 传入 |

**关键缓解因素**: 每个端点都调用 `authContextService.resolveUserId(userId)`，该方法（AuthContextService.java:13-22）会校验传入的 userId 与 Token 中的当前用户是否一致，不匹配则抛出 `IllegalArgumentException`。

这是"可选参数 + 服务端强制校验"模式，而非真正的"客户端可伪造 userId"漏洞。但最佳实践仍是从 Token 中直接派生，不在 API 契约中暴露 userId。

**实际风险等级**: 🔴 → 🟠

---

## 🟠 高优先级 — 数据完整性与性能

### P-1: "N+1 查询"（命名不准确）

**结论**: ⚠️ 部分确认 — 问题存在但命名有误

DocumentFileService.java:95-108 的 `updateStatus` 先通过 `requireByFileHash`（执行 SELECT）再调用 `updateById`（执行 UPDATE）。这是"读后写"模式，不是 N+1 问题（N+1 指循环内逐条查询）。

| 确认项 | SELECT + UPDATE 双重数据库往返 |
| N+1 表述 | ❌ 不准确 |
| 修复价值 | 中等 — 用 `UpdateWrapper` 可减少一次数据库往返 |

---

### P-2: 批量写入脱离事务

**结论**: 🔍 需运行时验证

RagUnitService.java:107-117 中 `saveBatch` 通过 `sqlSessionFactory.openSession(ExecutorType.BATCH)` 创建新会话。但方法注释（第 94-101 行）明确说明依赖 `SpringManagedTransactionFactory`，在 `TransactionTemplate` 回调中调用时自动参与外层 Spring 事务。

使用 `mybatis-plus-spring-boot3-starter` 时此设计理论上成立。建议通过集成测试验证失败场景下数据库和向量存储能正确回滚。

---

### P-3: 缺少 @Transactional

**结论**: ✅ 完全确认

DocumentFileService.java 中**确实没有任何** `@Transactional` 注解。

`createUploadingRecord`（先查询再插入/更新）和 `markUploadSuccess` 等方法在并发场景下可能出现状态不一致。`DocumentDeleteService` 有 `@Transactional`，但 `RagUnitService` 也没有，事务保护不完整。

---

### P-7: .join() 阻塞 Servlet 线程

**结论**: ✅ 完全确认

AiController.java:46 中 `aiService.chat(...).join()` 同步阻塞 Tomcat 线程。

注意：`multiTurnChat` 端点（第 109 行）已正确使用 `Flux` + SSE 非阻塞方式，问题仅限于老的 `/ai/chatmemory/chat` 端点。

---

## 🟡 中优先级 — 配置与架构

### C-1: HikariCP 无池大小配置

**结论**: ✅ 完全确认

`application.yaml` 中没有 `maximum-pool-size` 或 `minimum-idle` 配置，使用默认值 10 个连接。

---

### C-2: 缺少优雅停机

**结论**: ✅ 完全确认

未配置 `server.shutdown: graceful` 或 Spring 生命周期超时。

---

### C-3: 缺少 Jackson 日期配置

**结论**: ✅ 完全确认

未配置 `spring.jackson.date-format` 或 `spring.jackson.serialization.write-dates-as-timestamps`。`LocalDateTime` 默认序列化为时间戳数组（如 `[2026,6,14,10,30,0]`）。

---

### C-4: 关键词搜索用 LIKE '%xx%'

**结论**: ✅ 完全确认

RagUnitQueryRepository.java:91-95 对 `title`、`content`、`filename` 使用 MyBatis-Plus `.like()`，生成 `LIKE '%keyword%'`。前置 `%` 阻止 B-tree 索引使用，数据量大时会导致全表扫描。

---

### A-1: MinIO 下载用原生 HTTP

**结论**: ✅ 完全确认

FileProcessConsumer.java:97-113 使用 `java.net.URL` + `HttpURLConnection`，未使用 MinIO SDK 的认证和连接池。

---

### A-4: ChatMemory 循环依赖

**结论**: ❌ 不确认 — 此问题在当前代码中不存在

Aiconfig.java 中的依赖链是线性的：

```
chatClient → chatMemory → summaryChatClient → deepchat (ChatModel)
```

`SummaryWindowChatMemory` 仅依赖 `ChatClient`（用于摘要生成），不依赖 `ChatMemory`，不存在循环。代码中也无 `@Lazy` 注解。

---

## 🟢 低优先级 — 代码质量与测试

### Q-1/Q-2: 包名大写

**结论**: ✅ 确认（风格问题）

包名 `Config` 和 `Controller` 不符合 Java 包名应全小写的约定，但全库一致使用。

---

### Q-3/Q-4: 重复方法

**结论**: ✅ 完全确认

| 方法 | 出现位置 |
|------|----------|
| `validateFileHash` | RagUnitService.java:460 + DocumentDeleteService.java:121（实现略有不同） |
| `normalizeErrorMessage` | RagUnitService.java:475 + FileProcessConsumer.java:190（逻辑不同） |

---

### Q-5: RagUnitService 上帝类

**结论**: ✅ 完全确认

RagUnitService.java 共 500 行，9 个构造器依赖，混合了文件处理、文档管理、删除、向量存储写入等多个职责。

---

### Q-7: BCryptPasswordEncoder 重复创建

**结论**: ✅ 完全确认

| 文件 | 行号 |
|------|------|
| AuthAccountService.java | 29 |
| PasswordRecoveryService.java | 36 |

两个服务各自 `new BCryptPasswordEncoder()`，应注册为单个 `@Bean` 并注入。

---

### B-1: javacv-platform 依赖过重

**结论**: ✅ 完全确认

`pom.xml` 使用 `org.bytedeco:javacv-platform:1.5.10`，会下载所有平台的原生二进制文件（约 500MB-1GB）。建议改为平台特定依赖（如 `javacv` + `windows-x86_64` 等分类器）。

---

### F-3: 废弃 Web API createScriptProcessor

**结论**: ✅ 完全确认

Chat.vue:298 中使用了 Web Audio API 已废弃的 `ScriptProcessorNode`：

```js
processorNode = audioContext.createScriptProcessor(4096, 1, 1)
```

`ScriptProcessorNode` 已被 W3C 标记废弃，在主线程上运行音频处理回调，容易导致卡顿和丢帧。现代浏览器可能随时移除。应迁移到 `AudioWorkletNode`，使用独立的音频处理线程。

> **验证备注**: 初次搜索仅覆盖 Java `src/` 目录，遗漏了 Vue 前端 `frontend/` 目录。经指正后确认此问题存在。

---

### T-1~T-4: 测试覆盖不足

**结论**: ✅ 大部分确认

| 编号 | 问题 | 验证 |
|------|------|------|
| T-1 | 缺少 UploadController 集成测试 | ✅ — 仅有 `UploadApplicationServiceTest`（服务层），无 Controller 层集成测试 |
| T-2 | 缺少安全/SaToken 集成测试 | ✅ — 有 `AuthControllerTest` 但无安全特定测试 |
| T-3 | 缺少删除服务测试 | ✅ — `DocumentDeleteService` 无对应测试文件 |
| T-4 | 同上 | ✅ — 同上 |

已有测试文件包括 `AiControllerTest`、`AuthControllerTest`、`FileProcessConsumerTest` 及多个服务层测试。

---

## ⬜ 不存在的问题

（原审计报告中 F-3 已在上方重新验证为"完全确认"，A-4 循环依赖不存在。）

---

## 修复优先级建议

### 第一梯队（本周修复）

| 编号 | 问题 | 修复方式 |
|------|------|----------|
| **S-2** | Actuator 未鉴权 + 详情泄露 | 限缩暴露端点 + `show-details: when_authorized` |
| **P-3** | DocumentFileService 无事务 | 为读写方法加 `@Transactional` |
| **P-7** | 阻塞 Servlet 线程 | 标记废弃或改为 `DeferredResult`/异步 |

### 第二梯队（下个迭代）

| 编号 | 问题 | 修复方式 |
|------|------|----------|
| **C-4** | LIKE '%xx%' 全表扫描 | 添加 MySQL FULLTEXT 索引或使用 Elasticsearch |
| **P-1** | SELECT + UPDATE 双重查询 | 用 `UpdateWrapper` 替代 |
| **Q-7** | BCryptPasswordEncoder 重复 | 注册为单例 Bean |
| **A-1** | MinIO 原生 HTTP | 迁移到 MinIO SDK |

### 第三梯队（技术债务）

| 编号 | 问题 | 修复方式 |
|------|------|----------|
| **Q-3/Q-4** | 重复工具方法 | 抽取到 `ValidationUtils` / `ErrorUtils` |
| **Q-5** | RagUnitService 过长 | 拆分为 `RagUnitQueryService` + `RagUnitWriteService` |
| **F-3** | ScriptProcessorNode 废弃 | 迁移到 AudioWorkletNode |
| **B-1** | javacv-platform 过重 | 限定平台分类器 |
| **C-1/C-2/C-3** | 配置缺失 | 补全 HikariCP/优雅停机/Jackson 配置 |

### 无需操作

| 编号 | 原因 |
|------|------|
| **A-4** | 不存在循环依赖 |
