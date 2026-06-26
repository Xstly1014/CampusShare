# CampusShare 技术方案设计文档

> **文档版本**: v1.0
> **创建日期**: 2026-06-27
> **文档状态**: 迭代中

---

## 一、技术选型

### 1.1 整体架构

前后端分离 + 微服务架构，初期单模块部署，后续根据业务规模拆分。

```
┌──────────────────────────────────────────────────┐
│                    客户端                         │
│         (H5 / 微信浏览器 / 未来小程序)            │
└──────────────────────┬───────────────────────────┘
                       │ HTTP/HTTPS
┌──────────────────────▼───────────────────────────┐
│                API Gateway (Spring Cloud Gateway)│
│       路由转发 / 鉴权 / 限流 / 日志 / CORS        │
└────────────┬─────────────────┬───────────────────┘
             │                 │
    ┌────────▼───────┐  ┌──────▼────────┐
    │  用户服务       │  │  帖子服务      │  ...
    │ campushare-user│  │ (待独立)       │
    │ - 注册登录     │  │ - 发帖/浏览    │
    │ - 用户信息     │  │ - 评论/互动    │
    │ - 文件上传     │  │ - 搜索推荐     │
    └────────┬───────┘  └───────┬────────┘
             │                  │
    ┌────────▼──────────────────▼────────┐
    │          MySQL / Redis             │
    │    (主从 + 读写分离，待扩展)       │
    └───────────────────────────────────┘
```

### 1.2 技术栈清单

| 分类 | 技术选型 | 版本 | 说明 |
|------|---------|------|------|
| **前端框架** | React | 18.x | 组件化，生态丰富 |
| **前端语言** | TypeScript | 5.x | 类型安全 |
| **构建工具** | Vite | 5.x | 快速构建，HMR |
| **样式方案** | Tailwind CSS | 3.x | 原子化 CSS |
| **路由管理** | React Router DOM | 6.x | 声明式路由 |
| **状态管理** | Zustand | 4.x | 轻量状态管理 |
| **HTTP 客户端** | Axios | 1.x | 请求拦截/响应拦截 |
| **后端框架** | Spring Boot | 3.4.x | 社区成熟 |
| **微服务框架** | Spring Cloud | 2023.x | 服务治理 |
| **API 网关** | Spring Cloud Gateway | - | 统一入口 |
| **ORM 框架** | MyBatis Plus | 3.5.x | 高效 CRUD |
| **数据库** | MySQL | 8.0 | 主流关系型数据库 |
| **缓存** | Redis | 7.x | 验证码/计数/Session |
| **认证授权** | JWT + Spring Security | - | 无状态认证 |
| **密码加密** | BCrypt | - | 安全强度 10 |
| **容器化** | Docker + Docker Compose | - | 一致部署 |
| **版本控制** | Git + GitHub | - | 代码管理 |
| **API 文档** | SpringDoc OpenAPI | 2.x | 自动生成（待接入） |

### 1.3 选型理由

**前端选择 React + TypeScript + Vite**：
- 组件化开发，复用性强
- TypeScript 提供类型安全，减少运行时错误
- Vite 开发体验优秀，构建速度快

**后端选择 Spring Boot 3 + Java 17**：
- 生态成熟，企业级稳定性
- Spring Cloud 完整的微服务解决方案
- MyBatis Plus 简化数据库操作

**缓存选择 Redis**：
- 高性能 KV 存储
- 支持验证码、计数、限流等多种场景

---

## 二、项目结构

### 2.1 仓库结构

```
CampusShare/
├── frontend/                    # 前端项目
│   ├── src/
│   │   ├── pages/              # 页面组件
│   │   ├── components/         # 公共组件
│   │   ├── services/           # API 服务层
│   │   ├── store/              # 状态管理
│   │   ├── utils/              # 工具函数
│   │   ├── types/              # TypeScript 类型定义
│   │   └── App.tsx             # 应用入口
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts
│   └── Dockerfile
│
├── backend/                     # 后端项目
│   ├── pom.xml                 # 父工程 POM
│   ├── campushare-common/      # 公共模块（工具类/常量/异常）
│   ├── campushare-user/        # 用户服务（含帖子、文件）
│   ├── campushare-gateway/     # API 网关服务
│   └── docker/
│       └── mysql/init.sql      # 数据库初始化脚本
│
├── docs/                        # 项目文档
│   ├── PRD.md                  # 产品需求文档
│   ├── database-design.md      # 数据库设计文档
│   ├── api-docs.md             # API 接口文档
│   ├── tech-design.md          # 技术方案设计文档
│   └── deployment.md           # 部署运维文档
│
├── docker-compose.yml           # 服务编排
├── README.md                    # 项目说明
└── .gitignore
```

