# 后端升级路线图

> **文档版本**: v1.0
> **创建日期**: 2026-07-01
> **说明**: 本文档定义了后端各阶段的升级方案、技术选型对比、实施步骤和验证标准。Agent 实施每个升级前必须仔细阅读对应章节。

---

## Phase 1: 微服务基础设施补全（P0，2-3周）

### 1.1 引入 Nacos 作为服务注册发现与配置中心

**选型决策**：选择 **Nacos 2.x**（而非 Eureka/Consul/Apollo）

**选型对比**：
| 方案 | 服务发现 | 配置中心 | 一致性 | 生态 | 学习曲线 | 社区活跃度 |
|------|---------|---------|--------|------|---------|-----------|
| Nacos 2.x | ✅ | ✅ | AP/CP可切换 | Spring Cloud Alibaba 原生集成 | 低 | 高（阿里开源，国内活跃） |
| Eureka | ✅ | ❌ | AP | Spring Cloud 原生 | 低 | 低（已停更） |
| Consul | ✅ | ✅ | CP | 需额外集成 | 中 | 中 |
| Apollo | ❌ | ✅ | - | 需单独部署 | 中 | 中 |

**决策理由**：
- 同时支持服务发现和配置中心，减少中间件数量
- Spring Cloud Alibaba 原生集成，与 Spring Cloud 3.x 兼容好
- 支持 AP/CP 切换，适合不同场景
- 国内文档丰富，中文社区活跃
- 自带控制台，方便查看服务列表和配置管理

**实施步骤**：

1. **Docker Compose 添加 Nacos 服务**：
   - 使用 `nacos/nacos-server:v2.3.x` 镜像
   - 配置 MySQL 作为持久化存储（需要新建 nacos 数据库，执行初始化脚本）
   - 端口：8848（HTTP）、9848（gRPC）
   - 配置健康检查、数据卷
   - JVM 参数调优（参考现有服务）

2. **后端各服务添加 Nacos 依赖**：
   ```xml
   <!-- 在父pom的dependencyManagement中添加 -->
   <dependency>
       <groupId>com.alibaba.cloud</groupId>
       <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
   </dependency>
   <dependency>
       <groupId>com.alibaba.cloud</groupId>
       <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
   </dependency>
   ```

3. **修改各服务配置**：
   - 移除硬编码的 Feign URL 配置
   - 配置 Nacos 注册中心地址
   - 将各服务的 application-docker.yml 中需要动态调整的配置迁移到 Nacos Config
   - 配置共享配置（如 Redis 地址、公共配置）
   - 保留本地 application.yml 作为开发环境默认值（本地开发可以直连localhost，不依赖Nacos）

4. **Feign 改为服务名调用**：
   ```java
   // 修改前（硬编码URL）
   @FeignClient(name = "campushare-user", url = "${feign.user.url:http://localhost:8081}")
   // 修改后（服务发现）
   @FeignClient(name = "campushare-user", path = "/")
   ```

5. **添加 Feign 负载均衡**：
   - 引入 `spring-cloud-starter-loadbalancer`
   - 配置负载均衡策略（默认轮询即可）

6. **监控配置**：
   - 在 prometheus.yml 中添加 Nacos 监控目标
   - 导入 Nacos Grafana 仪表盘
   - 在 Tempo 中配置 Nacos 链路追踪

7. **验证标准**：
   - [ ] Nacos 控制台能看到所有注册的服务
   - [ ] 停掉一个服务实例，Nacos 在 30 秒内剔除
   - [ ] 启动两个 user-service 实例，Feign 调用能负载均衡到两个实例
   - [ ] 修改 Nacos 中的配置（如日志级别），服务动态刷新无需重启
   - [ ] 所有现有功能正常运行

8. **必须同步更新**：
   - [ ] docker-compose.yml 添加 Nacos 服务
   - [ ] AGENT-WORKFLOW.md 添加 Nacos 端口、调试命令、控制台地址
   - [ ] 所有服务的 pom.xml 添加依赖
   - [ ] 所有服务的 application.yml / application-docker.yml 配置修改
   - [ ] backend/docker/prometheus/prometheus.yml 添加监控
   - [ ] optimization-logs/ 记录升级过程

