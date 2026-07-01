# RAG 知识库问答系统

基于 **Spring Boot 3 + Spring AI + Vue 3** 的企业级 RAG（检索增强生成）系统，支持文档上传、解析、向量化、检索、重排、多轮对话和会话记忆。

---

## 技术栈

| 层级 | 技术方案 |
|------|----------|
| 后端 | Spring Boot 3.5 + Java 21 |
| AI 编排 | Spring AI 1.1.8 + Spring AI Alibaba 1.1.2.3 |
| 模型服务 | 阿里云 DashScope（qwen-plus / text-embedding-v3） |
| 向量存储 | Milvus 2.5 |
| 关系数据库 | MySQL 8.x + MyBatis-Plus |
| 缓存 | Redis 7.x |
| 消息队列 | RabbitMQ 3.x |
| 对象存储 | MinIO |
| 文档解析 | MinerU + Apache Tika + Apache POI |
| 音视频处理 | JavaCV + 阿里云 ASR |
| 认证鉴权 | Sa-Token |
| 前端 | Vue 3 + Vite 8 + Pinia + Vue Router + Tailwind CSS |

---

## 系统架构

```
用户浏览器
   │
   ▼
┌──────────────────────────────────────────────────────────────┐
│  Vue 3 前端  :5173（开发） / Nginx :80（生产）               │
└──────────────────────────────────────────────────────────────┘
   │
   ▼
┌──────────────────────────────────────────────────────────────┐
│  Spring Boot 后端  :8080                                     │
│                                                              │
│  ┌────────┐ ┌────────┐ ┌────────────┐ ┌────────┐           │
│  │ MySQL  │ │ Redis  │ │  Milvus    │ │ MinIO  │           │
│  │ :3306  │ │ :6379  │ │  :19530    │ │ :9000  │           │
│  └────────┘ └────────┘ └────────────┘ └────────┘           │
│  ┌──────────────────────┐                                    │
│  │  RabbitMQ  :5672     │                                    │
│  └──────────────────────┘                                    │
└──────────────────────────────────────────────────────────────┘
                      ▲
                      │ DashScope API / MinerU API
                      ▼
               阿里云 AI 服务
```

---

## 快速开始（本地部署）

### 1. 环境要求

| 工具 | 最低版本 |
|------|----------|
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 18+ |
| npm | 8+ |
| Docker | 20+（用于启动中间件） |

### 2. 启动中间件（Docker 一键启动）

```bash
# MySQL
docker run -d --name mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=nm561234789 \
  -e MYSQL_DATABASE=rag_knowledge \
  mysql:8.0

# Redis
docker run -d --name redis \
  -p 6379:6379 \
  redis:7-alpine

# Milvus（Standalone 模式，内置 etcd + minio）
docker run -d --name milvus-standalone \
  -p 19530:19530 -p 9091:9091 \
  milvusdb/milvus:v2.5.13 milvus run standalone

# RabbitMQ（带管理界面）
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management-alpine

# MinIO（应用对象存储）
docker run -d --name minio \
  -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  minio/minio server /data --console-address ":9001"
```

> **注意**：Milvus Standalone 镜像内置了 etcd 和 MinIO，无需额外启动。如果本地已有同端口服务，请调整端口映射。

### 3. 创建数据库并导入表结构

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS rag_knowledge DEFAULT CHARACTER SET utf8mb4;"

# 导入建表 SQL
mysql -u root -p rag_knowledge < sql/schema.sql
```

SQL 文件位于项目根目录 `sql/` 下：

```
sql/
├── schema.sql                 # 主表结构（首次部署执行）
├── V2__add_auth_user_email.sql # Flyway 增量迁移
├── reset_remote.sql           # 远程数据库重置脚本
└── temp_reset_rag.sql         # 临时 RAG 数据重置脚本
```

> **注意**：如果已有数据库且表结构存在，Flyway 会以 Baseline 方式接入，无需重复导入。首次部署请先执行 `schema.sql`。

### 4. 配置 API Key

编辑 `src/main/resources/application.yaml`，将占位符替换为真实值：

```yaml
spring:
  ai:
    dashscope:
      api-key: 你的DashScope API Key          # 必填，用于 LLM 对话 + Embedding

mineru:
  api-key: 你的MinerU JWT Token               # 可选，用于 PDF/Word/Excel/PPT 高质量解析

aliyun:
  asr:                                         # 可选，用于语音识别
    access-key-id:     你的阿里云AK
    access-key-secret: 你的阿里云SK
    app-key:           你的ASR AppKey
```

**DashScope API Key 获取**：https://dashscope.console.aliyun.com/ → API Key 管理

### 5. 修改数据库密码（如果与默认不同）

```yaml
# application.yaml
spring:
  datasource:
    password: ${DB_PASSWORD:你的MySQL密码}
