# 安全风险确认清单（2026-07-06）

本文基于当前代码实现和用户提供的审计文本整理。结论：文本中的核心安全问题大部分成立，其中 LLM Provider 密钥处理、LLM 调试数据持久化、SSRF 防护细节、删除任务状态归属校验、可信代理 IP 解析属于优先整改项。

## 高优先级

### 1. LLM Provider 响应构造前批量解密 API Key

**状态：已完成。**

当前响应只返回 `maskedApiKey`，但服务层为了生成 mask 会在多个管理接口里解密密钥，扩大了明文密钥在内存、调试器、异常 dump、日志误打点中的暴露面。

证据位置：

- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:129`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:161`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:200`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:212`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:223`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:415`

建议：

- 已调整列表、启停和空密钥更新响应路径，不再为展示 mask 解密库内密钥。
- 响应新增 `hasApiKey`，保留 `maskedApiKey` 兼容前端；无本次输入明文时只返回通用 `****`。
- 创建/更新传入新密钥时仍仅基于本次请求明文生成 mask，不从数据库解密。
- 对应测试已补充 `verify(crypto, never()).decrypt(any())`，防止回归。

验证：

- `mvn -Dtest=LlmProviderApplicationServiceTest test`

### 2. LLM 调试持久化完整请求和原始响应

**状态：已完成。**

LLM 调试会保存完整请求、解析后请求体、原始响应和完整响应对象。这些字段可能包含用户输入、系统提示词、RAG 上下文、模型输出、业务敏感数据或个人信息。

证据位置：

- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:453`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:460`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:461`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:463`
- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:464`
- `src/main/java/com/example/demo/model/dto/llm/LlmDebugResponse.java:17`
- `src/main/java/com/example/demo/model/dto/llm/LlmDebugResponse.java:21`
- `src/main/java/com/example/demo/model/dto/llm/LlmDebugResponse.java:31`

建议：

- 已新增 `llm.debug.persist-detail` 开关，默认 `false`。
- 默认持久化调试记录时不再保存 `debugRequestJson`、`resolvedRequestJson`、`rawResponseText`、完整 `parsedResponseJson`。
- 默认只保留必要元信息、`maskedHeadersJson`、HTTP 状态、耗时、成功状态、错误码和截断后的错误消息。
- 管理接口即时响应暂保持原调试详情，避免影响管理员在线调试；如生产需要进一步收紧，可继续增加响应级别的详情开关。

验证：

- `mvn -Dtest=LlmProviderApplicationServiceTest test`

### 3. 自定义 LLM Endpoint SSRF 防护仍不完整

**状态：已完成第一阶段加固。**

当前已做本机和内网地址判断，但仍存在校验与实际连接分离、重定向、端口限制、DNS rebinding 等风险。

证据位置：

- `src/main/java/com/example/demo/service/llm/HttpLlmDebugClient.java:416`
- `src/main/java/com/example/demo/service/llm/HttpLlmDebugClient.java:436`
- `src/main/java/com/example/demo/service/llm/HttpLlmDebugClient.java:448`

已完成：

- 已拒绝包含 userinfo 的 URL，避免 `https://user@example.com/...` 这类混淆地址。
- 已在 `llm.allow-private-endpoints=false` 时限制公网显式端口，仅允许 `80`、`443`、`8443`。
- 已在非流式和流式 `HttpURLConnection` 请求中显式禁用自动重定向。
- 非流式调试遇到非 2xx 响应时不再进入成功响应解析分支，而是返回安全的错误调试响应。
- 非流式调试已统一到 `HttpURLConnection` 安全请求路径，并受 `llm.debug.max-response-bytes` 响应大小上限约束。
- 流式调试读取上游 SSE 时同样受 `llm.debug.max-response-bytes` 响应大小上限约束。
- 已补充回归测试覆盖私有地址、非法协议、userinfo、非白名单端口、非流式重定向、流式重定向和超大响应拒绝。

后续增强：

- DNS rebinding 与校验/连接分离问题仍建议继续通过连接前后最终远端地址复核解决。
- IPv4-mapped IPv6、十进制/八进制/十六进制 IP、短 IP 等非常规 IP 表达建议继续补全。
- 首包时间和总耗时硬上限仍可继续增强，目前连接和读取超时仍使用 Provider 自身配置。