---

### 1.2 引入 RocketMQ 作为消息队列（异步解耦）

**选型决策**：选择 **RocketMQ 5.x**（而非 RabbitMQ/Kafka/Pulsar）

**选型对比**：
| 方案 | 延迟 | 吞吐量 | 顺序消息 | 事务消息 | 重试/死信 | 运维复杂度 | 适用场景 |
|------|------|--------|---------|---------|----------|-----------|---------|
| RocketMQ 5.x | ms级 | 高（10万+） | ✅ | ✅ | ✅ | 中 | 业务解耦、事务消息、电商/社交场景 |
| RabbitMQ | us级 | 中（万级） | ❌ | ❌ | ✅ | 低 | 简单异步、路由灵活 |
| Kafka | ms级 | 极高（百万级） | ✅（分区内） | ❌ | 需手动 | 中 | 日志、大数据流、高吞吐 |
| Pulsar | ms级 | 高 | ✅ | ✅ | ✅ | 高 | 云原生、多租户、分层存储 |

**决策理由**：
- 事务消息支持：需要保证"DB操作成功+消息必达"的一致性（如发帖成功后异步发通知）
- 重试和死信机制完善：消息消费失败可以重试，最终进死信队列人工干预
- 适合业务系统：社交场景的通知、点赞、评论、计数更新等业务异步化，RocketMQ是国内互联网公司常用方案
- 延迟消息支持：可以替代部分定时任务（如"24小时后通知"、"验证码过期"）
- Spring Cloud Alibaba 生态集成好

**需要异步化的场景清单**：
1. 通知发送（评论/点赞/关注/回复通知）→ 异步
2. 计数更新（帖子点赞数、评论数、分类帖子数）→ 异步+批量聚合
3. 邮件/验证码发送 → 异步
4. 搜索索引更新（ES）→ 异步
5. 数据统计（用户获赞数、帖子浏览量聚合）→ 异步
6. 缓存预热/更新 → 异步
7. 审计日志 → 异步

**实施步骤**：

1. **Docker Compose 添加 RocketMQ 服务**：
   - 使用 `apache/rocketmq:5.1.x` 镜像（NameServer + Broker + Proxy）
   - 或使用 `foxiswho/rocketmq` 系列镜像方便部署
   - 端口：9876（NameServer）、10911（Broker）、8081（Dashboard）
   - 配置数据卷持久化
   - JVM 参数调优（根据服务器内存）

2. **后端添加 RocketMQ 依赖**：
   ```xml
   <dependency>
       <groupId>org.apache.rocketmq</groupId>
       <artifactId>rocketmq-spring-boot-starter</artifactId>
       <version>2.3.0</version>
   </dependency>
   ```

3. **定义 Topic 和 Tag 规范**：
   ```
   Topic: campushare-notification
   Tags: COMMENT_NOTIFICATION, LIKE_NOTIFICATION, FOLLOW_NOTIFICATION, SYSTEM_NOTIFICATION
   
   Topic: campushare-counter
   Tags: POST_LIKE, POST_STAR, POST_COMMENT, POST_VIEW, CATEGORY_COUNT
   
   Topic: campushare-email
   Tags: VERIFY_CODE, WELCOME_EMAIL, RESET_PASSWORD
   
   Topic: campushare-search
   Tags: POST_CREATE, POST_UPDATE, POST_DELETE, COMMENT_CREATE
   ```

4. **改造现有同步调用为异步消息**：
   - **P0 优先改造**：通知发送（评论/点赞后同步调用通知的逻辑）
     - post-service 评论/点赞成功后，发送一条 RocketMQ 消息
     - user-service 作为消费者接收消息，创建通知记录
     - 消费失败重试3次，进死信队列
   - 邮件发送异步化
   - 计数更新可以考虑批量消费（攒N条或等M秒批量更新DB）

