# Baselines — 基线压测数据

> **本目录存放优化前的基线压测数据，作为优化效果对比的基准。**
> 每个接口优化前必须先跑基线，数据存入本目录。

---

## 一、测试环境

### 1.1 部署环境（实测确认于 2026-07-03）

| 项目 | 值 | 备注 |
|------|------|------|
| 部署机 IP | 192.168.150.103 | |
| 操作系统 | CentOS Linux 7.6.1810 (Core) | 内核 3.10.0-957.el7.x86_64 |
| CPU 核数 | 4 核 | |
| 内存 | 7.6 GB total（available 2.5G，Swap 2G） | 已用 4.1G |
| 磁盘 | 38G total，初始可用约28G（压测前需`docker system prune -f`清理） | ⚠️ 文件上传压测会产生大量磁盘文件，压测后需清理 |
| Docker 版本 | 26.1.4 | |
| docker-compose 版本 | 2.27.1 | 实际是 v2，hyphenated 命令兼容可用（非 v1） |

### 1.2 服务版本（实测确认于 2026-07-03）

| 服务 | 镜像/版本 | 容器名 | 运行状态 |
|------|-----------|--------|----------|
| gateway-service | 本地构建 | campushare-gateway-service | ✅ Up (healthy) |
| user-service | 本地构建 | campushare-user-service | ✅ Up (healthy) |
| post-service | 本地构建 | campushare-post-service | ✅ Up (healthy) |
| agent-service | 本地构建 | campushare-agent-service | ✅ Up (healthy) |
| frontend | 本地构建 | campushare-frontend | ✅ Up (healthy) |
| MySQL | 8.0.46 | campushare-mysql | ✅ Up (healthy) |
| Redis | 7.4.9 (alpine) | campushare-redis | ✅ Up (healthy) |
| PostgreSQL | 16.14 (pgvector) | campushare-agent-postgres | ✅ Up (healthy) |
| Prometheus | v2.52.0 | campushare-prometheus | ✅ Up (healthy) |
| Grafana | 10.4.0 | campushare-grafana | ✅ Up (healthy) |
| Tempo | 2.5.0 | campushare-tempo | ⚠️ **Restarting (crash-loop)** |

### 1.3 JVM 配置（实测确认于 2026-07-03）

> 全部服务已加载 OpenTelemetry Java Agent（`-javaagent:/app/opentelemetry-javaagent.jar`）

| 服务 | 实际堆配置 | GC | 与推荐值对比 |
|------|-----------|-----|-------------|
| user-service | -Xms512m -Xmx1024m | G1GC(JDK17默认) | ✅ 符合推荐 |
| post-service | -Xms512m -Xmx1024m | G1GC(JDK17默认) | ✅ 符合推荐 |
| agent-service | -Xms512m -Xmx1024m | G1GC(JDK17默认) | ✅ 符合推荐（按全服务配） |
| gateway-service | -Xms256m -Xmx512m | G1GC(JDK17默认) | ✅ 符合推荐（网关为轻量服务，堆减半合理） |

### 1.4 数据量（实测确认于 2026-07-03）

| 数据 | 数量 | 说明 |
|------|------|------|
| 用户(users) | 3,002 | 初始生成 |
| 帖子(posts) | 10,052 | 初始生成 |
| 点赞(post_likes) | 125,684 | ✅ 已补充（每帖0-30赞，资源帖偏多） |
| 收藏(post_stars) | 74,587 | ✅ 已补充（每帖0-15收藏） |
| 评论(comments) | 25,025 | ✅ 已补充（每帖0-5评论） |
| 浏览历史(view_history) | 100,271 | ✅ 已补充（每帖0-20浏览） |
| 下载记录(post_downloads) | 24,991 | ✅ 已补充（仅资源帖，每帖0-10下载） |
| 通知(notifications) | 22 | 极少，压测时按需补充 |

> 关联数据由 `generate-test-interactions.sql` 存储过程生成，遍历10,051条帖子逐条随机生成。
> Redis 计数缓存已清除（FLUSHDB），应用层将从 MySQL 重新加载计数。

### 1.5 已知问题与处理决策（2026-07-03 确认）

| 问题 | 严重程度 | 处理决策 | 状态 |
|------|----------|----------|------|
| 磁盘紧张（曾90%） | 🔴 高 | `docker system prune -a -f` 清理，现已降至26%（28G可用） | ✅ 已解决 |
| 关联数据不足(0点赞/15评论) | 🟡 中 | 用 SQL 存储过程补充 ~35万条关联数据 | ✅ 已解决 |
| Tempo crash-loop | 🟡 中 | 暂不修复，压测阶段用 Prometheus + 应用日志 + OpenTelemetry agent 替代 | ✅ 已决策 |
| docker-compose 实为 v2 | 🟢 低 | 不影响操作（v2 兼容 hyphenated 命令），已更新文档 | ✅ 已更新 |
| gateway JVM 配置 | 🟢 低 | 确认为 -Xms256m -Xmx512m（网关轻量服务，堆减半合理） | ✅ 已确认 |

