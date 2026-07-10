# 简历亮点提炼

> 从所有 insights 记录中提炼，遵循 STAR 映射关系。
> 每条亮点必须有至少一个对应文件可追溯。

## 简历条目公式

```
S(背景问题) → A(技术手段) → R(量化结果)
```

---

## 当前亮点列表

| # | 维度 | 简历语句 | 对应记录文件 | 面试准备完成 |
|---|------|----------|-------------|-------------|
| 1 | 架构 | 设计并实现基于 Spring Cloud Gateway + OpenFeign 的微服务架构，将 post-service 从单体拆分为独立服务，建立"表所有权"边界，禁止跨服务 JOIN，Feign 双向内部 API 通信。拆分后 post-service 可独立扩容，支撑 1151 QPS | [ADR-001 微服务拆分](architecture/2026-06-29_ADR-001_微服务拆分post-service.md) | ✅ |
| 2 | 性能 | 通过 SQL 聚合下推 + Redis 缓存 + 批量查询修复 N+1，将帖子列表接口 P95 延迟从 16.3 秒降至 239ms，单页 DB/网络请求数从 61 次降至 4 次（减少 93%+），QPS 从 4 提升至 204 req/s | [接口性能优化](performance/2026-06-29_optimization_接口性能SQL聚合缓存N+1.md) | ✅ |
| 3 | 性能 | 排查 Dockerfile ENTRYPOINT exec 形式不展开 $JAVA_OPTS 导致 JVM 参数失效问题，修复后 JVM 堆从默认极小值恢复至 512m-1024m，GC 频率回归健康，CPU 从持续 100% 降至峰值 79% | [JVM 优化](performance/2026-06-29_optimization_JVM参数与Dockerfile修复.md) | ✅ |
| 4 | 性能 | 基于 EXPLAIN 执行计划分析，遵循"等值列在前、排序列在后"原则设计复合索引，将核心查询从 type=index（filtered=6.25%）优化为 type=ref（filtered=100%）。JMeter A/B 对照实验验证：P95 从 167ms 降至 70ms，QPS 从 230 提升至 575 | [数据库索引优化](performance/2026-06-29_optimization_数据库复合索引设计.md) | ✅ |
| 5 | 工程化 | 搭建 Prometheus + Grafana + Tempo + OpenTelemetry 可观测性体系，排查并修复三连故障（Tempo 版本不兼容、Actuator 端点未暴露、PromQL 语法致冷启动崩溃），总结 Prometheus 热加载 vs 冷启动容错差异 | [监控故障复盘](reliability/2026-06-28_postmortem_监控体系搭建故障链.md) [可观测性设计](engineering/observability.md) | ✅ |
| 6 | 架构 | 设计 17 张表的核心数据模型，核心业务表用 UUID 主键、关联表用自增 INT；帖子表冗余互动计数字段避免列表查询聚合；复合索引覆盖 8 类高频查询模式，全部达到 type=ref/filtered=100% | [数据模型设计](architecture/2026-06-27_data-model_核心实体.md) | ✅ |
| 7 | 稳定性 | 排查 WebFlux agent-service RAG 知识库索引全失败（18篇文档 failed:18）：Controller 返回同步类型致 reactor 事件循环线程 .block() 抛 IllegalStateException。用 `Mono.fromCallable().subscribeOn(Schedulers.boundedElastic())` 包裹阻塞调用，修复后 reindex 成功（inserted:18, failed:0），agent 问答基于真实 RAG 知识消除幻觉 | [WebFlux 故障复盘](reliability/2026-07-02_postmortem_WebFlux阻塞调用导致RAG索引全失败.md) | ✅ |

---

## 每条亮点的面试问答准备

### 亮点 1：微服务拆分（post-service 独立）

**Q1: 为什么这样设计而不是保持单体或一步到位完整微服务化？**
A: 压测证明帖子列表是性能热点需要独立扩容，这是拆分的硬需求。但 1 人团队短期无法承担完整微服务化（MQ+分布式事务+服务发现）的复杂度，且通知量级（单校几千条）下 MQ 是 over-engineering。所以选了中间态 v1.5：拆 post-service + OpenFeign 同步调用，复杂度可控又解决独立扩容核心诉求。

**Q2: 这个方案有什么缺点或局限？**
A: Feign 同步调用耦合——post-service 宕机时 user-service 的通知发送会失败，当前仅 try-catch 降级打日志，通知可能丢失。未来需引入本地消息表+异步重试。共享 MySQL 单点未做读写分离，数据量达百万级后需拆库。

**Q3: 如果流量/数据量再扩大 10x，现在的方案还适用吗？**
A: 10x 后需演进：①引入 MQ 解耦通知（量级上去后同步 Feign 成瓶颈）；②MySQL 读写分离/分库；③引入服务发现（服务实例多后静态路由不够）；④引入熔断限流（Resilience4j）。当前架构为这些演进预留了扩展点（Gateway 可加限流、Feign 可加熔断）。