5. **消息可靠性保证**：
   - 发送方：使用事务消息或发送方确认机制，保证 DB 事务成功后消息一定发出
   - 消费方：消费幂等（消息可能重复投递，消费方要基于消息ID或业务ID做幂等）
   - 死信队列：消费失败超过次数进死信，需要有告警和人工处理机制

6. **监控配置**：
   - 接入 RocketMQ Dashboard
   - Prometheus 采集 RocketMQ 指标（生产/消费 TPS、堆积量、失败数）
   - Grafana 配置 RocketMQ 仪表盘
   - 消息堆积告警

7. **验证标准**：
   - [ ] RocketMQ Dashboard 可访问，能看到 Topic 和消费组
   - [ ] 发帖评论后，通知异步创建成功（前端能收到通知）
   - [ ] 停掉 user-service，post-service 评论成功，消息不丢失；启动 user-service 后消息被消费
   - [ ] 消费失败重试机制正常，超过次数进入死信队列
   - [ ] 发送1000条消息无丢失，无重复消费导致的业务异常（幂等生效）
   - [ ] 接口响应时间明显下降（因为去掉了同步Feign调用）
   - [ ] Prometheus/Grafana 能看到 RocketMQ 监控指标

8. **必须同步更新**：
   - [ ] docker-compose.yml 添加 RocketMQ NameServer/Broker/Dashboard
   - [ ] AGENT-WORKFLOW.md 添加 RocketMQ 端口、Dashboard地址、Topic列表、常见问题
   - [ ] 各服务 pom.xml 添加依赖
   - [ ] 新建 message 模块？不，先在各自服务内实现消息的生产/消费
   - [ ] 监控配置添加 RocketMQ

---

### 1.3 引入 Sentinel 作为熔断降级限流框架

**选型决策**：选择 **Sentinel**（而非 Resilience4j/Hystrix）

**选型对比**：
| 方案 | 熔断降级 | 限流 | 热点参数限流 | 系统自适应保护 | 控制台 | 生态 |
|------|---------|------|------------|--------------|--------|------|
| Sentinel | ✅ | ✅（多种规则） | ✅ | ✅ | ✅ 实时监控+规则配置 | Spring Cloud Alibaba 原生 |
| Resilience4j | ✅ | ✅（RateLimiter） | ❌ | ❌ | ❌（需Micrometer） | 函数式，轻量 |
| Hystrix | ✅ | ❌ | ❌ | ❌ | ✅ | 已停止维护 |

**决策理由**：
- 功能最全：熔断、降级、限流（QPS/线程数/冷启动/匀速排队）、热点参数限流、系统自适应保护（基于CPU/负载）
- 自带控制台：实时监控、动态规则配置，不需要重启服务
- Spring Cloud Alibaba 生态，与 Nacos 集成好（规则可持久化到 Nacos）
- 中文文档完善，国内使用广泛，适合业务系统

**需要做的保护**：
1. **Feign 调用熔断**：user-service 不可用时 post-service 的降级策略
2. **网关限流**：
   - 全局限流：保护整体系统
   - IP限流：防刷
   - 接口限流：验证码1次/分钟、发帖10次/小时、评论30次/小时
   - 用户维度限流（登录后）
3. **热点参数限流**：热门帖子详情接口的参数限流
4. **系统自适应保护**：CPU>80%时自动限流
5. **降级策略**：非核心接口降级（如帖子列表的"作者信息"降级为匿名用户，不影响主流程）

**实施步骤**：

1. **Docker Compose 添加 Sentinel Dashboard**：
   - 使用 `bladex/sentinel-dashboard:1.8.x` 镜像
   - 端口：8858
   - 默认账号密码：sentinel/sentinel

2. **后端添加 Sentinel 依赖**：
   ```xml
   <dependency>
       <groupId>com.alibaba.cloud</groupId>
       <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
   </dependency>
   <!-- Feign 整合 Sentinel -->
   <dependency>
       <groupId>com.alibaba.cloud</groupId>
       <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
   </dependency>
   ```