### 2.2 后端模块划分

| 模块 | 职责 | 状态 |
|------|------|------|
| `campushare-common` | 公共工具、常量定义、异常处理、统一响应 | ✅ 已实现 |
| `campushare-user` | 用户认证、用户信息、帖子、评论、文件上传 | 🚧 部分实现 |
| `campushare-gateway` | API 网关、路由转发、鉴权、限流、CORS | ✅ 已实现 |
| `campushare-post` | 帖子服务（后续从 user 拆分） | ⏳ 规划中 |
| `campushare-search` | 搜索服务（Elasticsearch） | ⏳ 规划中 |

---

## 三、核心技术方案

### 3.1 认证与鉴权方案

#### 3.1.1 JWT Token 机制

采用双 Token 策略：

```
用户登录 → 服务器签发
├── Access Token  → 有效期 2小时 → 用于接口鉴权
└── Refresh Token → 有效期 7天  → 用于刷新 Access Token
```

**Token 结构**：
```json
{
  "sub": "userId",
  "username": "testuser",
  "roles": ["USER"],
  "iat": 1719456789,
  "exp": 1719463989,
  "type": "access"
}
```

**刷新流程**：
1. 前端发现 Access Token 即将过期（剩余 < 5分钟）
2. 使用 Refresh Token 请求 `/auth/refresh`
3. 服务器校验 Refresh Token，签发新的 Access Token
4. 如 Refresh Token 也过期，提示重新登录

#### 3.1.2 网关鉴权

网关层统一处理鉴权：

```
请求到达网关
  │
  ├── 白名单路径（/auth/**, /files/**, 帖子列表等）→ 直接转发
  │
  └── 需要登录的路径
       ├── Token 有效 → 解析用户信息 → 转发到下游服务
       └── Token 无效/过期 → 返回 401
```

### 3.2 缓存方案

#### 3.2.1 缓存层级

```
L1: 浏览器本地缓存 (localStorage/sessionStorage)
    └── 用户基本信息、非敏感配置

L2: Redis 分布式缓存
    ├── 验证码（5分钟）
    ├── Token 黑名单
    ├── 浏览计数（异步落库）
    └── 热点帖子详情（30分钟）

L3: MySQL 持久化存储
    └── 核心业务数据
```

#### 3.2.2 缓存策略

| 场景 | 策略 | 一致性 |
|------|------|--------|
| 验证码 | Cache Aside（Redis 唯一数据源） | 强一致 |
| 用户信息 | Cache Aside，先更 DB 再删缓存 | 最终一致 |
| 帖子详情 | Cache Aside，热点数据缓存 | 最终一致 |
| 浏览计数 | Write Back（Redis 计数，异步落 DB） | 最终一致 |
| 收藏/点赞 | Double Write（DB + Redis 双写） | 最终一致 |

### 3.3 文件存储方案

#### 3.3.1 当前方案（本地存储）

```
用户上传文件 → 后端校验 → 本地存储 (backend/uploads/yyyyMMdd/)
    │
    ├── 文件名：UUID + 原始扩展名
    ├── 按日期分目录，避免单目录文件过多
    └── 通过网关 /files/** 静态资源映射访问
```

**适用阶段**：开发测试、小规模部署

#### 3.3.2 演进方案（对象存储）

用户量增长后迁移到对象存储（OSS/COS/S3）：

```
用户上传 → 后端签发预签名 URL → 前端直传 OSS
    │
    └── 上传成功后回调后端记录文件元信息
```

**优势**：
- 减轻服务器带宽压力
- 支持 CDN 加速
- 存储容量弹性扩展
- 图片可配合图片服务做缩放、水印

### 3.4 搜索方案

#### 3.4.1 当前方案（MySQL LIKE）

帖子标题和内容搜索使用 MySQL `LIKE` + 全文索引：

```sql
-- 模糊搜索
SELECT * FROM posts 
WHERE title LIKE '%关键词%' OR content LIKE '%关键词%'
  AND school_id = ? AND status = 1
ORDER BY create_time DESC
LIMIT 20;
```

**适用阶段**：数据量 < 100w

#### 3.4.2 演进方案（Elasticsearch）