**Q4: 你是怎么发现这个问题的 / 怎么验证这个方案有效的？**
A: 压测发现帖子接口是热点且无法独立扩容。验证：JMeter A/B 压测，post-service 独立后 QPS 达 1151 req/s，Feign 调用延迟 < 2ms（Docker 直连），跨服务表访问违规数 0。

---

### 亮点 2：接口性能优化（SQL+缓存+N+1）

**Q1: 为什么 N+1 用批量查询而不是 JOIN 或数据冗余？**
A: 微服务架构禁止跨服务 JOIN（表所有权边界）。数据冗余（帖子表冗余作者名/头像）有一致性问题——用户改头像要更新所有相关帖子。批量查询+Map 组装改动最小、符合微服务规范、性能提升立竿见影，且批量 Feign 接口已存在。

**Q2: 这个方案有什么缺点或局限？**
A: 缓存一致性——Redis 用 TTL 5 分钟过期，发新帖后最多 5 分钟延迟才更新计数，未做主动失效。批量 Feign 调用若 user-service 宕机会导致列表无法显示作者信息，未来需加降级（显示"未知用户"）。

**Q3: 如果流量/数据量再扩大 10x，现在的方案还适用吗？**
A: 10x 后：①Redis 缓存需加主动失效（发帖时删缓存）；②批量 Feign 调用数可能超时，需加分页批量；③考虑 Caffeine 本地缓存 + Redis 多级缓存减少网络往返。

**Q4: 你是怎么发现这个问题的 / 怎么验证这个方案有效的？**
A: Grafana 监控暴露 P95=16.3s。读源码发现全表扫描+内存聚合+N+1（61次/页）。验证：JMeter 压测 P95 降至 239ms，请求数降至 4 次/页。

---

### 亮点 3：JVM 参数失效排查（ENTRYPOINT 修复）

**Q1: 为什么 JVM 参数没生效没第一时间发现？**
A: 两个原因：①Docker exec 形式 ENTRYPOINT 不展开 $VAR 是经典坑，不熟悉时容易忽略；②监控面板 CPU 单位 bug（0.0-1.0 未乘 100）误判 CPU 仅 1%，实际已 100% 打满，误导了排查方向。教训：优化后必须用 `ps aux`/`jcmd VM.flags` 验证配置真正生效。

**Q2: 这个方案有什么缺点或局限？**
A: shell 形式下 PID 1 是 sh 不是 java，`docker stop` 信号传递多一跳（sh 转发给 java）。功能不受影响但不够优雅。更优方案是用环境变量在 ENTRYPOINT 中直接展开（如 `ENTRYPOINT java ${JAVA_OPTS} ...`），但需 Docker 17.04+。

**Q3: 如果流量/数据量再扩大 10x，现在的方案还适用吗？**
A: 10x 后需：①JVM 堆可能需扩大到 2g-4g；②引入熔断/重试（Spring Retry + 指数退避）；③G1GC 参数精细化调优（-XX:MaxGCPauseMillis）；④考虑 ZGC（JDK 17+）应对低延迟场景。

**Q4: 你是怎么发现这个问题的 / 怎么验证这个方案有效的？**
A: 优化代码后性能无改善，`nproc` 发现仅 1 核，`ps aux` 检查 JVM 进程命令行。修复后 QPS 从 52 提升至 204，P95 从 790ms 降至 239ms，CPU 峰值从 100% 降至 79%。

---

### 亮点 4：数据库复合索引设计（EXPLAIN + A/B 压测）

**Q1: 为什么不用覆盖索引消除回表？**
A: 覆盖索引需把 SELECT 列全入索引，但 posts.content 是 TEXT 大字段，放索引会严重拖慢写入且索引体积爆炸。LIMIT 10 场景下回表 10 次成本可忽略，覆盖索引 tradeoff 不划算。

**Q2: 这个方案有什么缺点或局限？**
A: 索引写入开销——复合索引增多，INSERT/UPDATE/DELETE 需维护多个索引。当前写入频率不高可接受。最热排序（ORDER BY star_count）仍有 filesort，因 star_count 不在索引中，但 LIMIT 10 下排序行数极少可接受。

**Q3: 如果流量/数据量再扩大 10x，现在的方案还适用吗？**
A: 10x 后（10万帖）：①数据可能超出 Buffer Pool 产生磁盘 IO，索引优势会更明显（内存扫描→磁盘扫描差距几十倍）；②考虑分表（按 school_id 分区）；③最热排序考虑用 Redis ZSET 维护。

**Q4: 你是怎么发现这个问题的 / 怎么验证这个方案有效的？**
A: EXPLAIN 分析发现 type=index、filtered=6.25%、Using filesort。设计复合索引后 EXPLAIN 验证 type=ref、filtered=100%。JMeter A/B 对照实验：无索引组 P95=167ms/QPS=230，复合索引组 P95=70ms/QPS=575。

