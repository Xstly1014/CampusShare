# Docker Compose 接入

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、docker-compose.yml 增量

在根目录 `docker-compose.yml` 新增 agent-service 与 agent-postgres 两个服务：

```yaml
services:
  # ... 现有服务 ...

  # ========================================
  # Agent 服务
  # ========================================

  # PostgreSQL + pgvector（Agent 向量库）
  agent-postgres:
    image: pgvector/pgvector:pg16
    container_name: campushare-agent-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: agent_vectors
      POSTGRES_USER: agent
      POSTGRES_PASSWORD: ${AGENT_PG_PASSWORD:-agent123456}
    ports:
      - "5432:5432"
    volumes:
      - agent_pg_data:/var/lib/postgresql/data
      - ./backend/docker/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U agent"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s
    networks:
      - campushare-network

  # BGE Embedding/Rerank 服务
  bge-service:
    image: ghcr.io/huggingface/text-embeddings-inference:1.5
    container_name: campushare-bge-service
    restart: unless-stopped
    environment:
      - MODEL_ID=BAAI/bge-m3
      - RERANK_MODEL_ID=BAAI/bge-reranker-v2-m3
      - PORT=8080
    ports:
      - "8084:8080"
    volumes:
      - bge_model_data:/data
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 120s
    networks:
      - campushare-network

  # Agent 微服务
  agent-service:
    build:
      context: ./backend
      dockerfile: Dockerfile
      target: agent-service
    image: campusshare/agent-service:latest
    container_name: campushare-agent-service
    restart: unless-stopped
    mem_limit: 1536m
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - TZ=Asia/Shanghai
      - JAVA_OPTS=-Xms512m -Xmx1024m -javaagent:/app/opentelemetry-javaagent.jar
      - OTEL_SERVICE_NAME=agent-service
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317
      - OTEL_METRICS_EXPORTER=none
      - DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY}
      - DOUBAO_API_KEY=${DOUBAO_API_KEY}
      - BGE_BASE_URL=http://bge-service:8080
      - AGENT_PG_PASSWORD=${AGENT_PG_PASSWORD:-agent123456}
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      agent-postgres:
        condition: service_healthy
      bge-service:
        condition: service_healthy
      post-service:
        condition: service_healthy
      user-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
    networks:
      - campushare-network

# 新增数据卷
volumes:
  # ... 现有 ...
  agent_pg_data:
    driver: local
    name: campushare-agent-pg-data
  bge_model_data:
    driver: local
    name: campushare-bge-model-data
```

## 二、端口分配表（更新）

| 服务 | 容器端口 | 宿主机端口 |
|------|---------|----------|
| frontend(nginx) | 80 | 80 |
| gateway-service | 8080 | 8080 |
| user-service | 8081 | 8081 |
| post-service | 8082 | 8082 |
| **agent-service** | **8083** | **8083** |
| **bge-service** | **8080** | **8084** |
| mysql | 3306 | 3306 |
| redis | 6379 | 6379 |
| **agent-postgres** | **5432** | **5432** |
| prometheus | 9090 | 9090 |
| grafana | 3000 | 3000 |
| tempo | 3200/4317/4318 | 3200/4317/4318 |

注意：bge-service 容器内监听 8080，但映射到宿主机 8084，避免与 gateway-service 8080 冲突。

## 三、资源预算（4 核 8G 服务器）

| 服务 | CPU | 内存 | 说明 |
|------|-----|------|------|
| mysql | 0.5 核 | 512M | 已有 |
| redis | 0.2 核 | 128M | 已有 |
| user-service | 0.5 核 | 1G | 已有 |
| post-service | 0.5 核 | 1G | 已有 |
| gateway-service | 0.2 核 | 512M | 已有 |
| frontend | 0.1 核 | 128M | 已有 |
| prometheus | 0.2 核 | 256M | 已有 |
| grafana | 0.2 核 | 256M | 已有 |
| tempo | 0.3 核 | 512M | 已有 |
| **agent-service** | **1 核** | **1.5G** | 新增 |
| **agent-postgres** | **0.3 核** | **512M** | 新增 |
| **bge-service** | **1 核** | **2G** | 新增（含模型加载） |
| 合计 | 5.0 核 | 9.3G | 超出 8G |