数据量增长后引入 Elasticsearch：

```
帖子发布/更新 → 同步到 ES（双写 + 定时补偿）
用户搜索 → ES 检索 → 返回帖子 ID 列表 → DB 补全详情
```

**优势**：
- 中文分词，搜索更精准
- 支持相关性排序
- 支持聚合筛选
- 性能更优

### 3.5 限流方案

网关层实现限流：

```
用户请求 → 网关限流检查
    │
    ├── 全局限流：QPS 1000
    ├── IP 限流：60次/分钟
    └── 接口限流：
        ├── 发送验证码：1次/分钟/账号
        ├── 发帖：10次/小时/用户
        └── 评论：30次/小时/用户
```

**实现方式**：
- Redis + Lua 脚本实现滑动窗口限流
- 超出限流返回 429 Too Many Requests

---

## 四、安全方案

### 4.1 数据安全

| 场景 | 方案 |
|------|------|
| 密码存储 | BCrypt 加密，强度 10，不可逆 |
| 敏感数据传输 | HTTPS 加密传输 |
| SQL 注入 | MyBatis Plus 参数化查询 |
| XSS 防护 | 前端输出转义，后端输入过滤 |
| CSRF 防护 | JWT Token 机制，SameSite Cookie |
| 文件上传 | 白名单校验扩展名 + MIME 类型，禁止可执行文件 |

### 4.2 接口安全

- **认证**：所有非公开接口需携带有效 Token
- **授权**：基于角色的访问控制（RBAC）
- **幂等性**：写操作接口支持幂等（基于请求 ID）
- **防刷**：接口限流，防止恶意请求

### 4.3 隐私保护

- 用户手机号/邮箱脱敏展示
- 浏览记录保留期限（可配置，默认 30 天）
- 用户可申请注销账号，数据按法规保留

---

## 五、性能优化方案

### 5.1 前端优化

| 优化点 | 方案 |
|--------|------|
| 首屏加载 | 路由懒加载、代码分割、Gzip 压缩 |
| 图片优化 | WebP 格式、懒加载、响应式图片 |
| 列表性能 | 虚拟滚动（长列表时）、分页加载 |
| 缓存策略 | 接口数据缓存、静态资源 CDN |
| 构建优化 | Tree Shaking、依赖优化 |

### 5.2 后端优化

| 优化点 | 方案 |
|--------|------|
| 数据库 | 合理索引、SQL 优化、读写分离（后期） |
| 缓存 | 多级缓存、热点数据预热 |
| 异步 | 浏览计数、消息通知异步处理 |
| 连接池 | HikariCP 数据库连接池调优 |
| JVM | 参数调优、GC 日志监控 |

---

## 六、可观测性体系（Observability）

### 6.1 总体架构

采用 **Metrics + Traces + Logs** 可观测性三支柱方案：

```
┌─────────────────────────────────────────────────────────────┐
│                    Grafana (统一可视化)                       │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────┐     │
│  │ Metrics  │  │   Traces     │  │   Logs (待接入)    │     │
│  │ Dashboard│  │   Explore    │  │   (Loki/ELK)      │     │
│  └─────▲────┘  └──────▲───────┘  └────────▲──────────┘     │
└────────┼──────────────┼─────────────────────┼───────────────┘
         │              │                     │
    ┌────▼────┐    ┌────▼─────┐         ┌─────▼──────┐
    │ Prometheus│  │   Tempo  │         │  Loki/ES   │
    │ (指标存储)│  │ (链路存储)│         │ (日志存储)  │
    └─────▲────┘  └─────▲─────┘         └─────▲──────┘
          │             │                      │
          └─────────────┼──────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        │  Spring Boot 应用服务          │
        │  ┌─────────────────────────┐  │
        │  │ Micrometer (指标埋点)   │  │
        │  │ OpenTelemetry (链路)    │  │
        │  │ SLF4J/Logback (日志)    │  │
        │  └─────────────────────────┘  │
        └───────────────────────────────┘
```

### 6.2 Metrics (指标监控)

#### 6.2.1 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| 指标采集 | Micrometer + Spring Boot Actuator | 标准指标门面，自动埋点 |
| 指标存储 | Prometheus | 时序数据库，Pull 模式 |
| 可视化 | Grafana | 统一仪表盘 |
| 告警 | Prometheus Alertmanager + (通知渠道) | 告警规则与通知 |

#### 6.2.2 监控指标

