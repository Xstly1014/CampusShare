# 系统整体架构概览

- **日期：** 2026-06-27（初版） / 2026-06-29（微服务拆分后更新）
- **STAR — S (Situation)：**
  - 项目目标：校园资源共享平台 CampusShare，支持校园帖子、分类广场、私信、通知、创作者认证等社区功能
  - 预期规模：单校 1 万帖子级别压测验证，目标支撑 1000+ QPS
  - 团队规模：1 人全栈开发（AI Agent 辅助）
  - 时间约束：5 天内完成核心功能 + 微服务化 + 可观测性 + 性能优化
  - 已有技术栈约束：Java 17 + Spring Boot 3.2 + React 18 + MySQL 8 + Redis 7
- **STAR — T (Task)：** 交付一个前后端分离 + 微服务架构的校园社区平台，核心挑战是在小团队/短周期下完成微服务拆分、可观测性体系、性能调优三件事

---

## 整体组件图 (C4 Container 级别)

> 这是最重要的图。每次架构演进都要更新这张图。
> 当前状态：v1.5 — post-service 已从 user-service 拆分独立。

```mermaid
flowchart TB
    subgraph 客户端层
        A[Web 前端\nReact 18 + TypeScript + Vite\nTailwind CSS + Zustand]
    end

    subgraph 网关层
        C[Spring Cloud Gateway\nJWT 认证 + 静态路由 + 白名单\n端口 8080]
    end

    subgraph 服务层
        D[user-service\n用户/认证/关注/私信/通知/创作者\n端口 8081]
        E[post-service\n帖子/评论/分类/点赞/收藏/浏览历史\n端口 8082]
    end

    subgraph 数据层
        G[(MySQL 8\n共享数据库 按表所有权分服务\nUUID 主键)]
        H[(Redis 7\n缓存 + 会话)]
    end

    subgraph 可观测性层
        P[Prometheus\n指标采集 9090]
        GR[Grafana\n看板 3000]
        T[Tempo\n链路追踪 3200]
    end

    subgraph 外部依赖
        I[/SMTP 邮件服务\nQQ邮箱 587/]
        J[/文件存储\nDocker volume /app/uploads/]
    end

    A -->|Nginx 反代 80| C
    C -->|/api/posts/** /comments/** /categories/** /admin/**| E
    C -->|/api/** 兜底 含 /auth /users /follows /messages /notifications| D
    D <-->|OpenFeign 内部 API\n/api/internal/**| E
    D --> G
    E --> G
    D --> H
    E --> H
    D --> I
    D & E --> J
    D & E & C -.->|Micrometer + OTel Agent| P
    P --> GR
    D & E & C -.->|OTLP trace| T
    T --> GR
```

---

## 核心业务数据流图

> 业务场景：用户在分类广场发布一个帖子 → 其他用户浏览、点赞、评论 → 评论作者收到通知
> 这条链路覆盖了网关认证、跨服务 Feign 调用、通知投递三个核心环节。

```mermaid
sequenceDiagram
    actor U as 用户
    participant FE as 前端 React
    participant GW as Gateway 8080
    participant POST as post-service 8082
    participant USER as user-service 8081
    participant DB as MySQL
    participant RDS as Redis

    U->>FE: 发布帖子（带 categoryId）
    FE->>GW: POST /api/posts (Authorization: Bearer JWT)
    GW->>GW: JWT 解析验证 + 注入 X-User-Id/X-Username
    GW->>POST: 转发（StripPrefix=1 去掉 /api）
    POST->>DB: INSERT posts (id=UUID, author_id, category_id)
    POST-->>GW: 201 Created {postId}
    GW-->>FE: 返回帖子ID
    FE-->>U: Toast 发布成功

    Note over U,RDS: 另一用户浏览并点赞该帖子

    U->>FE: 点击点赞
    FE->>GW: POST /api/posts/{id}/like
    GW->>POST: 转发
    POST->>DB: INSERT post_likes (uk_post_user 防重)
    POST->>DB: UPDATE posts SET like_count = like_count + 1
    POST->>USER: Feign POST /api/internal/notifications（发送 LIKE 通知）
    USER->>DB: INSERT notifications (user_id=作者, sender_id=点赞者)
    POST-->>GW: 200 OK
    GW-->>FE: 返回成功
    FE-->>U: 点赞高亮

    Note over USER: 作者下次打开通知中心
    U->>FE: 打开通知收纳篮
    FE->>GW: GET /api/notifications/feed
    GW->>USER: 转发
    USER->>USER: 按通知偏好过滤（disabled 类型 unreadCount=0）
    USER->>DB: SELECT notifications GROUP BY type
    USER->>RDS: 读取/写入未读计数缓存
    USER-->>GW: 返回分类收纳篮列表
    GW-->>FE: 通知数据
    FE-->>U: 渲染通知中心
```

---

## 关键约束与假设

| 约束维度 | 当前设定 | 来源 / 理由 |
|----------|----------|-------------|
| 最大 QPS | 1151 req/s（A/B 压测验证） | 4 核 8G + 复合索引优化后实测 |
| 数据留存 | 全量保留，逻辑删除（deleted=1） | 校园内容需可审计，不做物理删除 |
| 延迟 SLA | P95 < 100ms（核心列表接口） | 压测验证 P95=70ms |
| 预算上限 | 单台 4 核 8G 虚拟机 | 学生项目，控制成本 |
| 技术栈锁定 | Java 17 + Spring Boot 3.2 + MySQL 8 | 团队已有经验，生态成熟 |
| 主键策略 | 核心业务表 UUID（VARCHAR(36)），关联表自增 INT | UUID 便于分布式生成，关联表追求插入性能 |
| 服务间通信 | OpenFeign 同步调用（无消息队列） | 当前规模下 MQ 复杂度 > 收益 |

---

## 被排除的架构方案

| 方案 | 为什么不适用 |
|------|-------------|
| 完整微服务化（v2.0，每域一服务） | 团队仅 1 人，过早拆分引入分布式事务/服务发现复杂度，当前 v1.5 拆 post-service 已够用 |
| Serverless 部署 | 冷启动延迟不可接受 + Spring Boot 启动重，不适合 FaaS |
| Event Sourcing | 校园社区场景写多读多，事件溯源复杂度 > 收益 |
| 消息队列解耦通知 | 当前通知量级（单校几千条）下，同步 Feign + DB 写入足够；MQ 增加运维负担 |
| 跨服务 JOIN | 违反微服务边界铁律，禁止跨服务直接访问其他服务的表 |

---

## 已知风险 & 演进方向

1. **Feign 同步调用耦合**：user-service 依赖 post-service 的 Feign 调用，若 post-service 宕机，通知发送会失败。演进方向：引入本地消息表 + 异步重试，或引入 MQ。
2. **共享 MySQL 单点**：所有微服务共享一个 MySQL 实例（按表所有权逻辑隔离），未做读写分离/主从。数据量增长到百万级后需拆分独立数据库或读写分离。
3. **无分布式事务**：跨服务操作（如发帖 + 通知）无事务保证，依赖业务层补偿。当前可接受，后续需引入 Saga 或本地消息表。
4. **Tempo 健康检查绕过**：Tempo 2.5 配置最小化后健康检查端点空指针 panic，临时禁用 healthcheck。需完善 Tempo 配置恢复正式健康检查。
5. **缓存一致性**：Redis 缓存采用 TTL 过期策略（5 分钟），未做主动失效。学校帖子数等非实时数据可接受短窗口不一致。