验证：

- `mvn -Dtest=HttpLlmDebugClientTest test`

### 4. LLM 调试流式接口实际先聚合完整响应

**状态：已完成。**

`LlmProviderApplicationServiceImpl.debugStream(...)` 先调用 `debug(request)` 得到完整 `LlmDebugResponse`，再把 `streamEvents` 组装成 SSE 返回。前端看到 SSE，但后端可能已经完整等待并缓存上游响应。

证据位置：

- `src/main/java/com/example/demo/service/llm/LlmProviderApplicationServiceImpl.java:334`
- `src/main/java/com/example/demo/service/llm/HttpLlmDebugClient.java:153`
- `src/main/java/com/example/demo/service/llm/HttpLlmDebugClient.java:184`

已完成：

- `debugStream` 已直接解析 Provider 并委托 `httpLlmDebugClient.debugStream(...)`。
- 流式调试不再调用 `debug(request)`，不再先生成完整 `LlmDebugResponse`。
- 流式调试不再持久化完整调试会话详情，避免把完整流式响应落入调试记录。
- 已新增 `llm.debug.max-sse-events`，限制单次流式调试可消费的 SSE 事件数。
- 已补充回归测试，确保流式路径不会调用 `httpLlmDebugClient.debug(...)`，不会插入 `LlmDebugSession`，且超出 SSE 事件数量上限会中止。

后续增强：

- 总耗时硬上限仍可继续增强。
- 超限时已中止当前流式处理，并通过指标记录失败。

验证：

- `mvn -Dtest=LlmProviderApplicationServiceTest test`

## 中优先级

### 5. 删除任务状态接口缺少用户归属校验

**状态：已完成。**

当前返回体没有直接返回 `minioPath`，但 Redis 内部任务对象 `FileDeleteTask` 包含 `userId`、`fileHash`、`minioPath`、向量 ID 和分段 ID。查询接口只按 `taskId` 查任务，没有校验当前用户是否为任务所属用户。

证据位置：

- `src/main/java/com/example/demo/Controller/RagDocumentController.java:113`
- `src/main/java/com/example/demo/service/RagDocumentApplicationService.java:136`
- `src/main/java/com/example/demo/service/DocumentDeleteService.java:82`
- `src/main/java/com/example/demo/model/dto/FileDeleteTask.java:17`
- `src/main/java/com/example/demo/model/dto/FileDeleteTask.java:20`

已完成：

- 控制器查询删除任务状态时已使用 `authContextService.getCurrentUserId()` 获取当前用户。
- 应用服务已改为 `getDeleteStatus(taskId, currentUserId)` 并向下传递用户 ID。
- `DocumentDeleteService` 从 Redis 读取任务后校验 `task.userId` 与当前用户一致。
- 归属不匹配时统一按“任务不存在或已过期”处理，避免暴露其他用户任务是否存在。
- 已补充回归测试，覆盖应用服务传递用户 ID 和删除服务拒绝查询他人任务。

后续增强：

- Redis key 可继续演进为 `delete:task:{userId}:{taskId}`，进一步降低横向猜测风险。
- 返回体应继续避免暴露 `minioPath`、内部向量 ID 等实现细节。

验证：

- `mvn '-Dtest=RagDocumentApplicationServiceTest,DocumentDeleteServiceTest' test`

### 6. IP 限流信任 X-Forwarded-For

**状态：已完成。**

限流和忘记密码确认都直接读取 `X-Forwarded-For` 第一个 IP。如果应用直接暴露公网，攻击者可以伪造该头绕过基于 IP 的限制。

证据位置：

- `src/main/java/com/example/demo/Config/RateLimitInterceptor.java:62`
- `src/main/java/com/example/demo/Controller/AuthController.java:146`

已完成：

