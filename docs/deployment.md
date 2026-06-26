# CampusShare 部署运维文档

> **文档版本**: v1.0
> **创建日期**: 2026-06-27
> **部署方式**: Docker Compose

---

## 一、部署架构

### 1.1 服务组成

```
                    ┌─────────────┐
                    │   客户端     │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  Nginx/网关  │
                    │  (8080)      │
                    └──┬───────┬──┘
                       │       │
            ┌──────────┘       └──────────┐
            ▼                              ▼
    ┌───────────────┐              ┌───────────────┐
    │  前端服务      │              │  API 网关     │
    │  (Nginx)      │              │  (8081)       │
    └───────────────┘              └──────┬────────┘
                                          │
                                ┌─────────▼─────────┐
                                │   用户服务         │
                                │   (8081, 内部)    │
                                └──┬────────────┬───┘
                                   │            │
                            ┌──────▼──────┐ ┌──▼─────────┐
                            │   MySQL     │ │   Redis     │
                            │   (3306)    │ │   (6379)    │
                            └─────────────┘ └─────────────┘
```

### 1.2 端口分配

| 服务 | 容器内端口 | 宿主机端口 | 说明 |
|------|-----------|-----------|------|
| campusshare-frontend | 80 | 8080 | 前端 H5 页面 |
| campusshare-gateway | 8080 | 8081 | API 网关 |
| campusshare-user | 8081 | - | 用户服务（不直接暴露） |
| MySQL | 3306 | 3306 | 数据库 |
| Redis | 6379 | 6379 | 缓存 |

---

## 二、环境要求

### 2.1 服务器要求

| 配置 | 最低要求 | 推荐配置 |
|------|---------|---------|
| CPU | 2 核 | 4 核+ |
| 内存 | 4 GB | 8 GB+ |
| 磁盘 | 20 GB SSD | 50 GB+ SSD |
| 操作系统 | CentOS 7+ / Ubuntu 20.04+ | Ubuntu 22.04 LTS |
| Docker | 20.10+ | 26.0+ |
| Docker Compose | v2+ | v2.20+ |

### 2.2 软件依赖

- Docker Engine
- Docker Compose Plugin（或 docker-compose）
- Git（用于拉取代码）

---

## 三、快速开始

### 3.1 克隆项目

```bash
git clone https://github.com/Xstly1014/CampusShare.git
cd CampusShare
```

### 3.2 配置环境变量（可选）

复制环境变量模板：

```bash
# 数据库配置（如修改需同步 docker-compose.yml）
export MYSQL_ROOT_PASSWORD=campushare123
export MYSQL_DATABASE=campushare
```

### 3.3 启动服务

```bash
# 构建并启动所有服务
docker compose up -d --build

# 或使用 docker-compose（带横杠版本）
docker-compose up -d --build
```

### 3.4 验证启动

```bash
# 查看所有服务状态
docker compose ps

# 预期输出：
# NAME                    STATUS
# campusshare-mysql       Up (healthy)
# campusshare-redis       Up
# campusshare-user        Up
# campusshare-gateway     Up
# campusshare-frontend    Up
```

### 3.5 访问应用

- **前端页面**: http://服务器IP:8080
- **API 网关**: http://服务器IP:8081
- **MySQL**: 服务器IP:3306（root / campushare123）
- **Redis**: 服务器IP:6379（无密码）

---

## 四、docker-compose.yml 详解

### 4.1 服务列表

```yaml
services:
  mysql:              # MySQL 8.0 数据库
  redis:              # Redis 7 缓存
  user-service:       # 用户服务（Spring Boot）
  gateway-service:    # API 网关（Spring Cloud Gateway）
  frontend:           # 前端（Nginx + 静态文件）
```

### 4.2 数据卷

| 卷名 | 挂载路径 | 说明 |
|------|---------|------|
| `mysql-data` | `/var/lib/mysql` | MySQL 数据持久化 |
| `redis-data` | `/data` | Redis 数据持久化 |
| `uploads` | `/app/uploads` | 用户上传文件 |