3. **配置 Feign 熔断降级**：
   - 为 UserFeignClient 和 PostFeignClient 编写 Fallback 实现
   - 示例：getUserById 失败时返回匿名用户（有默认头像、用户名"用户"），不抛异常
   - 配置熔断规则：慢调用比例（>500ms的调用占比>50%则熔断10秒）、异常比例

4. **网关层限流**：
   - gateway 集成 Sentinel
   - 配置网关限流规则（基于路由、基于IP、基于API分组）
   - 集成 Redis 做分布式限流（Sentinel 默认是单机限流，网关需要分布式限流）

5. **关键接口限流**：
   - 验证码发送：1次/分钟/账号 + IP维度限流
   - 登录接口：防止暴力破解（失败5次后需要验证码，10次后锁定账号15分钟）
   - 发帖/评论：频率限制
   - 上传接口：限流

6. **规则持久化到 Nacos**：
   - 不要把规则存在内存里（重启丢失）
   - 配置 Sentinel 规则数据源为 Nacos，规则变更动态生效

7. **监控配置**：
   - Sentinel 控制台实时监控（簇点链路、QPS、响应时间、异常数）
   - Metrics 接入 Prometheus
   - Grafana 添加 Sentinel 仪表盘
   - 限流/熔断告警

8. **验证标准**：
   - [ ] Sentinel Dashboard 可访问，能看到各服务的资源
   - [ ] Feign 调用降级生效：停掉 user-service，post-service 不报错，返回降级数据
   - [ ] 网关限流生效：1分钟内请求验证码接口超过5次返回429
   - [ ] 热点参数限流生效：高频访问同一个帖子被限流
   - [ ] 熔断恢复：下游服务恢复后，10秒后自动半开探测，恢复调用
   - [ ] Nacos 配置规则后动态生效，无需重启服务

---

### 1.4 引入 Redisson 分布式锁和分布式对象

**选型决策**：选择 **Redisson**（而非 Spring Integration Lock/手写Redis锁）

**理由**：
- 功能最完善的 Redis Java 客户端
- 提供可重入锁、公平锁、读写锁、红锁、联锁等多种锁
- 看门狗机制自动续期，避免业务执行时间长锁过期
- 提供分布式集合、分布式对象、限流器等高级功能
- 与 Spring Boot 集成简单

**应用场景**：
1. 用户注册/修改资料：防止并发注册同一账号
2. 点赞/收藏操作：虽然DB有唯一约束，但应用层加锁可以减少DB冲突异常
3. 验证码发送：原子性限流（替代当前的get+set非原子操作）
4. 创作者认证申请：防止重复提交
5. 未来的库存扣减（积分/虚拟货币）等场景

**实施步骤**：

1. **添加 Redisson 依赖**：
   ```xml
   <dependency>
       <groupId>org.redisson</groupId>
       <artifactId>redisson-spring-boot-starter</artifactId>
       <version>3.27.x</version>
   </dependency>
   ```

2. **配置 Redisson（单机模式，基于现有Redis）**：
   - 替换默认的 LettuceConnectionFactory 或共存
   - 单节点配置（当前 Redis 是单机，集群模式在P3阶段）

3. **改造并发场景**：
   - 验证码发送频率控制：用 RRateLimiter 替代当前的计数+过期
   - 注册接口加分布式锁（按账号/IP加锁）
   - 封装 DistributedLockTemplate 方便使用

4. **验证标准**：
   - [ ] 并发请求同一接口，分布式锁生效，只有一个请求进入业务逻辑
   - [ ] 看门狗续期生效：业务执行30秒锁不释放
   - [ ] 验证码限流原子性：并发发送验证码不会超发

---

### 1.5 完善日志体系（Loki + 日志规范）