- 已新增共享 `ClientIpResolver`，限流拦截器和忘记密码确认接口统一使用该解析器。
- 默认不信任任何代理头；只有 `remoteAddr` 命中 `app.security.trusted-proxies` 配置后，才接受 `X-Forwarded-For` 或 `X-Real-IP`。
- `X-Forwarded-For` 仅取第一个合法 IP 字面量；非法值会回退到 `remoteAddr`。
- 已支持可信代理精确 IP 和 CIDR 配置。
- 已补充回归测试，覆盖未信任代理伪造转发头、可信代理转发、非法转发头回退。

后续增强：

- 限流维度可继续加入账号、用户 ID、endpoint、设备指纹等。
- 忘记密码确认可继续按用户名和 IP 双维度细分限流策略。

验证：

- `mvn '-Dtest=ClientIpResolverTest,AuthControllerTest' test`

### 7. Swagger/OpenAPI 在登录拦截中放行

**状态：已完成。**

如果生产环境也使用同一配置，接口结构、参数和权限点会直接暴露。

证据位置：

- `src/main/java/com/example/demo/Config/SaTokenConfig.java:47`
- `src/main/java/com/example/demo/Config/SaTokenConfig.java:48`
- `src/main/java/com/example/demo/Config/SaTokenConfig.java:49`

已完成：

- 已新增 `application-prod.yaml`，生产 profile 下关闭 `springdoc.api-docs.enabled` 和 `springdoc.swagger-ui.enabled`。
- 已补充配置回归测试，确保生产配置不会重新暴露 `/v3/api-docs/**` 和 Swagger UI。
- 开发环境仍保留 Swagger 登录拦截放行，方便本地调试。

后续增强：

- 如果生产环境仍需要 API 文档，应改为内网/VPN/认证保护，而不是公网匿名访问。

验证：

- `mvn -Dtest=ProductionOpenApiConfigTest test`

### 8. CORS 允许 credentials，但当前前端使用 header token

**状态：已完成。**

当前前端通过 `satoken` header 传 token，后端 CORS 仍允许 credentials。若暂不使用 Cookie，`allowCredentials(true)` 会扩大跨站携带 Cookie 的风险面。

证据位置：

- `src/main/java/com/example/demo/Config/WebMvcConfig.java:41`
- `frontend/src/api/index.js:10`
- `frontend/src/api/index.js:12`

已完成：

- CORS `allowCredentials` 已改为 `app.cors.allow-credentials` 配置项。
- 默认值为 `false`，`application.yaml` 已显式配置 `allow-credentials: false`，匹配当前 header token 模式。
- 已补充回归测试，确保 `CorsProperties` 默认不允许 credentials。

后续增强：

- 若迁移到 httpOnly Cookie，必须显式开启该配置，并同时加入 SameSite、Secure、CSRF token 和严格 Origin 校验。

验证：

- `mvn -Dtest=WebMvcConfigTest test`

### 9. Chat.vue 使用 v-html，虽有 DOMPurify 但缺少策略化测试

**状态：已完成。**

当前 markdown-it 已禁用 raw HTML，且渲染后经过 DOMPurify，不能直接判定为漏洞。但 `v-html` 属于高风险渲染点，应补 XSS 回归测试和协议策略。

证据位置：

- `frontend/src/views/Chat.vue:82`
- `frontend/src/views/Chat.vue:790`
- `frontend/src/views/Chat.vue:792`

已完成：

- 已新增 `markdownSecurity` 协议白名单，仅允许 `http`、`https`、`mailto` 和受控相对链接。
- `markdown-it` 已接入 `validateLink`，渲染阶段拒绝 `javascript:`、危险 `data:`、`vbscript:`、协议相对 URL 等链接。
- DOMPurify 已增加 `ALLOWED_URI_REGEXP`，对最终 `href` 再做一层协议约束。
- 外链继续强制 `target="_blank"` 和 `rel="noopener noreferrer"`。
- 已新增轻量安全测试脚本，覆盖允许/拒绝的 Markdown 链接协议；前端生产构建已通过。

验证：

- `npm run test:security`（工作目录：`frontend`）
- `npm run build`（工作目录：`frontend`）

## 低优先级/规范问题

### 10. 仓库仍有编码和规范一致性问题

**状态：已完成。**

部分文件曾出现中文注释乱码，说明缺少统一编码约束。

已完成：