> ⚠️ **重要**：删除容器不会删除数据卷，重新启动数据会保留。如需完全重置，需手动删除数据卷。

### 4.3 网络

所有服务通过 `campushare-network` 内部网络通信，服务间可通过服务名互相访问。

---

## 五、常用运维命令

### 5.1 服务管理

```bash
# 启动所有服务
docker compose up -d

# 停止所有服务
docker compose stop

# 启动已停止的服务
docker compose start

# 重启所有服务
docker compose restart

# 停止并删除容器（数据保留）
docker compose down

# 停止并删除容器 + 数据卷（⚠️ 数据丢失）
docker compose down -v
```

### 5.2 查看状态

```bash
# 查看服务状态
docker compose ps

# 查看所有服务日志（实时）
docker compose logs -f

# 查看单个服务日志
docker compose logs -f user-service
docker compose logs -f gateway-service

# 查看最后 100 行日志
docker compose logs --tail=100 user-service
```

### 5.3 更新部署

```bash
# 1. 拉取最新代码
git pull origin master

# 2. 重新构建并启动（只变更的服务）
docker compose up -d --build

# 3. 查看启动日志
docker compose logs -f
```

### 5.4 进入容器

```bash
# 进入 MySQL
docker compose exec mysql mysql -uroot -pcampushare123

# 进入 Redis
docker compose exec redis redis-cli

# 进入用户服务容器
docker compose exec user-service sh

# 进入前端容器
docker compose exec frontend sh
```

---

## 六、数据库管理

### 6.1 数据备份

```bash
# 备份整个数据库
docker compose exec mysql mysqldump -uroot -pcampushare123 campushare > backup_$(date +%Y%m%d).sql

# 备份指定表
docker compose exec mysql mysqldump -uroot -pcampushare123 campushare users posts > backup_tables_$(date +%Y%m%d).sql
```

### 6.2 数据恢复

```bash
# 恢复数据库
docker compose exec -T mysql mysql -uroot -pcampushare123 campushare < backup_20260627.sql
```

### 6.3 重置数据库（开发环境）

```bash
# 1. 停止服务
docker compose down

# 2. 删除 MySQL 数据卷
docker volume rm campushare-mysql-data

# 3. 重新启动（会自动执行 init.sql 初始化）
docker compose up -d --build
```

---

## 七、文件存储管理

### 7.1 上传文件位置

用户上传的文件存储在 `uploads` 数据卷中，路径为：

```
/app/uploads/yyyyMMdd/uuid-文件名.ext
```

### 7.2 查看上传文件

```bash
# 查看上传目录
docker compose exec user-service ls -la /app/uploads

# 按日期查看
docker compose exec user-service ls -la /app/uploads/20260627
```

### 7.3 文件备份

```bash
# 复制上传文件到宿主机
docker compose cp user-service:/app/uploads ./uploads_backup
```

---

## 八、配置说明

### 8.1 后端配置 (application.yml)

主要配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | 8081 | 服务端口 |
| `spring.datasource.url` | jdbc:mysql://mysql:3306/campushare | 数据库连接 |
| `spring.datasource.username` | root | 数据库用户名 |
| `spring.datasource.password` | campushare123 | 数据库密码 |
| `spring.data.redis.host` | redis | Redis 地址 |
| `spring.data.redis.port` | 6379 | Redis 端口 |
| `jwt.secret` | (内置) | JWT 密钥 |
| `upload.path` | /app/uploads | 文件上传路径 |
| `upload.max-size` | 52428800 (50MB) | 单文件最大大小 |

> **生产环境**：务必修改默认密码和 JWT 密钥！

### 8.2 前端配置

前端通过环境变量配置 API 地址：

```bash
# .env.production
VITE_API_BASE_URL=http://服务器IP:8081/api
```

---