### 3.1 资源不足应对

4 核 8G 服务器无法承载全部服务。两种方案：

**方案 A：升级服务器到 8 核 16G**（推荐）
- 成本：约 ¥200/月（云服务器）。
- 收益：所有服务共存，运维简单。

**方案 B：BGE 服务部署到独立机器**
- bge-service 单独部署到带 GPU 的机器（或 CPU 推理机器）。
- 通过内网访问，agent-service 配置 `BGE_BASE_URL=http://<bge机器IP>:8080`。
- 主服务器资源降至 7.3G，可承载。

### 3.2 无 GPU 应对

若服务器无 GPU，bge-service 用 CPU 推理：
- 镜像改为 `ghcr.io/huggingface/text-embeddings-inference:cpu-1.5`。
- embedding 延迟从 50ms 升至 500ms（可接受）。
- 移除 `deploy.resources.reservations.devices` 配置。

## 四、PostgreSQL 初始化

`backend/docker/postgres/init.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS post_vectors (
  post_id VARCHAR(36) PRIMARY KEY,
  post_title VARCHAR(255) NOT NULL,
  post_content_excerpt TEXT,
  post_type VARCHAR(32),
  category VARCHAR(64),
  school VARCHAR(64),
  author_id VARCHAR(36),
  author_verified BOOLEAN,
  like_count INT DEFAULT 0,
  view_count INT DEFAULT 0,
  created_at TIMESTAMP,
  embedding vector(1024),
  embedding_model VARCHAR(32) DEFAULT 'bge-m3',
  embedding_version INT DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_post_vectors_embedding
  ON post_vectors USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
CREATE INDEX IF NOT EXISTS idx_post_vectors_category ON post_vectors(category);
CREATE INDEX IF NOT EXISTS idx_post_vectors_school ON post_vectors(school);
CREATE INDEX IF NOT EXISTS idx_post_vectors_type ON post_vectors(post_type);
CREATE INDEX IF NOT EXISTS idx_post_vectors_title_trgm
  ON post_vectors USING gin (post_title gin_trgm_ops);

CREATE TABLE IF NOT EXISTS knowledge_vectors (
  article_id BIGINT PRIMARY KEY,
  title VARCHAR(128) NOT NULL,
  topic VARCHAR(32) NOT NULL,
  content_excerpt TEXT,
  content_md5 CHAR(32),
  status VARCHAR(16),
  version INT,
  embedding vector(1024),
  embedding_model VARCHAR(32) DEFAULT 'bge-m3',
  embedding_version INT DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_embedding
  ON knowledge_vectors USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_topic ON knowledge_vectors(topic);
CREATE INDEX IF NOT EXISTS idx_knowledge_vectors_title_trgm
  ON knowledge_vectors USING gin (title gin_trgm_ops);
```

## 五、MySQL 初始化

agent-service 的 MySQL 表通过 `backend/docker/mysql/agent-init.sql` 初始化，由 agent-service 启动时检查执行：

```java
@Component
@RequiredArgsConstructor
public class AgentSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='campushare' AND table_name='agent_sessions'",
            Integer.class
        );
        if (count != null && count == 0) {
            log.info("Initializing agent-service schema...");
            executeSqlFile("classpath:schema/agent-init.sql");
            log.info("Agent schema initialized");
        }
    }
}
```

## 六、网关路由配置

