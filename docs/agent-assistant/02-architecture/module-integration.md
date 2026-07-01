# 模块集成方案

> 状态: 草稿  
> 最后更新: 2026-06-30

本文档定义 Agent 模块如何嵌入现有 CampusShare，覆盖前端、网关、后端、数据、部署五个层面。

## 一、前端集成

### 1.1 NavBar 改动
当前 `frontend/src/components/common/NavBar.tsx` 的 `navItems` 顺序：首页 → 仓库 → 通知 → 我的。  
新增 Agent 入口，插入到首页与仓库之间，使用 `Sparkles` 或 `Bot` 图标（lucide-react）：

```ts
const navItems = [
  { path: '/home', icon: Home, label: '首页' },
  { path: '/assistant', icon: Bot, label: '助手' },   // 新增
  { path: '/warehouse', icon: Package, label: '仓库' },
  { path: '/notifications', icon: Bell, label: '通知', badge: unreadCount },
  { path: '/profile', icon: User, label: '我的' },
]
```

> 注意：5 个 tab 在移动端底部宽度仍可接受（每个约 20%），需验证小屏不挤。

### 1.2 路由
在 `frontend/src/router/index.tsx` 新增：
```tsx
<Route path="/assistant" element={<PrivateRoute><AssistantPage /></PrivateRoute>} />
```
需登录（PrivateRoute），复用现有认证守卫。

### 1.3 新增文件
- `frontend/src/pages/AssistantPage.tsx`：聊天主页面。
- `frontend/src/components/assistant/`：消息气泡、引用卡片、流式渲染、反馈按钮、输入框等子组件。
- `frontend/src/services/assistant.ts`：Agent API 封装（SSE + REST）。
- `frontend/src/stores/assistantStore.ts`：会话状态（Zustand）。

### 1.4 复用
- `services/http.ts`：REST 请求。
- `context/AuthContext`：取 userId。
- `components/common/Toast`：错误提示。
- `utils/time.ts`：消息时间显示。

## 二、网关集成

### 2.1 路由
`backend/campushare-gateway/src/main/resources/application-docker.yml` 新增：
```yaml
- id: agent-service
  uri: http://agent-service:8083
  predicates:
    - Path=/api/assistant/**,/api/internal/agent/**
```

### 2.2 白名单
Agent 接口**需要登录**（用于个性化与反馈），不在白名单。  
但 SSE 长连接需注意：
- gateway Netty 不对 SSE 做请求体大小限制（SSE 是响应流，无请求体）。
- gateway 默认超时需调大（SSE 长连接）：`httpclient.responseTimeout: 300s`。
- JWT 过期时 SSE 连接应被关闭，前端捕获后刷新 token 重连。

### 2.3 内部 API
`/api/internal/agent/**` 供未来其他服务调用（如 post-service 想用 Agent 做内容摘要），MVP 不实现。

## 三、后端微服务集成

### 3.1 新建 campushare-agent 模块
遵循 `AGENT-WORKFLOW.md` 第六章「创建新微服务步骤」：
- `backend/campushare-agent/` Maven 模块。
- 父 pom 注册 `<module>campushare-agent</module>`。
- 启动类 `AgentApplication`。
- 包结构：config/controller/service/impl/mapper/entity/dto/feign/tool/agent。

### 3.2 Feign 客户端
Agent 服务需调用：
- `PostFeignClient`（调 post-service 内部接口，需扩展语义检索接口）。
- `UserFeignClient`（调 user-service 内部接口，取用户公开信息）。

### 3.3 post-service 需扩展的内部接口
在 `InternalPostController` 新增：
- `POST /api/internal/posts/semantic-search`：接收 query + filters，返回候选帖（向量检索在 agent 侧做，或在 post 侧做）。
- 决策：向量索引归属 post-service（帖子是它的表），agent 通过 Feign 调检索接口。
- 详见 `12-backend-microservice/feign-integration.md`。

### 3.4 user-service 需扩展的内部接口
- `GET /api/internal/users/{id}/public-profile`：返回公开统计（获赞/发帖数），用于「我现在差多少」场景。

## 四、数据集成

### 4.1 Agent 自有表（详见 `12-backend-microservice/database-schema.md`）
- `agent_sessions`
- `agent_messages`
- `agent_feedback`
- `agent_tool_calls`（trace 用）

### 4.2 向量库
- 帖子向量：由 post-service 在帖子创建/更新时同步到向量库（通过事件或定时任务）。
- 知识库向量：Agent 服务自己管理（帮助文档导入时向量化）。

### 4.3 数据同步策略
| 数据 | 同步方式 | 延迟 |
|------|----------|------|
| 帖子向量 | post-service 发事件 / 定时增量同步 | ≤ 5min |
| 帖子计数（点赞/评论） | 检索时实时 Feign 取 | 实时 |
| 知识库 | 后台手动/定时全量 | 立即 |

## 五、部署集成

### 5.1 docker-compose.yml 新增 agent-service
```yaml
agent-service:
  build:
    context: ./backend
    dockerfile: Dockerfile
    target: agent-service
  image: campusshare/agent-service:latest
  container_name: campushare-agent-service
  restart: unless-stopped
  mem_limit: 1024m
  ports:
    - "8083:8083"
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - TZ=Asia/Shanghai
    - JAVA_OPTS=-Xms512m -Xmx768m -javaagent:/app/opentelemetry-javaagent.jar
    - OTEL_SERVICE_NAME=agent-service
    - OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4317
    - LLM_API_KEY=${LLM_API_KEY}
    - LLM_BASE_URL=${LLM_BASE_URL}
  depends_on:
    mysql: { condition: service_healthy }
    redis: { condition: service_healthy }
    user-service: { condition: service_healthy }
    post-service: { condition: service_healthy }
  networks:
    - campushare-network
```

### 5.2 Dockerfile 新增 stage
在 `backend/Dockerfile` 参考 post-service 的 Stage，新增 `agent-service` target。

### 5.3 .env.example 新增
```
# LLM 配置
LLM_API_KEY=your-llm-api-key
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-chat
EMBEDDING_API_KEY=your-embedding-api-key
EMBEDDING_BASE_URL=https://api.deepseek.com
EMBEDDING_MODEL=bge-m3
```

### 5.4 Prometheus 抓取
`backend/docker/prometheus/prometheus.yml` 新增 agent-service target。

## 六、AGENT-WORKFLOW.md 需同步更新的内容
进入实现阶段后，必须在 `AGENT-WORKFLOW.md` 更新：
- 0.1 项目概述：新增 campushare-agent 服务说明。
- 0.3 端口映射：新增 8083。
- 3.1 请求链路：新增 /api/assistant/** 路由。
- 3.2 服务边界表：新增 agent-service 行。
- 5.2 网关白名单：确认 /api/assistant/** 不在白名单。
- 第六章微服务划分表：新增 agent-service。
- 重启命令表：新增「改 agent-service」对应重启命令。