**实施内容**：
1. Docker Compose 添加 Loki + Promtail（或使用 Alloy 作为采集器）
2. 配置日志格式为 JSON，包含 traceId、spanId、userId、requestId
3. 配置 MDC 过滤器，每个请求自动注入 traceId 到日志
4. 添加日志脱敏：密码、身份证号、token 等敏感信息不打日志
5. 区分日志级别：ERROR/WARN/INFO/DEBUG，生产环境默认 INFO
6. 关键操作审计日志（登录、发帖、删帖、审核等）单独打日志方便审计
7. Grafana 中配置 Loki 数据源，与 Tempo 链路追踪联动（TraceId 跳转到日志）

---

## Phase 2: 数据层升级（P1，2-3周）

### 2.1 引入 Flyway 做数据库版本化迁移

**选型决策**：Flyway（而非 Liquibase）

**理由**：
- 简单直观，SQL 脚本即迁移，学习成本低
- 与 Spring Boot 集成好
- 支持回滚（需要企业版或手动写 undo 脚本，但我们用正向迁移+备份即可）
- 性能好

**实施步骤**：
1. 各服务添加 flyway-core 依赖
2. 在各服务的 resources/db/migration 目录下创建迁移脚本（按版本命名 V1__xxx.sql、V2__xxx.sql）
3. 将现有的 V*.sql 迁移脚本整理为 Flyway 格式
4. 配置 Flyway：baseline-on-migrate=true（已有数据库从当前版本开始基线）
5. 未来新增表/字段必须通过 Flyway 脚本执行，禁止手动改DB
6. Docker Compose 启动时各服务自动执行 Flyway 迁移
7. 验证：新环境启动自动建表，老环境自动执行增量脚本

---

### 2.2 多级缓存体系建设（Caffeine + Redis）

**方案**：
- **L1 本地缓存**：Caffeine（替代 Guava Cache，性能更好），缓存不经常变的数据（分类列表、学校列表、用户基本信息）
- **L2 远程缓存**：Redis，存储热点数据、会话、计数器等
- **缓存一致性**：
  - 写操作：先更新DB，再删除L2缓存，再发送MQ通知所有节点删除L1缓存
  - 缓存过期：L1 短TTL（30秒~5分钟），L2 长TTL（30分钟~24小时）
  - 缓存穿透：空值缓存 + 布隆过滤器
  - 缓存击穿：热点Key永不过期 + 互斥锁重建
  - 缓存雪崩：TTL加随机偏移

**实施步骤**：
1. 引入 Caffeine 依赖
2. 封装多级缓存组件 CacheManager
3. 分类列表、学校列表等数据先从 L1 读，L1 没有读 L2，L2 没有读 DB 并回填
4. 数据变更时通过 RocketMQ 广播消息，所有实例清除本地 L1 缓存

---

### 2.3 引入 Elasticsearch 做全文检索

**选型决策**：Elasticsearch 8.x（OpenSearch 备选）

**理由**：
- 中文分词支持好（IK分词器）
- 全文检索、相关性排序、聚合分析能力强
- 生态完善，ELK 技术栈（Loki 替代 Logstash，但 ES 还是搜索首选）

**需要索引的数据**：
1. 帖子索引：标题、内容、作者、分类、学校、标签、创建时间
2. 用户索引：昵称、简介、学校
3. 未来：私信、通知搜索

**数据同步方案**：
- 发帖/更新/删除时发送 RocketMQ 消息
- 单独的 search-service（或在post-service内）消费消息，更新ES索引
- 初始数据通过 Bulk API 全量导入

**实施步骤**：
1. Docker Compose 添加 Elasticsearch 8.x + Kibana（单节点模式用于开发，生产至少3节点）
2. 配置 IK 中文分词器
3. 创建索引 mapping，设置中文分词字段
4. 实现 MQ 消费者同步数据到 ES
5. 实现搜索 API，替换当前 MySQL FULLTEXT
6. 支持搜索建议、高亮、筛选、排序

---

### 2.4 引入 Seata 处理分布式事务（必要场景）

**选型决策**：Seata AT 模式（基于二阶段提交，无侵入）

**应用场景**：
不是所有跨服务调用都需要分布式事务，只在以下场景使用：
- 发帖成功后必须初始化计数/统计（强一致要求）
- 涉及金额/积分的场景（未来）