- 已新增 `.editorconfig`，统一 UTF-8、LF、最终换行和基础缩进规则。
- 已新增 `.gitattributes`，从 Git 层面对文本文件强制 LF，避免不同系统提交时换行漂移。
- Markdown 保留尾随空格例外，避免破坏有意使用的 Markdown 换行。

后续增强：

- CI 可继续增加编码检查或格式化检查。

### 11. 临时工具和本地文件需要持续隔离

**状态：已完成。**

`TempBcrypt.java` 等本地工具应保持在 `.gitignore` 内，不应进入仓库。已经提交过的 `.idea` 文件应持续从版本控制中排除。

已确认：

- `.gitignore` 已覆盖 `src/main/resources/application-dev.yaml`，该本地明文配置不会被提交。
- `.gitignore` 已覆盖 `.idea/`、`TempBcrypt.java`、日志、临时目录和本地测试产物。
- 当前 `git ls-files` 未发现 `TempBcrypt.java`、`.idea`、`application-dev.yaml` 被版本控制跟踪。

后续约束：

- 本地一次性工具继续放在忽略目录。
- 如确实需要密码生成工具，应做成正式脚本并禁止输出生产密码。

## 仍需补充的剩余风险

以下问题不影响上文已完成整改项的结论，但仍建议纳入后续安全迭代，避免“第一阶段加固”被误读为彻底消除风险。

## 剩余高优先级

### 12. LLM 调试请求头脱敏仍不完整

**状态：已完成。**

当前已解决 API Key 响应 mask 和调试详情默认不持久化，但 `HttpLlmDebugClient` 对自定义请求头的脱敏策略仍偏窄。当前主要处理 `Authorization: Bearer ...`，但自定义 Provider 可能把密钥放在其他 header 中。

风险 header 示例：

- `X-API-Key`
- `api-key`
- `Authorization: Basic ...`
- `Cookie`
- `Proxy-Authorization`
- `OpenAI-Organization`
- 其他供应商约定的鉴权、组织、租户、项目类敏感 header

已完成：

- 已对 header 名称做大小写无关匹配，命中敏感 header 时统一脱敏。
- `Authorization` 和 `Proxy-Authorization` 不再只处理 Bearer，Basic、自定义 scheme 等都会保留 scheme 并隐藏凭据。
- `X-API-Key`、`api-key`、`Cookie`、`OpenAI-Organization`、`token`、`secret`、`api-key` 等敏感命名模式会显示为 `****`。
- 普通调试 header 仍保留，便于管理员排查请求上下文。
- 已补充回归测试，覆盖大小写混合、Basic、Cookie、Proxy-Authorization、供应商 API Key header，确保 `maskedHeadersJson` 不包含原始敏感值。

验证：

- `mvn -Dtest=HttpLlmDebugClientTest test`

### 13. SSRF 仍缺 DNS rebinding / DNS pinning 防护

**状态：已完成第二阶段加固。**

第 3 项已完成第一阶段加固，但还不能视为 SSRF 已彻底解决。当前仍存在校验 host 与实际连接之间的 TOCTOU / DNS rebinding 窗口。

已完成：

- 校验阶段会解析 host 并保存初始解析地址集合。
- 请求发起前和收到响应码后会重新解析 host，若当前解析结果与初始解析无交集则拒绝，降低 DNS rebinding / TOCTOU 风险。
- 默认生产模式下，复核阶段若发现解析结果变为本机、内网、link-local、multicast、metadata 等地址会拒绝。
- 已补充 IPv4-mapped IPv6、IPv6 ULA、短 IP、十进制/八进制/十六进制 IP、云 metadata IP 等绕过表达的回归测试。
- `allowPrivateEndpoints=true` 仍允许本地测试/开发端点，避免影响现有本地 HTTP Server 测试。

后续增强：

- 当前实现是 DNS 解析前后复核，不是 socket 级 DNS pinning；如果要彻底绑定实际连接目标，需要自定义 HTTP/TLS 连接层或引入支持自定义 DNS resolver 的 HTTP client。
- metadata 和云厂商内部地址段应持续根据部署云环境补充。

验证：

- `mvn -Dtest=HttpLlmDebugClientTest test`

### 14. LLM Provider 运行时仍可能长期缓存明文 API Key