## 九、生产环境部署建议

### 9.1 安全加固

- [ ] 修改 MySQL root 密码为强密码
- [ ] 修改 Redis 密码（requirepass）
- [ ] 修改 JWT 密钥为随机字符串
- [ ] 配置 HTTPS（使用 Let's Encrypt 或购买证书）
- [ ] 限制 MySQL/Redis 端口仅内网访问
- [ ] 配置防火墙，只开放 80/443 端口

### 9.2 高可用

- [ ] MySQL 主从复制 + 读写分离
- [ ] Redis 主从 + 哨兵模式
- [ ] 应用服务多实例部署 + 负载均衡
- [ ] Nginx 反向代理 + 限流

### 9.3 监控告警

- [ ] 接入 Prometheus + Grafana 监控
- [ ] 配置告警规则（服务宕机、CPU/内存过高）
- [ ] 日志收集（ELK / Loki）
- [ ] 慢查询监控

### 9.4 备份策略

- [ ] MySQL 每日全量备份 + binlog 实时备份
- [ ] Redis RDB + AOF 持久化
- [ ] 上传文件定期备份到对象存储
- [ ] 备份文件异地存储
- [ ] 定期演练数据恢复

---

## 十、故障排查

### 10.1 服务无法启动

```bash
# 查看具体错误日志
docker compose logs user-service

# 常见问题：
# - 端口被占用：修改 docker-compose.yml 端口映射
# - 内存不足：增加服务器内存或调整 JVM 参数
# - 数据库连接失败：检查 MySQL 是否正常启动
```

### 10.2 数据库连接失败

```bash
# 检查 MySQL 状态
docker compose ps mysql
docker compose logs mysql

# 手动测试连接
docker compose exec mysql mysql -uroot -pcampushare123 -e "SELECT 1"
```

### 10.3 Redis 连接失败

```bash
# 检查 Redis 状态
docker compose ps redis
docker compose logs redis

# 手动测试连接
docker compose exec redis redis-cli ping
```

### 10.4 前端无法访问后端

```bash
# 检查网关服务
docker compose ps gateway-service
docker compose logs gateway-service

# 检查用户服务
docker compose ps user-service
docker compose logs user-service

# 测试网关连通性
curl http://localhost:8081/api/auth/send-code?account=13800138000
```

### 10.5 文件上传失败

```bash
# 检查上传目录权限
docker compose exec user-service ls -la /app/uploads
docker compose exec user-service touch /app/uploads/test.txt

# 检查磁盘空间
df -h
```

---

## 十一、性能调优

### 11.1 JVM 参数

在 `backend/campushare-user/Dockerfile` 中调整：

```dockerfile
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
```

| 参数 | 说明 | 建议值 |
|------|------|--------|
| `-Xms` | 初始堆内存 | 内存的 1/4 |
| `-Xmx` | 最大堆内存 | 内存的 1/2 |
| `-XX:+UseG1GC` | G1 垃圾回收器 | 推荐 |

### 11.2 MySQL 调优

在 `docker-compose.yml` 的 mysql 服务 command 中添加：

```yaml
command:
  - --innodb-buffer-pool-size=1G
  - --max-connections=500
```

---

## 十二、版本回滚

```bash
# 1. 查看历史版本
git log --oneline -10

# 2. 回滚到指定版本
git checkout <commit-hash>

# 3. 重新构建
docker compose up -d --build

# 4. 验证
docker compose ps
```

---

## 附录：快速部署脚本

```bash
#!/bin/bash
# 一键部署脚本

set -e

echo "===== 1. 拉取代码 ====="
git pull origin master

echo "===== 2. 停止旧服务 ====="
docker compose down

echo "===== 3. 构建并启动新服务 ====="
docker compose up -d --build

echo "===== 4. 等待服务启动 ====="
sleep 10

echo "===== 5. 检查服务状态 ====="
docker compose ps

echo "===== 部署完成 ====="
```