gateway-service 的 `application.yml` 新增 agent-service 路由：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: agent-service
          uri: lb://agent-service
          predicates:
            - Path=/api/agent/**
          filters:
            - StripPrefix=1
            - JwtAuthFilter
          order: 5  # 在 post-service(10) 之前匹配
```

`JwtAuthFilter` 已有，解析 JWT 后透传 `X-User-Id` 头给 agent-service。

## 七、.env.example 更新

```bash
# === Agent Service ===
DEEPSEEK_API_KEY=sk-your-deepseek-key-here
DOUBAO_API_KEY=your-doubao-api-key-here
AGENT_PG_PASSWORD=agent123456
# BGE_BASE_URL 默认指向 docker 内 bge-service，本地开发可改为 http://localhost:8084
```

## 八、本地开发启动

```bash
# 1. 启动基础设施
docker-compose up -d mysql redis agent-postgres bge-service

# 2. 编译 agent-service
cd backend
$env:JAVA_HOME = "E:\javaJdk17"
mvn clean compile -DskipTests -pl campushare-agent -am

# 3. 启动 agent-service（IDE 中运行 AgentServiceApplication，profile=dev）

# 4. 启动前端
cd frontend
npm run dev
# 访问 http://localhost:5173
```

## 九、部署机重启命令

部署机路径 `/root/CampusShare`：

```bash
# 全量启动
cd /root/CampusShare && git pull origin master
docker-compose up -d --build

# 仅重启 agent-service
cd /root/CampusShare && git pull origin master
docker-compose up -d --build agent-service

# 重启 agent-service + bge-service（如改了 embedding 配置）
cd /root/CampusShare && git pull origin master
docker-compose up -d --build agent-service bge-service

# 数据库变更（新增 agent 表）
docker exec -it campushare-mysql mysql -uroot -proot123456 campushare < backend/docker/mysql/agent-init.sql
docker-compose restart agent-service
```

## 十、决策记录 (ADR)

### ADR-161: agent-service 端口 8083
- **理由**：8080-8082 已占用，8083 顺序延续。
- **一致性**：与 README 端口表风格一致。

### ADR-162: bge-service 映射到 8084 而非 8080
- **理由**：8080 是 gateway 宿主机端口，冲突。bge-service 容器内仍监听 8080（镜像默认），宿主机映射到 8084。
- **访问**：容器间通过 `http://bge-service:8080` 访问（Docker 网络），宿主机调试用 `http://localhost:8084`。

### ADR-163: 推荐升级服务器到 8 核 16G
- **理由**：4 核 8G 无法承载全部服务（合计 9.3G）。升级后无资源压力，运维简单。
- **替代**：bge-service 独立部署——增加运维复杂度，但成本低。

### ADR-164: bge-service 用 HuggingFace TEI 镜像
- **理由**：TEI 是 HuggingFace 官方推理服务，支持 BGE-M3 + reranker，性能优化好。
- **替代**：自建 Python FastAPI 包装 sentence-transformers——性能差 3-5×。

### ADR-165: PostgreSQL 用 pgvector/pgvector 镜像
- **理由**：见 [database-schema.md](./database-schema.md) ADR-144。

### ADR-166: agent-init.sql 由 agent-service 启动时检查执行
- **理由**：不污染主 init.sql，且 agent-service 独立部署时不依赖 MySQL 容器重建。
- **实现**：ApplicationRunner 检查表是否存在，不存在则执行 classpath:schema/agent-init.sql。

### ADR-167: 网关路由 order=5 优先于 post-service
- **理由**：`/api/agent/**` 必须在 `/api/**` 兜底前匹配。order 越小优先级越高。
- **验证**：请求 `/api/agent/sessions` 应路由到 agent-service，而非被 user-service 兜底接收。

### ADR-168: bge-service start_period 120s
- **理由**：BGE-M3 模型 2GB+，首次加载需 60-90s。120s start_period 避免健康检查误判失败。
- **风险**：agent-service 依赖 bge-service healthy 才启动，意味着 bge 加载期间 agent 也不可用。可接受（仅启动时）。
