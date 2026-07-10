# JMeter 基线压测指南

## 测试计划

| 文件 | 说明 | 并发 | 时长 | 注意事项 |
|------|------|------|------|----------|
| `01-like-star-download-baseline.jmx` | P0批次1：点赞/收藏/下载记录 | 50线程 | 60秒 | 三个接口顺序执行，需替换postId |
| `02-comment-like.jmx` | P0批次2：评论点赞 | 50线程 | 180秒 | 需替换commentId；路径为 `/api/posts/comments/{id}/like`（不是 `/api/comments/{id}/like`） |
| `03-file-upload.jmx` | P0批次3：文件上传 | 10线程 | 60秒 | 使用100KB测试文件，文件须放在JMeter bin目录；压测后及时清理磁盘文件 |

---

## 通用测试参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `-Jserver` | 192.168.150.103 | 目标服务器 IP |
| `-Jport` | 8080 | Gateway 端口 |
| `-Jaccount` | 130******77 | 登录账号 |
| `-Jpassword` | 123456 | 登录密码 |

## 各脚本特有参数

| 脚本 | 需替换参数 | 获取方式 |
|------|-----------|----------|
| 01-like-star-download | `-JpostId` | VM执行：<br>`docker exec campushare-mysql mysql -uroot -proot123456 campushare -sN -e "SELECT id FROM posts WHERE deleted=0 AND post_type='resource' LIMIT 1"` |
| 02-comment-like | `-JcommentId` | VM执行：<br>`docker exec campushare-mysql mysql -uroot -proot123456 campushare -sN -e "SELECT id FROM comments WHERE deleted=0 ORDER BY RAND() LIMIT 1"` |
| 03-file-upload | （GUI模式直接配置） | 在JMeter bin目录创建测试文件：<br>PowerShell执行：`fsutil file createnew test_100kb.bin 102400` |

---

## 执行步骤

### 方式A：本地 Windows 运行 JMeter（推荐，不消耗 VM 资源）

#### 1. 安装 JMeter

```powershell
# 下载 JMeter 5.6.3
Invoke-WebRequest -Uri "https://dlcdn.apache.org/jmeter/binaries/apache-jmeter-5.6.3.zip" -OutFile "$env:TEMP\jmeter.zip"
# 解压到 C:\jmeter
Expand-Archive -Path "$env:TEMP\jmeter.zip" -DestinationPath "C:\jmeter"
```

> JMeter 需要 JDK 17+（你开发项目已安装，无需额外安装）

#### 2. GUI 模式运行（推荐用于压测观察）

直接双击 `jmeter.bat` 启动GUI，打开对应 `.jmx` 文件，修改需要替换的参数后点击启动按钮。

> 文件上传脚本建议用GUI模式，方便确认文件路径正确、查看"查看结果树"调试。

#### 3. 非 GUI 模式运行（命令行）

```powershell
cd C:\jmeter\apache-jmeter-5.6.3\bin

# 示例：点赞/收藏/下载压测
.\jmeter.bat -n -t e:\workspace_work\CampusShare\optimization-logs\baselines\jmeter\01-like-star-download-baseline.jmx -JpostId=<帖子ID> -l results.csv
```

#### 4. 同时打开 Grafana 监控

浏览器访问：http://192.168.150.103:3000
- 用户名：admin
- 密码：admin123
- Dashboard：CampusShare 应用监控

**关键监控面板**：
- **服务概览**：QPS、错误率、P95 延迟
- **JVM 堆内存**：各代内存使用
- **GC 暂停**：GC 次数和耗时
- **Top 10 接口 P95**：各接口延迟排名

#### 5. 收集结果

测试完成后，记录以下数据：
- JMeter Summary Report（Samples/Average/Min/Max/Std Dev/Error %/Throughput）
- Grafana 截图（测试期间的监控面板，GC、P95、QPS）
- VM 日志（如有错误）：`docker logs <service-name> --since 5m | grep -iE "error|exception"`

---

### 方式B：VM 上运行 JMeter（有网络延迟优势，但消耗 VM 资源）

```bash
# 安装 JMeter
cd /opt
wget https://dlcdn.apache.org/jmeter/binaries/apache-jmeter-5.6.3.tgz
tar xzf apache-jmeter-5.6.3.tgz

# 获取参数（以点赞为例）
POST_ID=$(docker exec campushare-mysql mysql -uroot -proot123456 campushare -sN -e "SELECT id FROM posts WHERE deleted=0 AND post_type='resource' LIMIT 1")

# 运行测试
cd /opt/apache-jmeter-5.6.3/bin
./jmeter -n -t /root/CampusShare/optimization-logs/baselines/jmeter/01-like-star-download-baseline.jmx -JpostId=$POST_ID -Jserver=localhost -l baseline-results.csv
```

---

## JMeter 脚本通用结构

```
TestPlan
├── setUp Thread Group (1线程，仅执行一次：登录获取Token)
│   ├── POST /api/auth/login
│   ├── JSON Extractor: $.data.token → authToken
│   └── JSR223: props.put("authToken", vars.get("authToken"));
└── 业务线程组 (N线程, ramp-up X秒, 持续Y秒)
    ├── Header Manager: Authorization: Bearer ${__P(authToken)}
    ├── HTTP Request（接口请求）
    └── Summary Report → 结果输出
```

---

## 注意事项

1. **预热效应**：服务刚启动时JIT未完成，初始延迟可能偏高，建议压测前先跑30秒预热，或者压测时长≥60秒关注稳定数据
2. **竞态条件（点赞/收藏类）**：toggle操作高并发下会产生竞态，错误率是基线数据的一部分（优化前应约37-38%）
3. **文件上传压测特殊注意**：
   - 每个请求都会写入磁盘，持续压测会快速消耗空间
   - 压测前确认磁盘可用≥10GB：`df -h /`
   - 压测后立即清理上传文件：
     ```bash
     rm -rf /root/CampusShare/uploads_data/2026* 2>/dev/null
     docker exec campushare-user-service sh -c "rm -rf /app/uploads/2026* /app/uploads/.tmp/* 2>/dev/null"
     ```
   - 建议用100KB小文件做性能压测，大文件只做功能验证
4. **Grafana截图**：测试过程中截图2-3次（开始/中间/结束），用于分析JVM和GC变化
5. **Prometheus抓取间隔**：10秒，测试至少60秒以获取足够数据点
6. **Content-Type注意**：multipart/form-data请求不要手动设置Content-Type头，由JMeter自动生成带boundary的正确值