---

## 二、压测工具

### 2.1 JMeter（主用）

- 配置参考：[insights/engineering/testing-strategy.md](../../insights/engineering/testing-strategy.md)
- 测试计划文件存放：本目录下 `.jmx` 文件

### 2.2 curl（单接口基准）

```bash
# 示例：测试帖子详情延迟
TOKEN=$(curl -s -X POST http://192.168.150.103/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"13068735577","password":"123456"}' | \
  grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)

# 单次延迟（重复10次取平均）
for i in {1..10}; do
  curl -s -o /dev/null -w "%{time_total}\n" \
    -H "Authorization: Bearer $TOKEN" \
    http://192.168.150.103/api/posts/school/1
done
```

### 2.3 wrk（轻量级压测，可选）

```bash
# 示例：50并发压测10秒
wrk -t4 -c50 -d10s -H "Authorization: Bearer $TOKEN" \
  http://192.168.150.103/api/posts/school/1
```

---

## 三、基线数据格式

### 3.1 文件命名

```
baselines/{编号}-{接口名}-baseline.md
```

示例：`baselines/01-post-like-baseline.md`

### 3.2 基线记录模板

```markdown
# 基线数据：{接口名}

- **测试日期：** YYYY-MM-DD
- **测试人：** Agent/User
- **环境：** {部署机IP / CPU / 内存}

## 测试配置

| 参数 | 值 |
|------|------|
| 并发数 | |
| 持续时间 | |
| 预热时间 | |
| 数据量 | |

## 测试结果

| 指标 | 数值 |
|------|------|
| P50 | |
| P95 | |
| P99 | |
| QPS | |
| 错误率 | |
| CPU 峰值 | |
| 内存峰值 | |

## 监控截图

{Grafana 截图链接或描述}

## 备注

{异常现象、注意事项}
```

---

## 四、基线数据索引

| 编号 | 接口 | JMeter 计划 | 基线文件 | 测试日期 | 核心指标 |
|------|------|-------------|----------|----------|---------|
| 01 | 帖子点赞/收藏/下载 | [jmx](jmeter/01-like-star-download-baseline.jmx) / [指南](jmeter/README.md) | [01-like-star-download-baseline.md](01-like-star-download-baseline.md) | 2026-07-03 | 点赞38%错误率→0%，QPS 245→713/s |
| 02 | 评论点赞 | [jmx](jmeter/02-comment-like.jmx) | [02-comment-like-baseline.md](02-comment-like-baseline.md) | 2026-07-03 | 同模式竞态Bug→0%错误率，QPS 685/s |
| 03 | 文件上传 | [jmx](jmeter/03-file-upload.jmx) | [03-file-upload-baseline.md](03-file-upload-baseline.md) | 2026-07-03 | Max延迟795→373ms(降53%)，新增并发+磁盘保护 |
| 04 | 发帖 | - | - | - | - |
| 05 | 帖子详情 | - | - | - | - |
| 06 | 帖子列表查询族 | - | - | - | - |
| 07 | 学校帖子计数 | - | - | - | - |
| 08 | 评论列表+创建 | - | - | - | - |
| 09 | 通知查询族 | - | - | - | - |
| 10 | 私信操作 | - | - | - | - |
| 11 | 用户主页+统计 | - | - | - | - |
| 12 | 登录 | - | - | - | - |
| 13 | AI对话 | - | - | - | - |

---

## 五、压测注意事项

1. **预热**：压测前先跑 1-2 分钟预热（JIT 编译 + 缓存填充），看稳定值而非初始值
2. **数据量**：使用测试数据（1万用户+2万帖子），不要在生产数据上压测
3. **监控同步**：压测时同步观察 Grafana（CPU/内存/GC/QPS/P95）
4. **隔离**：压测时关闭无关服务（如 Grafana/Tempo 在资源紧张时可关闭）
5. **记录环境**：每次压测记录 CPU 核数、内存、JVM 配置，确保对比基准一致
6. **参考 AGENT-WORKFLOW.md §13.5**：单核 vs 多核 JVM 配置不同，压测结果不可直接跨环境对比
7. **文件上传压测特殊注意**：
   - 文件上传是**数据累积型接口**，每个请求都会写入磁盘，持续压测会快速消耗磁盘空间
   - 10并发下100KB文件压测1分钟约写入1GB，1MB文件压测3分钟可写满38GB磁盘
   - 压测前需确认磁盘可用空间≥10GB，压测后立即执行：
     ```bash
     rm -rf /root/CampusShare/uploads_data/2026* 2>/dev/null
     docker exec campushare-user-service sh -c "rm -rf /app/uploads/2026* /app/uploads/.tmp/* 2>/dev/null"
     df -h /
     ```
   - 建议小文件（100KB）压测验证性能，大文件测试只需要验证功能正确性，不做长时间高并发压测
