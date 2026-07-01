# 后端当前技术债务与问题总结

> **文档版本**: v1.0
> **创建日期**: 2026-07-01
> **说明**: 本文档详细列出后端现有技术选型中落后、草率、存在风险的部分，作为升级的输入。每个问题都包含：问题描述、影响、严重程度、代码位置参考。

---

## 一、微服务基础设施问题（P0）

### 1.1 没有服务注册与发现中心

**问题描述**：当前服务间调用通过硬编码 URL（`http://user-service:8081`）实现，在 application-docker.yml 中固定配置。没有使用 Nacos/Eureka/Consul 等服务注册中心。

**当前实现**：
```yaml
# application-docker.yml 中硬编码
feign:
  user:
    url: http://user-service:8081
```

**影响**：
- 服务无法水平扩展（多个实例无法负载均衡）
- 服务地址变更需要修改所有调用方配置
- 没有服务健康检查自动剔除机制
- 无法实现灰度发布、流量染色等高级特性

**严重程度**：🔴 P0

**涉及文件**：
- [UserFeignClient.java](file:///E:/workspace_work/CampusShare/backend/campushare-post/src/main/java/com/campushare/post/feign/UserFeignClient.java)
- [PostFeignClient.java](file:///E:/workspace_work/CampusShare/backend/campushare-user/src/main/java/com/campushare/user/feign/PostFeignClient.java)

---

### 1.2 没有配置中心

**问题描述**：所有配置写在 application.yml 和 application-docker.yml 中，通过环境变量传递少量配置。没有使用 Nacos Config/Apollo/Spring Cloud Config 等配置中心。

**影响**：
- 配置修改需要重启服务
- 无法动态调整限流阈值、线程池大小、日志级别等运行时参数
- 多环境配置管理混乱（本地/Docker/生产靠 profile 区分）
- 敏感配置（数据库密码、JWT密钥）明文存储在配置文件中

**严重程度**：🔴 P0

---

### 1.3 没有熔断降级与限流框架

**问题描述**：Feign 调用没有熔断保护，gateway 没有实现真正的限流（代码中 AgentRateLimiter 仅在 agent-service 存在，主业务服务没有）。没有使用 Sentinel/Resilience4j 做系统化的熔断降级限流。

**当前问题**：
- 一个服务挂了会导致级联故障（post-service 依赖 user-service，user-service 挂了 post-service 也无法响应）
- 没有接口级别的 QPS 限流，容易被恶意刷接口
- Feign 调用失败没有降级策略，直接抛异常给用户
- 没有慢调用熔断、异常比例熔断等保护机制

**严重程度**：🔴 P0

**涉及文件**：
- [JwtAuthenticationFilter.java](file:///E:/workspace_work/CampusShare/backend/campushare-gateway/src/main/java/com/campushare/gateway/filter/JwtAuthenticationFilter.java)（网关无限流逻辑）

---

### 1.4 共享数据库（伪微服务）

**问题描述**：虽然划分了 user-service 和 post-service，但它们共享同一个 MySQL 数据库实例，只是按表所有权做了逻辑划分。虽然规范禁止跨服务 JOIN 和跨表写入，但物理上是同一个库。

**当前状态**：
- 所有服务连接同一个 MySQL `campushare` 数据库
- 只是约定"不跨服务访问表"，没有物理隔离
- 无法对不同服务做独立的数据库扩容、优化、备份

**影响**：
- 数据库成为单点瓶颈
- 一个服务的慢SQL可能影响所有服务
- 无法按服务独立扩容数据库资源
- 存在违规跨服务JOIN的风险（虽然规范禁止，但没有物理阻止）

**严重程度**：🟡 P1（当前阶段可接受，但升级路径要明确）

---

## 二、消息队列缺失问题（P0）

### 2.1 服务间耦合严重，同步调用过多

**问题描述**：点赞、收藏、评论、关注等操作需要同步调用通知服务发送通知，发帖后需要同步更新分类计数等。这些都是同步 Feign 调用，没有通过消息队列异步解耦。

**典型场景**：
- 用户发帖 → 需要更新分类帖子计数 → 同步操作（如果计数更新失败，发帖也失败？）
- 用户评论 → 需要发送通知给帖子作者 → Feign同步调用 → 如果通知服务超时，评论也失败
- 用户点赞 → 需要更新点赞计数 + 发送通知 → 多个同步操作

**影响**：
- 接口响应时间变长（串行调用多个服务）
- 级联失败风险高（一个下游服务不可用导致整个链路失败）
- 无法削峰填谷（突发流量直接打数据库）
- 无法实现最终一致性（例如通知发送失败无法重试）

**严重程度**：🔴 P0

---

### 2.2 没有异步处理能力

**问题描述**：邮件发送、数据统计、缓存预热、日志审计等适合异步处理的任务，当前都是同步执行或者没有实现。

**示例问题**：
- [UserServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-user/src/main/java/com/campushare/user/service/impl/UserServiceImpl.java) 中发送验证码是同步调用邮件服务，如果邮件服务慢，接口响应就慢
- 没有异步事件机制来处理"用户注册后发送欢迎邮件"、"发帖后更新推荐索引"等场景

**严重程度**：🟡 P1

---

## 三、数据层问题

### 3.1 主键选型混乱

**问题描述**：主键类型不统一：users/posts/comments/messages/notifications 等用 VARCHAR(36) UUID，而关联表（post_likes/post_stars/follows等）用 INT 自增。UUID 作为主键存在性能问题。

**问题分析**：
- UUID 是无序的，InnoDB 主键索引会频繁页分裂，插入性能差
- UUID 占 36 字节（VARCHAR），比 BIGINT（8字节）大很多，二级索引占用空间大
- 没有使用雪花算法（Snowflake）等有序分布式ID生成方案
- 自增ID在分库分表时会有问题

**当前代码**：实体类使用 `@TableId(type = IdType.ASSIGN_UUID)` 

**影响**：
- 写入性能随数据量增长下降
- 索引占用空间大，缓存命中率低
- 未来分库分表困难

**严重程度**：🟡 P1（主键改造影响大，需要谨慎规划迁移方案）

---

### 3.2 没有数据库迁移工具

**问题描述**：数据库 schema 变更靠手动执行 SQL 脚本（V*.sql 文件），没有使用 Flyway 或 Liquibase 做版本化管理。

**当前问题**：
- 新环境部署需要手动执行 init.sql + 所有迁移脚本
- 无法自动回滚
- 无法确认当前数据库版本
- 多人开发时容易出现迁移脚本冲突
- AGENT-WORKFLOW.md 中记录了"新增表手动执行CREATE TABLE"的繁琐流程

**严重程度**：🟡 P1

---

### 3.3 没有分布式锁

**问题描述**：需要并发控制的场景没有使用分布式锁，例如：
- 用户注册（防止同一邮箱/手机号并发注册）
- 点赞/收藏（防止重复点赞，虽然数据库有唯一约束，但应用层没有拦截）
- 验证码发送频率控制（当前用Redis计数，但不是原子操作）
- 创作者认证申请（防止重复提交）

**当前实现问题**：
- [UserServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-user/src/main/java/com/campushare/user/service/impl/UserServiceImpl.java) 中发送验证码的计数是先get再set，存在并发竞态条件
- 没有使用 Redisson 等成熟的分布式锁框架

**严重程度**：🟡 P1

---

### 3.4 多级缓存体系缺失

**问题描述**：当前只有 Redis 作为远程缓存，没有本地缓存（Caffeine/Guava Cache）作为L1缓存。对于不经常变更的数据（如分类列表、学校列表），每次都查 Redis 有网络开销。

**缓存策略问题**：
- 没有缓存预热机制
- 没有缓存穿透/击穿/雪崩的系统化防护
- 没有缓存一致性的正式方案（只是"先写DB再删缓存"，没有重试和补偿）
- 热点Key没有本地缓存兜底

**严重程度**：🟡 P1

---

### 3.5 没有读写分离和分库分表规划

**问题描述**：所有读写请求都打在主库上，没有读写分离。单表数据量增长后（如帖子表、通知表）没有分库分表方案。

**影响**：
- 读请求多时主库压力大
- 单表数据量超过千万后查询性能下降
- 无法横向扩展数据库读能力

**严重程度**：🟢 P2（当前数据量小可暂不实施，但架构上要预留）

---

### 3.6 全文搜索依赖 MySQL FULLTEXT 索引

**问题描述**：tech-design.md 中提到搜索用 MySQL FULLTEXT 索引，这在中文场景下效果很差（中文分词问题），且性能不佳。没有使用 Elasticsearch/OpenSearch 做专业的全文检索。

**严重程度**：🟡 P1（搜索功能还未完善，但基础设施需要提前准备）

---

## 四、安全问题

### 4.1 JWT 实现存在缺陷

**问题描述**：
- JWT 密钥硬编码在配置文件中（`CampusShare-Secret-Key...`），且是对称加密（HS256）
- 没有 Token 黑名单机制（虽然 Redis 存了 token，但没有看到主动失效逻辑，用户改密码/登出后旧 Token 仍可用）
- Refresh Token 没有轮换机制（Refresh Token 重复使用）
- 没有 JWT 签名密钥的定期轮换方案
- 网关 JWT 过滤器每次请求都解析 JWT（没有缓存解析结果，虽然 JWT 解析很快但可以优化）

**涉及文件**：
- [JwtUtils.java (gateway)](file:///E:/workspace_work/CampusShare/backend/campushare-gateway/src/main/java/com/campushare/gateway/utils/JwtUtils.java)
- [JwtUtils.java (common)](file:///E:/workspace_work/CampusShare/backend/campushare-common/src/main/java/com/campushare/common/utils/JwtUtils.java)

**严重程度**：🔴 P0（安全问题）

---

### 4.2 权限模型过于简单

**问题描述**：当前只有 USER/ADMIN 两个角色，RBAC 模型不完整。没有细粒度的权限控制（如谁能删帖、谁能审核创作者、谁能管理后台等），权限判断散落在代码中。

**当前实现**：
- 用户表 `role` 字段是字符串，存 "USER"/"ADMIN"
- 没有权限表、角色权限关联表
- 接口权限校验有的在网关（白名单），有的在Service层硬编码判断

**严重程度**：🟡 P1

---

### 4.3 没有接口防刷和风控机制

**问题描述**：除了验证码发送频率有简单计数外，其他接口没有防刷机制：
- 登录接口没有暴力破解防护（没有失败次数限制、没有验证码拦截）
- 注册接口没有IP限流
- 发帖/评论接口没有频率限制
- 没有恶意请求识别（如异常高频IP、异常User-Agent）

**严重程度**：🟡 P1

---

### 4.4 没有审计日志

**问题描述**：关键操作（登录、发帖、删帖、审核、修改密码等）没有安全审计日志，无法追溯谁在什么时候做了什么。

**严重程度**：🟡 P1

---

### 4.5 CORS 配置过于宽松

**问题描述**：网关 CORS 配置 `allowedOriginPatterns: "*"`，允许任何来源访问。生产环境应该限制为具体域名。

**涉及文件**：
- [application.yml (gateway)](file:///E:/workspace_work/CampusShare/backend/campushare-gateway/src/main/resources/application.yml#L38-L50)

**严重程度**：🟡 P1（开发阶段方便，上线前必须收紧）

---

## 五、工程化问题

### 5.1 缺少参数校验框架

**问题描述**：Controller 层没有使用 `@Valid` + JSR-380（Hibernate Validator）做参数校验，参数校验逻辑散落在 Service 层，代码重复且容易遗漏。

**示例**：登录接口没有校验用户名长度、密码复杂度；注册接口没有校验邮箱格式等（可能有部分校验，但没有用框架统一处理）。

**严重程度**：🟡 P1

---

### 5.2 API 文档缺失

**问题描述**：虽然 tech-design.md 提到 SpringDoc OpenAPI "待接入"，但实际没有集成。接口文档需要手动维护，没有自动生成的 Swagger/OpenAPI 文档。

**严重程度**：🟡 P1（影响前后端协作效率）

---

### 5.3 没有单元测试和集成测试

**问题描述**：pom.xml 中虽然引入了 `spring-boot-starter-test`，但 src/test 目录下几乎没有测试用例。没有单元测试、集成测试、接口测试。

**影响**：
- 重构没有安全保障，改代码容易改出bug
- 无法做回归测试
- CI/CD 无法自动化验证

**严重程度**：🔴 P0

---

### 5.4 日志体系不完善

**问题描述**：
- 没有日志聚合（当前只有 Docker logs，没有 ELK/Loki）
- 日志格式不统一，没有 TraceId 透传（虽然 OTel Agent 能注入，但代码中没有主动使用 MDC）
- 没有区分业务日志、访问日志、错误日志、审计日志
- 没有日志采样、脱敏策略（密码、身份证号等可能打到日志中）
- 关键操作日志缺失（谁在什么时候登录、发帖失败原因等）

**严重程度**：🟡 P1

---

### 5.5 多环境配置管理混乱

**问题描述**：本地开发用 `application.yml`（硬编码 localhost），Docker 用 `application-docker.yml`，没有明确的 dev/test/staging/prod 多环境配置方案。配置文件命名和profile使用不规范。

**严重程度**：🟡 P1

---

## 六、代码架构问题

### 6.1 分层架构不严格

**问题描述**：
- Controller 层存在业务逻辑（应该只做参数校验和调用 Service）
- Service 层直接操作 HttpServletRequest/Response 等Web对象（应该与Web层解耦）
- DTO/Entity/VO 没有严格区分，Entity 直接返回给前端（或DTO和Entity字段重复）
- 没有使用 MapStruct 做对象转换，手动 getter/setter 拷贝

**严重程度**：🟡 P1

---

### 6.2 没有统一的业务异常体系

**问题描述**：虽然有 BusinessException 和 GlobalExceptionHandler，但错误码定义不系统，异常使用不规范。有些地方直接抛 RuntimeException，错误码没有按模块分段管理。

**涉及文件**：
- [ResultCode.java](file:///E:/workspace_work/CampusShare/backend/campushare-common/src/main/java/com/campushare/common/result/ResultCode.java)

**严重程度**：🟢 P2

---

### 6.3 Feign 客户端配置简陋

**问题描述**：
- Feign 没有配置连接超时、读取超时（可能默认超时时间过长导致线程挂住）
- 没有配置重试策略（或者重试策略不合理）
- 没有连接池配置（使用默认的 HttpURLConnection，没有用 OkHttp/Apache HttpClient 连接池）
- Feign 拦截器没有统一传递请求头（如 TraceId）
- Feign 调用的错误解码不统一

**严重程度**：🟡 P1

---

### 6.4 事务处理不规范

**问题描述**：
- [UserServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-user/src/main/java/com/campushare/user/service/impl/UserServiceImpl.java) 中 register 方法加了 @Transactional，但其中包含了 Redis 操作和邮件发送（这些不受事务控制，回滚不了）
- 没有考虑事务传播级别
- 没有分布式事务方案（跨服务数据一致性）
- 长事务风险（一个事务里操作太多，锁持有时间长）

**严重程度**：🟡 P1

---

## 七、文件存储问题

### 7.1 本地文件存储不适合生产

**问题描述**：文件上传存储在本地 `/app/uploads/` 目录，通过 volume 挂载持久化。这在单机部署时可行，但多实例部署时无法共享文件，也没有CDN加速、图片处理等能力。

**当前问题**：
- user-service 和 post-service 共享 uploads_data volume 来访问文件，是一种hack
- 多实例部署时文件无法在实例间共享
- 没有图片缩略图、水印等处理能力
- 没有文件访问权限控制（知道URL就能访问）

**严重程度**：🟡 P1

---

## 八、可观测性问题

### 8.1 缺少日志聚合

**问题描述**：Prometheus + Grafana + Tempo 已有 Metrics 和 Traces，但缺少 Logs（三支柱缺一）。没有接入 Loki 或 ELK。

**影响**：
- 无法在 Grafana 中通过 TraceId 一键跳转到相关日志
- 排查问题需要进容器看 docker logs，效率低
- 没有日志的统一检索、告警

**严重程度**：🟡 P1

---

### 8.2 告警通知渠道缺失

**问题描述**：Prometheus 配置了告警规则（rules/alerts.yml），但没有配置 Alertmanager 和通知渠道（钉钉/邮件/企业微信）。告警触发后没人知道。

**严重程度**：🟡 P1

---

### 8.3 业务 Metrics 埋点不足

**问题描述**：虽然有 JVM、HTTP 请求等自动埋点的 Metrics，但业务自定义 Metrics 很少（如注册转化率、发帖成功率、搜索点击率、文件上传大小分布等业务指标缺失）。

**涉及文件**：
- [MetricsConfig.java (post)](file:///E:/workspace_work/CampusShare/backend/campushare-post/src/main/java/com/campushare/post/config/MetricsConfig.java)
- [MetricsConfig.java (user)](file:///E:/workspace_work/CampusShare/backend/campushare-user/src/main/java/com/campushare/user/config/MetricsConfig.java)

**严重程度**：🟢 P2

---

## 九、其他问题

### 9.1 实时通信使用轮询

**问题描述**：私信消息用 5 秒轮询、未读通知用 30 秒轮询，实时性差且浪费服务器资源。应该用 WebSocket 做实时推送。

**严重程度**：🟡 P1

---

### 9.2 验证码实现有安全隐患

**问题描述**：
- [UserServiceImpl.java](file:///E:/workspace_work/CampusShare/backend/campushare-user/src/main/java/com/campushare/user/service/impl/UserServiceImpl.java#L142) 中验证码用 `Math.random()` 生成，不是安全随机
- 验证码校验后删除但没有防止暴力破解的机制（可以无限次尝试）
- 验证码只有6位数字，且有效期5分钟，暴力破解成功率不低

**严重程度**：🟡 P1

---

### 9.3 邮件服务耦合在业务代码中

**问题描述**：EmailService 直接在 UserServiceImpl 中调用，没有抽象成独立的通知服务，未来接入短信、站内信、Push推送时需要修改大量代码。

**严重程度**：🟢 P2

---

## 十、问题优先级汇总

| 类别 | P0 | P1 | P2 |
|------|----|----|----|
| 微服务基础设施 | 服务注册发现、配置中心、熔断限流 | 共享数据库物理拆分 | - |
| 消息队列 | 消息队列引入异步解耦 | 异步处理能力 | - |
| 数据层 | - | 分布式锁、Flyway、多级缓存、分布式锁、ES搜索 | 读写分离、分库分表、主键改造 |
| 安全 | JWT安全加固 | RBAC权限、接口防刷、审计日志、CORS收紧 | - |
| 工程化 | 单元/集成测试 | 参数校验、API文档、日志聚合、多环境配置 | 异常体系优化 |
| 代码架构 | - | 分层规范、Feign配置、事务处理 | - |
| 存储 | - | 对象存储迁移 | - |
| 可观测性 | - | Loki日志、告警通知 | 业务Metrics |
| 实时通信 | - | WebSocket替代轮询 | - |

---

**下一步**：阅读 [后端升级路线图](./upgrade-roadmap.md) 了解具体的升级方案和技术选型。