**大多数场景使用最终一致性**（MQ+重试+幂等），只有真正需要强一致的场景才用 Seata。

**注意**：Seata 增加复杂度，不要滥用。优先考虑最终一致性方案。

---

### 2.5 主键策略升级（Snowflake 雪花算法）

**方案**：
- 使用雪花算法（Snowflake）生成 BIGINT 主键，替代 VARCHAR(36) UUID
- 优点：有序（InnoDB 插入性能好，不会页分裂）、占空间小（8字节 vs 36字节）、趋势递增、分布式唯一
- 实现：MyBatis Plus 内置 IdType.ASSIGN_ID 就是雪花算法，配置 workerId 和 datacenterId
- 注意：前端接收 Long 类型会有精度问题（JS Number 最大 2^53），需要将 Long 转 String 返回给前端（全局配置 Jackson 序列化 Long 为 String）

**迁移方案**（影响较大，需要谨慎）：
1. 新表直接用雪花ID
2. 旧表数据迁移是大工程，可分阶段：先新数据用新ID，旧数据保留UUID，关联查询兼容两种格式；或者停机迁移（不推荐）
3. **建议**：这个升级可以推迟到做分库分表前再做，当前阶段可以先在新模块（如agent-service）使用雪花ID

---

## Phase 3: 实时通信升级（P1，1-2周）

### 3.1 WebSocket 替代轮询

**选型决策**：
- 后端：Spring WebSocket + STOMP 协议（或直接使用 Netty WebSocket）
- 网关：Spring Cloud Gateway 支持 WebSocket 路由
- 连接管理：Redis 存储用户连接状态（多实例部署时路由消息）
- 消息投递：RocketMQ 广播消息，各 WebSocket 节点只推送给自己连接的用户

**需要实时推送的场景**：
1. 私信消息（即时接收）
2. 通知（点赞/评论/关注即时提醒）
3. 在线状态
4. 打字中状态（可选）

**实施步骤**：
1. user-service 添加 WebSocket 支持
2. 前端添加 WebSocket 客户端（stompjs 或原生 WebSocket）
3. 登录后建立 WebSocket 连接，心跳保活
4. 消息发送通过 MQ 广播到各节点，节点找到本地连接推送
5. 断开重连机制
6. 未读消息在重连后同步
7. 降级：WebSocket 连不上时自动降级为轮询

---

## Phase 4: 工程化体系建设（P0/P1，2周）

### 4.1 单元测试和集成测试

**框架**：
- 单元测试：JUnit 5 + Mockito
- 集成测试：Spring Boot Test + Testcontainers（MySQL、Redis用Docker容器）
- API 测试：REST Assured
- 覆盖率：JaCoCo，核心 Service 覆盖率 > 80%

**实施要求**：
- 每次新增功能必须写单元测试
- 重构前先补充测试用例
- 核心流程（登录、发帖、评论、点赞）必须有集成测试

---

### 4.2 参数校验（JSR-380 Hibernate Validator）

**实施**：
1. 添加 spring-boot-starter-validation 依赖
2. DTO 类添加校验注解（@NotBlank、@Email、@Size、@Pattern等）
3. Controller 参数添加 @Valid 注解
4. 全局异常处理器捕获 MethodArgumentNotValidException，返回友好错误提示
5. 示例：注册接口-用户名长度3-20、邮箱格式校验、密码强度校验

---

### 4.3 SpringDoc OpenAPI 自动生成 API 文档

**实施**：
1. 添加 springdoc-openapi-starter-webmvc-ui 依赖
2. 配置 OpenAPI 基本信息（标题、版本、作者）
3. Controller 和 DTO 添加 @Operation、@Schema 等注解
4. Knife4j 增强（可选，国内UI更友好）
5. 访问地址：http://localhost:8080/swagger-ui.html（经过网关）

---

### 4.4 JWT 安全加固