| 层级 | 指标 | 来源 |
|------|------|------|
| **基础设施** | CPU、内存、磁盘、网络 | Node Exporter（待接入） |
| **应用层** | QPS、错误率、P50/P95/P99 延迟 | `http_server_requests_seconds_*` |
| **JVM** | 堆内存、线程数、GC 次数/耗时 | `jvm_*` |
| **业务层** | 注册量、登录量、发帖量 | 自定义 Counter（待接入） |
| **数据库** | 连接数、活跃数、等待数 | HikariCP `hikaricp_*` |
| **缓存** | 命中率、内存使用率、命令数 | Lettuce 自动埋点 |
| **网关** | 路由延迟、限流次数 | Spring Cloud Gateway Metrics |

#### 6.2.3 告警规则

| 告警项 | 阈值 | 级别 | 通知渠道 |
|--------|------|------|---------|
| 服务不可用 | 连续 3 次健康检查失败 | P0 | 电话 + 钉钉 |
| 接口错误率 | > 5% 持续 5 分钟 | P1 | 钉钉 |
| 接口 P95 耗时 | > 2s 持续 5 分钟 | P1 | 钉钉 |
| JVM 堆内存 | > 80% 持续 5 分钟 | P2 | 邮件 |
| CPU 使用率 | > 80% 持续 10 分钟 | P2 | 邮件 |
| 磁盘使用率 | > 80% | P2 | 邮件 |
| Redis 命中率 | < 80% | P3 | 邮件 |
| 请求量突增 | > 2 倍历史均值 | P3 | 邮件 |

#### 6.2.4 关键指标优化点

重点关注以下可优化的指标，用于指导性能优化：

| 指标 | 优化方向 |
|------|---------|
| `http_server_requests_seconds` (按 URI) | 定位慢接口，针对性优化 |
| `jvm_gc_pause_seconds` | GC 调优、内存优化 |
| `hikaricp_connections_pending` | 连接池调优、SQL 优化 |
| `http_server_requests_seconds` (按 method+status) | 错误率分析 |
| `system_cpu_usage` / `process_cpu_usage` | CPU 瓶颈分析 |

### 6.3 Traces (链路追踪)

#### 6.3.1 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| 埋点方式 | OpenTelemetry Java Agent | 无侵入自动埋点 |
| 链路存储 | Grafana Tempo | 轻量级，与 Grafana 无缝集成 |
| 查询展示 | Grafana Explore | 统一查询界面 |
| 采样策略 | Parent-based AlwaysOn (开发环境) | 生产环境按需采样 |

#### 6.3.2 自动埋点覆盖范围

OpenTelemetry Java Agent 自动覆盖：

- ✅ **Spring MVC** — HTTP 请求入口
- ✅ **Spring WebClient / RestTemplate** — HTTP 客户端调用
- ✅ **MyBatis / JDBC** — SQL 执行（含慢 SQL 定位）
- ✅ **Redis (Lettuce / Jedis)** — Redis 命令
- ✅ **Logback / Log4j** — 日志自动注入 Trace ID
- ✅ **Spring Scheduling** — 定时任务
- ✅ **Kafka** — 消息队列（待接入 Kafka 后启用）

#### 6.3.3 链路追踪的应用场景

| 场景 | 说明 |
|------|------|
| **慢调用定位** | 找到 P95 慢请求的具体耗时环节（网络/SQL/业务逻辑） |
| **错误根因分析** | 通过 Trace ID 串联全链路日志，快速定位错误源 |
| **依赖梳理** | 自动生成服务依赖图，了解调用关系 |
| **重试分析** | 观察下游服务重试次数和效果 |
| **性能优化** | 识别瓶颈环节，针对性优化 |

#### 6.3.4 自定义埋点

关键业务逻辑可手动添加 Span：

```java
@Autowired
private Tracer tracer;

public void processOrder(String orderId) {
    Span span = tracer.spanBuilder("processOrder")
        .setAttribute("order.id", orderId)
        .setAttribute("order.type", "resource")
        .startSpan();
    try (Scope scope = span.makeCurrent()) {
        // 业务逻辑
        validateOrder(orderId);
        createPost(orderId);
        notifyUser(orderId);
    } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR, e.getMessage());
        throw e;
    } finally {
        span.end();
    }
}
```

### 6.4 Logs (日志) - 规划中

#### 6.4.1 演进路线