```

### 6. 启动后端

```bash
cd demo
mvn spring-boot:run -DskipTests
```

后端运行在 **http://localhost:8080**，健康检查：

```bash
curl http://localhost:8080/actuator/health
# 返回 {"status":"UP"} 表示启动成功
```

### 7. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 **http://localhost:5173**

### 8. 访问系统

浏览器打开 http://localhost:5173，注册账号后即可使用。

---

## 服务器部署

### 后端打包

```bash
mvn clean package -DskipTests
# 产出 target/demo-0.0.1-SNAPSHOT.jar
```

### 前端打包

```bash
cd frontend
npm run build
# 产出 frontend/dist/
```

### 后端启动（生产环境）

```bash
nohup java -jar target/demo-0.0.1-SNAPSHOT.jar \
  --spring.datasource.password=你的MySQL密码 \
  --spring.ai.dashscope.api-key=你的DashScope Key \
  > app.log 2>&1 &
```

也可通过环境变量注入：

```bash
export DB_PASSWORD=你的MySQL密码
export DASHSCOPE_API_KEY=你的DashScope Key
export REDIS_PASSWORD=你的Redis密码
export RABBITMQ_PASSWORD=你的RabbitMQ密码
export MINIO_SECRET_KEY=你的MinIO密码

java -jar target/demo-0.0.1-SNAPSHOT.jar
```

### Nginx 配置（前端 + 反向代理）

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        root /path/to/frontend/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 服务器端口一览

| 服务 | 端口 | 说明 |
|------|------|------|
| Nginx | 80 / 443 | 前端静态文件 + 反向代理 |
| Spring Boot | 8080 | 后端 API |
| MySQL | 3306 | 关系数据库 |
| Redis | 6379 | 缓存 + 会话记忆 |
| Milvus | 19530 | 向量数据库 |
| RabbitMQ | 5672 / 15672 | 消息队列 / 管理界面 |
| MinIO | 9000 / 9001 | 对象存储 / 管理界面 |

---

## 环境变量汇总

| 变量 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `DASHSCOPE_API_KEY` | ✅ | — | DashScope API Key（对话 + Embedding） |
| `DB_PASSWORD` | ✅ | — | MySQL root 密码 |
| `MINERU_API_KEY` | ❌ | — | MinerU 文档解析 Token |
| `ALIYUN_ASR_ACCESS_KEY_ID` | ❌ | — | 阿里云语音识别 AK |
| `ALIYUN_ASR_ACCESS_KEY_SECRET` | ❌ | — | 阿里云语音识别 SK |
| `ALIYUN_ASR_APP_KEY` | ❌ | — | 阿里云语音识别 AppKey |
| `BAIDU_MAP_AK` | ❌ | — | 百度地图 MCP |
| `REDIS_PASSWORD` | ❌ | 空 | Redis 密码（Docker 默认无密码） |
| `RABBITMQ_PASSWORD` | ❌ | guest | RabbitMQ 密码 |
| `MINIO_SECRET_KEY` | ❌ | minioadmin | MinIO 密钥 |

---

## 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| `InvalidApiKey` | DashScope Key 未设置或已过期 | 设置 `DASHSCOPE_API_KEY` 环境变量 |
| `Access denied for user 'root'` | MySQL 密码错误或 IP 未授权 | 检查 `DB_PASSWORD`，确认 MySQL 允许该 IP 访问 |
| `SignatureDoesNotMatch` (MinIO) | access-key / secret-key 不匹配 | 检查 MinIO 凭据，或重启 MinIO 容器 |
| Milvus 连接超时 | Milvus 容器未启动 | `docker start milvus-standalone`，等待 30 秒 |
| Flyway 迁移失败 | 数据库表结构冲突 | 执行 `mvn flyway:repair` 或重建数据库 |
| 前端白屏 | 后端未启动或 CORS 配置错误 | 确认后端已启动，检查浏览器控制台 |
| 端口被占用 | 其他进程占用端口 | 用 `netstat -ano \| findstr :端口号` 查找并关闭 |

---

## 项目结构

```
demo/
├── src/main/java/com/example/demo/
│   ├── Config/              # 配置类（Milvus、CORS、SaToken、RateLimit 等）
│   ├── Controller/          # REST 接口（AI、Auth、Upload、Document、Chunk）
│   ├── mapper/              # MyBatis Mapper
│   ├── model/               # 实体与 DTO
│   ├── service/             # 核心业务服务
│   │   ├── ai/              # AI 相关（Chat、Embedding、Rerank）
│   │   ├── agent/           # Agent 编排（Plan-Execute、ReAct）
│   │   └── processor/       # 各类文件处理器
│   └── util/                # 工具类
├── src/main/resources/
│   ├── application.yaml     # 主配置文件
│   └── db/migration/        # Flyway SQL 迁移脚本
├── frontend/                # Vue 3 前端项目
│   ├── src/
│   ├── package.json
│   └── vite.config.js
└── pom.xml
```

---

## License

内部项目，未声明正式许可证。