**修复内容**：
1. JWT 密钥从配置中心读取，不硬编码；生产环境用足够长的随机密钥（至少256位）
2. 实现 Token 黑名单：用户登出、改密码时将旧 Token 加入 Redis 黑名单（TTL 为 Token 剩余有效期）
3. Refresh Token 轮换：每次用 Refresh Token 换新 Access Token 时，也颁发新的 Refresh Token，旧 Refresh Token 失效
4. 引入非对称加密 RS256（可选，更安全但稍复杂）：私钥签发，公钥验证，网关和服务可以分离密钥
5. 网关解析 JWT 后，将用户信息放入请求头，下游服务不需要重复解析（已经是这样做了，保持）
6. 敏感操作（修改密码、注销账号）需要重新验证密码

---

### 4.5 RBAC 权限模型完善

**实施**：
1. 数据库设计：`roles` 表、`permissions` 表、`role_permissions` 表、`user_roles` 表
2. 初始角色：普通用户(USER)、认证创作者(CREATOR)、管理员(ADMIN)、超级管理员(SUPER_ADMIN)
3. 权限粒度：按资源+动作定义（如 post:create、post:deleteAny、user:manage）
4. 实现 `@PreAuthorize` 注解进行接口权限控制
5. 网关层做粗粒度权限（白名单/登录），Service层做细粒度权限

---

## Phase 5: 性能与稳定性持续优化（持续进行）

1. **JVM 深度调优**：G1GC 参数优化、GC日志分析、内存泄漏排查
2. **数据库优化**：慢SQL持续优化、索引优化、执行计划分析、SQL审计
3. **连接池调优**：HikariCP、Redis连接池、Tomcat线程池、Feign连接池持续调优
4. **压测体系**：JMeter 压测脚本，定期压测，性能基线对比
5. **告警完善**：Alertmanager 配置钉钉/邮件告警，P0/P1告警有人值守
6. **容灾演练**：模拟服务宕机、数据库故障、Redis故障等场景验证高可用
7. **对象存储迁移**：MinIO（自建）或阿里云 OSS/腾讯云 COS，文件直传+CDN

---

## Phase 6: 未来架构演进（P3，用户量增长后）

1. **数据库垂直拆分**：每个微服务使用独立的数据库（目前是同实例不同表，后续拆到不同实例）
2. **读写分离**：MySQL 主从复制，读请求走从库
3. **Redis 集群**：Redis Cluster 模式，支持水平扩展
4. **分库分表**：ShardingSphere-JDBC，帖子表、消息表、通知表按时间或用户ID分片
5. **服务网格**：Istio（如果服务数量多，需要更强大的流量治理能力）
6. **容器编排**：从 Docker Compose 迁移到 Kubernetes
7. **可观测性进阶**：持续剖析（Pyroscope）、eBPF 网络观测、异常检测
8. **多活容灾**：异地多活部署

---

## 升级顺序建议（Agent 执行时按此顺序）

1. **第一步**：Nacos 注册中心+配置中心 → 这是所有其他 Spring Cloud Alibaba 组件的基础
2. **第二步**：单元测试框架搭建 → 后续重构有安全保障
3. **第三步**：RocketMQ 消息队列 → 解耦同步调用，为后续异步化打基础
4. **第四步**：Sentinel 熔断限流 → 系统稳定性保护
5. **第五步**：Flyway 数据库迁移 → 规范化DB变更
6. **第六步**：Redisson 分布式锁 → 解决并发问题
7. **第七步**：Loki 日志体系 → 排查问题能力提升
8. **第八步**：参数校验 + OpenAPI 文档 → 工程化基础
9. **第九步**：JWT 安全加固 + RBAC → 安全
10. **第十步**：Elasticsearch 搜索 → 功能增强
11. **第十一步**：WebSocket 实时通信 → 体验提升
12. **第十二步**：多级缓存 → 性能优化

> **注意**：每个组件引入后必须同时完成：
> 1. Docker Compose 配置
> 2. 监控（Prometheus/Grafana）配置
> 3. AGENT-WORKFLOW.md 更新
> 4. optimization-logs/ 记录升级全流程
> 5. 现有功能验证通过（无回归bug）