| 阶段 | 方案 | 说明 |
|------|------|------|
| v1.0 | Docker logs + 本地文件 | 开发测试阶段 |
| v1.1 | Grafana Loki | 轻量日志聚合，与 Tempo/Grafana 无缝联动 |
| v2.0 | ELK (Elasticsearch + Logstash + Kibana) | 大规模，复杂查询 |

#### 6.4.2 日志规范

- **统一格式**：时间 + 级别 + 线程 + TraceID + 类名 + 消息
- **日志分级**：ERROR / WARN / INFO / DEBUG
- **关键日志**：注册、登录、发帖、上传文件、支付等
- **异常日志**：完整堆栈 + 请求上下文 + Trace ID
- **保留策略**：7 天热数据，30 天归档

### 6.5 采样策略

| 环境 | Trace 采样 | 说明 |
|------|-----------|------|
| 开发环境 | 100% (AlwaysOn) | 完整追踪，方便调试 |
| 测试环境 | 100% (AlwaysOn) | 完整追踪，便于压测分析 |
| 生产环境 | 10% ~ 100% 动态调整 | 根据流量和存储成本调整 |

生产环境建议：
- 错误请求：100% 采样
- 慢请求（> 1s）：100% 采样
- 正常请求：10% 采样
- 关键业务（支付等）：100% 采样

---

## 七、扩展性设计

### 7.1 水平扩展

- **无状态服务**：所有业务服务无状态，可水平扩展
- **会话管理**：JWT + Redis，多实例共享
- **数据库**：读写分离 → 分库分表
- **缓存**：Redis Cluster 集群模式

### 7.2 服务拆分路线

```
v1.0: 单体（user 服务包含所有业务）
  │
v1.5: 拆分为 user + post 两个服务
  │
v2.0: 微服务架构（user / post / comment / search / message / ...）
```

**拆分原则**：
- 按业务领域拆分（DDD）
- 高内聚、低耦合
- 数据一致性要求高的暂不拆分

---

## 八、开发规范

### 8.1 分支管理

采用 Git Flow 简化版：

```
master  ──────────────●──────────────●───
  (主分支，可发布)     │              │
                        │              │
develop ────●─────●───────●─────●────────
  (开发分支)   │     │       │     │
              │     │       │     │
feature/x ────┘     │       │     │
  (功能分支)         │       │     │
                    │       │     │
hotfix/x ───────────┘       │     │
  (紧急修复)                │     │
                            │     │
release/v1.0 ──────────────┘     │
  (发布分支)                      │
                                  │
release/v1.1 ────────────────────┘
```

### 8.2 代码规范

**前端**：
- ESLint + Prettier 代码格式化
- 组件命名：PascalCase
- 变量/函数：camelCase
- 常量：UPPER_SNAKE_CASE
- 文件命名：组件用 PascalCase，工具函数用 camelCase

**后端**：
- 阿里巴巴 Java 开发手册
- 类名：PascalCase
- 方法/变量：camelCase
- 常量：UPPER_SNAKE_CASE
- 包名：全小写

### 8.3 Commit Message 规范

采用 Conventional Commits：

```
<type>(<scope>): <subject>

feat(user): 添加用户注册接口
fix(auth): 修复验证码过期时间错误
docs(readme): 更新技术栈说明
style: 格式化代码
refactor(post): 重构帖子服务
test: 添加单元测试
chore: 更新依赖
```

---

## 九、版本规划

| 版本 | 主要内容 | 预计周期 |
|------|---------|---------|
| v1.0 | 基础功能：注册登录、学校列表、发帖、评论、个人资料 | 当前 |
| v1.1 | 互动完善：收藏/点赞/浏览历史后端实现、消息通知 | 2 周 |
| v1.2 | 创作者体系：认证申请、积分激励、关注功能 | 3 周 |
| v1.5 | 服务拆分：post 服务独立、ES 搜索 | 4 周 |
| v2.0 | 微服务化：完整微服务架构、多端支持 | 8 周 |

---

## 十、风险与应对

| 风险 | 概率 | 影响 | 应对方案 |
|------|------|------|---------|
| 用户量增长导致性能瓶颈 | 中 | 高 | 提前做好压测，预留扩容方案 |
| 数据安全/隐私合规 | 中 | 高 | 严格遵守法规，定期安全审计 |
| 内容审核风险 | 高 | 中 | 接入内容审核服务，人工审核兜底 |
| 技术债务累积 | 中 | 中 | 定期代码重构，保持代码质量 |
| 第三方依赖风险 | 低 | 中 | 核心依赖备选方案，多云策略 |
