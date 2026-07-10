# 部署拓扑

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、部署形态

复用现有单机 Docker Compose 部署（虚拟机 192.168.150.103，4核8G）。

### 1.1 新增容器
- `campushare-agent-service`：Java 17 Spring Boot，端口 8083。
- `campushare-postgres`（若选 PG-Vector）：PostgreSQL 16 + pgvector 扩展，端口 5432。

### 1.2 资源预算（4核8G 机器）
| 服务 | 内存限制 | 说明 |
|------|----------|------|
| agent-service | 1024m | JVM -Xmx768m |
| postgres(向量库) | 512m | shared_buffers=256m |
| 现有服务 | 6144m | user/post/gateway/mysql/redis/监控 |

> 若机器资源紧张，可暂时关闭 Tempo（链路用日志兜底），或向量库用 SQLite-vss 内嵌零运维方案做 MVP。

### 1.3 端口分配表（更新后）
| 服务 | 端口 |
|------|------|
| frontend(nginx) | 80 |
| gateway | 8080 |
| user-service | 8081 |
| post-service | 8082 |
| **agent-service（新）** | **8083** |
| mysql | 3306 |
| redis | 6379 |
| **postgres（新，可选）** | **5432** |
| prometheus | 9090 |
| grafana | 3000 |
| tempo | 3200 |

## 二、网络与依赖

```
agent-service depends_on:
  - mysql (healthy)     # Agent 自有表
  - redis (healthy)     # 会话缓存
  - user-service (healthy)  # Feign
  - post-service (healthy)  # Feign
  - postgres (healthy)  # 向量库（若用 PG-Vector）
```

agent-service 通过 Docker 网络访问：
- `http://user-service:8081`（Feign）
- `http://post-service:8082`（Feign）
- `http://postgres:5432`（JDBC，向量库）

外网访问（LLM/Embedding API）：
- 需配置 `dns: [8.8.8.8, 114.114.114.114]`（与 user-service 同款）。

## 三、配置管理

### 3.1 application-docker.yml 关键配置
```yaml
server:
  port: 8083
spring:
  application: { name: campushare-agent }
  datasource:  # Agent 自有表
    url: jdbc:mysql://mysql:3306/campushare?...
  redis: ...
agent:
  vector-store:
    type: pgvector  # 或 milvus
    pg:
      url: jdbc:postgresql://postgres:5432/campushare_vector
  llm:
    provider: deepseek
    base-url: ${LLM_BASE_URL}
    api-key: ${LLM_API_KEY}
    model: ${LLM_MODEL:deepseek-chat}
    fallback-model: ${LLM_FALLBACK_MODEL:qwen-turbo}
    timeout: 30s
  embedding:
    base-url: ${EMBEDDING_BASE_URL}
    api-key: ${EMBEDDING_API_KEY}
    model: ${EMBEDDING_MODEL:bge-m3}
feign:
  user: { url: http://user-service:8081 }
  post: { url: http://post-service:8082 }
```

### 3.2 .env.example 增量
见 `02-architecture/module-integration.md` 第五节。

## 四、启动与重启命令（实现阶段写入 AGENT-WORKFLOW.md）

- 改 agent-service：`cd /root/CampusShare && git pull origin master && docker-compose up -d --build agent-service`
- 改 agent + post 内部检索接口：`docker-compose up -d --build agent-service post-service`
- 全量：`docker-compose up -d --build`

## 五、决策记录 (ADR)

### ADR-005: 向量库部署形态 MVP 用 PG-Vector 独立容器
- **理由**：独立容器便于后续替换为 Milvus；不与 MySQL 混部避免向量索引影响业务库。
- **资源紧张备选**：SQLite-vss 内嵌 agent-service 容器，零额外容器，但并发与容量受限。