**状态：已完成。**

第 1 项解决的是“为了展示 mask 而解密”的问题，但运行时调用 LLM 仍可能通过 `LlmProviderRegistry` 把启用 Provider 的密钥解密后放入 `RuntimeLlmProvider`，导致明文密钥长期驻留内存。

风险：

- reload 或注册启用 Provider 时可能批量解密多个密钥。
- Runtime 对象长期持有明文 API Key，扩大 heap dump、调试器、日志误打点和内存扫描暴露面。
- 密钥轮换后，缓存对象生命周期若控制不好，可能继续使用旧明文。

已完成：

- `LlmProviderRegistry` 不再在 `reload()` / `register()` / `getRequired()` 阶段解密 API Key。
- `RuntimeLlmProvider` 已增加 `apiKeyCiphertext`，入库 Provider 的 Runtime 对象只保存密文，不长期持有明文。
- `HttpLlmDebugClient` 构造实际请求头时才按需解密密文；临时 Provider 仍只使用本次请求内的明文。
- `withModel(...)` 会继续传递密文，模型切换不会引入明文缓存。
- 已补充测试，确保 Registry 刷新和按模型解析路径不会调用 `decrypt(...)`，实际 debug 请求才解密并发送 Bearer header。

验证：

- `mvn '-Dtest=LlmProviderRegistryTest,HttpLlmDebugClientTest' test`

### 15. 流式调试仍缺总时长和并发上限

**状态：已完成第一阶段加固。**

第 4 项已经解决“不先聚合完整响应”、`max-sse-events` 和响应字节上限，但慢速 SSE、心跳流和长连接仍可能长期占用线程、连接和上游资源。

已完成：

- `llm.debug.max-stream-duration-ms` 为单次流式调试增加总时长上限，超时后返回 408 并主动断开上游连接。
- `llm.debug.max-active-streams` 为流式调试增加全局活跃 stream semaphore，耗尽时返回 429。
- 流式订阅取消和流结束时会调用 `disconnect()` 并释放并发许可，避免连接和线程长期驻留。
- 已补充并发耗尽和总时长 deadline 回归测试。

仍需后续增强：

- 当前已实现全局并发上限，每用户、每 Provider 维度的 stream semaphore 可按实际多租户策略继续补充。
- 总时长 deadline 会主动断开连接；阻塞读仍受 Provider `readTimeoutMs` 和底层 `HttpURLConnection` 行为影响，若要做到 socket 级精确取消，可后续迁移到支持自定义超时/取消语义的 HTTP client。
- 可继续增加客户端取消、慢速心跳保活的端到端回归测试。

验证：

- `mvn -Dtest=HttpLlmDebugClientTest test`

## 剩余中优先级

### 16. 配置可信代理后仍依赖入口网关清洗 XFF

**状态：已完成。**

`ClientIpResolver` 默认不信任代理头是安全的，但当配置 `app.security.trusted-proxies` 后，当前策略仍固定取 `X-Forwarded-For` 最左侧合法 IP。如果入口代理只是 append 而不覆盖客户端传入的 XFF，最左侧值可能被攻击者污染。

已完成：

- `ClientIpResolver` 已改为从右到左遍历 `X-Forwarded-For`，跳过可信代理地址，选择第一个非可信合法客户端 IP。
- 已补充攻击者预置 XFF、代理 append、多可信代理链路的回归测试。

部署前提：

- 配置 `app.security.trusted-proxies` 时，应用入口必须只允许边界代理访问，不能直接暴露公网。
- 边界代理仍应覆盖或清洗客户端传入的 `X-Forwarded-For`、`X-Real-IP`，应用侧解析是第二道防线。

验证：

- `mvn '-Dtest=ClientIpResolverTest,SaTokenConfigTest,ProductionOpenApiConfigTest,ProductionCorsConfigValidatorTest' test`

### 17. Swagger 生产关闭依赖 prod profile 正确激活

**状态：已完成。**

当前已新增 `application-prod.yaml` 关闭 springdoc，但如果生产忘记激活 `prod` profile，默认配置下 `SaTokenConfig` 仍会匿名放行 Swagger/OpenAPI 路径。

已完成：