---

### 亮点 5：可观测性体系搭建（三连故障复盘）

**Q1: 为什么三个故障接连出现？**
A: 一次性部署多组件（Prometheus+Grafana+Tempo+OTel）未逐一验证。每个组件有独立的配置兼容性问题（Tempo 版本、Actuator 默认行为、PromQL 语法），集中爆发。教训：可观测性栈应逐组件部署验证。

**Q2: 这个方案有什么缺点或局限？**
A: ①Tempo healthcheck 绕过（空指针 panic 未根治）；②AlertManager 未接入（告警只显示不发通知）；③全量采样（生产需按比例）；④日志未聚合（仅 docker logs 本地查看）。

**Q3: 如果流量/数据量再扩大 10x，现在的方案还适用吗？**
A: 10x 后：①Prometheus 单机可能扛不住，需分片（按服务分 Prometheus 实例）或用 Thanos；②Tempo 需配对象存储（S3/MinIO）而非本地存储；③采样策略必须从全量改按比例；④引入 ELK/Loki 日志聚合。

**Q4: 你是怎么发现这个问题的 / 怎么验证这个方案有效的？**
A: `docker-compose ps` 发现容器 Restarting；`docker logs` 定位 YAML 解析错误/PromQL 语法错误；Prometheus Targets 页面发现 404/500。修复后 4 个 Targets 全部 UP，Prometheus 冷启动通过。

---

### 亮点 6：数据模型设计（17 表 + UUID/自增混合主键）

**Q1: 为什么核心表用 UUID 而关联表用自增 INT？**
A: 核心表（users/posts/comments）需分布式生成 ID 便于未来分库分表；关联表（likes/stars/history）高频写入，自增 INT 顺序写 B+ 树叶子页性能好。tradeoff：UUID 占用空间大（36 vs 4 字节）、无序致页分裂，但核心表写入频率不高可接受。

**Q2: 这个方案有什么缺点或局限？**
A: UUID 无序导致 InnoDB 聚簇索引写入页分裂（核心表写入频率不高可接受）。冗余计数字段（view_count 等）有并发竞争，高并发点赞可能不准，当前用 `UPDATE SET count=count+1` 原子操作保证最终一致。school_id 和 category_id 互斥靠应用层约束无 DB CHECK。

**Q3: 如果流量/数据量再扩大 10x，现在的方案还适用吗？**
A: 10x 后：①冗余计数改用 Redis INCR 异步同步；②UUID 改雪花算法（有序+短）；③按 school_id 分表；④互斥字段加 CHECK 约束。

**Q4: 你是怎么发现这个问题的 / 怎么验证这个方案有效的？**
A: 查询模式分析驱动索引设计。EXPLAIN 验证 8 类高频查询全部 type=ref/filtered=100%。A/B 压测验证索引效果。

---

### 亮点 7：WebFlux 阻塞调用故障排查（RAG 索引全失败）

**Q1: 为什么 WebFlux 的 .block() 会抛异常，而 Spring MVC 不会？**
A: Spring MVC 用 Servlet 容器线程池（一个请求一个线程），线程阻塞等待是天经地义的。WebFlux 用 reactor-http-epoll 事件循环线程（少量线程处理大量请求），线程阻塞会导致整个事件循环卡死、所有请求堆积。Reactor 检测到在事件循环线程上调用 .block() 会主动抛 IllegalStateException 防止雪崩。修复方式是用 `Mono.fromCallable().subscribeOn(Schedulers.boundedElastic())` 把阻塞调用移到专用弹性线程池。

**Q2: 这个方案有什么缺点或局限？**
A: ①boundedElastic 线程池默认上限（10×CPU核心数），高并发阻塞调用仍可能排队；②fromCallable 包裹增加了代码复杂度，每个端点都要记得加；③异常堆栈跨线程边界后可读性下降。更优方案是全链路响应式（用 R2DBC 替代 JDBC、用响应式 Feign），但改造成本高。

**Q3: 如果流量/数据量再扩大 10x，现在的方案还适用吗？**
A: 10x 后：①boundedElastic 池需调大或改用自定义调度器；②embedding API 调用改异步非阻塞（WebClient 替代 RestTemplate.block()）；③reindex 改为消息队列异步消费，避免 HTTP 请求超时；④引入 embedding 结果缓存避免重复计算。

**Q4: 你是怎么发现这个问题的 / 怎么验证这个方案有效的？**
A: 初期误判为 .env 文件 Unicode 污染（cat 输出显示反引号），xxd hex dump 证明文件干净后转向 docker logs，发现 IllegalStateException 堆栈。修复后重新触发 reindex，验证 inserted:18/failed:0/vectorCount:18，端到端问"怎么登录注册"确认 agent 回答基于 RAG 真实知识（手机号和邮箱两种登录方式）而非 LLM 幻觉。