- `application.yaml` 和 `application-prod.yaml` 均默认关闭 `springdoc.api-docs.enabled` 与 `springdoc.swagger-ui.enabled`。
- `SaTokenConfig` 默认不再匿名放行 `/swagger-ui/**`、`/swagger-ui.html`、`/v3/api-docs/**`。
- 如需本地开放 Swagger，必须显式配置 `app.security.allow-public-swagger=true` 并开启 springdoc。
- 已补充默认配置、生产配置和 Swagger 匿名放行策略测试。

验证：

- `mvn '-Dtest=ClientIpResolverTest,SaTokenConfigTest,ProductionOpenApiConfigTest,ProductionCorsConfigValidatorTest' test`

### 18. 生产 CORS 还需要独立 allowlist 和启动校验

**状态：已完成。**

当前已把 `allowCredentials` 默认关闭，但如果 `application-prod.yaml` 没有覆盖 `app.cors.allowed-origins`，默认开发源可能随默认配置进入生产。

已完成：

- `application-prod.yaml` 已显式配置 `app.cors.allowed-origins` 从 `APP_CORS_ALLOWED_ORIGINS` 注入，并保持 `allow-credentials=false`。
- 新增 `ProductionCorsConfigValidator`，仅在 `prod` profile 下启动校验。
- 生产环境下如果 CORS allowlist 为空，或包含 `localhost`、`127.0.0.1`、`[::1]`、`0.0.0.0`、`*`、`null`，启动时 fail fast。
- 已补充生产空 allowlist、开发源、合法生产源和非生产跳过校验的回归测试。

验证：

- `mvn '-Dtest=ClientIpResolverTest,SaTokenConfigTest,ProductionOpenApiConfigTest,ProductionCorsConfigValidatorTest' test`

### 19. 前端安全测试尚未进入 CI，且测试范围偏轻

**状态：已完成。**

当前已新增 `npm run test:security`，但 CI 仍需确认实际执行该脚本；测试范围也主要覆盖链接协议判断函数，还没有覆盖完整渲染链路。

已完成：

- `.github/workflows/ci.yml` 已在前端 job 中加入 `npm run test:security`。
- `frontend/scripts/markdown-security-test.mjs` 已覆盖 `markdown-it -> DOMPurify -> 最终 v-html HTML` 的完整链路。
- 已覆盖 `javascript:`、危险 `data:`、SVG payload、`<img onerror=...>`、协议相对 URL 等恶意 markdown 样例。
- 测试已校验安全外链最终包含 `target="_blank"` 和 `rel="noopener noreferrer"`。
- `Chat.vue` 的 sanitizer 已改为显式 HTML 标签白名单，排除 SVG、script、img 等高风险标签，并在清洗后仅对保留安全 `href` 的链接补 `target` / `rel`。

验证：

- `npm run test:security`
- `npm run build`

### 20. CI 还缺 secret scanning

**状态：已完成。**

`application-dev.yaml` 被 `.gitignore` 忽略是必要但不充分的防线。仍建议在 CI 和本地提交前增加 secret scanning，降低误提交凭据的概率。

已完成：

- `.github/workflows/ci.yml` 已新增 `Secret Scan` job，使用 `gitleaks/gitleaks-action@v3` 扫描提交内容。
- 已检查前端 `.env.development`、`.env.production`，当前仅包含空的 `VITE_API_BASE_URL` 配置。
- 已删除被跟踪的前端实际 `.env.development`、`.env.production`，新增 `frontend/.env.example` 模板。
- `.gitignore` 已忽略 `frontend/.env*`，仅允许提交 `frontend/.env.example`。

仍需运维侧配合：

- CI secret scan 发现真实密钥时，应立即轮换对应凭据。
- 如需审计历史提交，可在仓库全历史上另行执行 Gitleaks/TruffleHog 深度扫描。

## 备注

- `src/main/resources/application-dev.yaml` 当前被 `.gitignore` 忽略，不应提交。允许本地明文配置是开发策略选择，但仍建议配合 secret scan 防止误提交。
- 本文已从确认清单演进为整改跟踪文档；标记为“已完成”的条目均已同步对应代码、配置或测试变更。
