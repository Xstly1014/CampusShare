# 🚀 CampusShare Docker部署指南

本文档提供完整的Docker部署方案，可在虚拟机上一键部署整个CampusShare应用。

## 📋 部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose 部署                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────┐      ┌──────────┐      ┌──────────┐           │
│  │ Frontend │──────▶│ Gateway  │──────▶│  User    │           │
│  │  (80)    │      │  (8080)  │      │ Service  │           │
│  │  Nginx   │      │          │      │  (8081)  │           │
│  └──────────┘      └──────────┘      └──────────┘           │
│                                              │                │
│                                              │                │
│                                      ┌───────┴────────┐       │
│                                      │                 │       │
│                                 ┌────▼────┐      ┌────▼────┐ │
│                                 │  MySQL  │      │  Redis  │ │
│                                 │ (3306)  │      │ (6379)  │ │
│                                 └─────────┘      └─────────┘ │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 🛠️ 环境准备

### 1. 虚拟机要求

- **操作系统**: Ubuntu 20.04+ / CentOS 7+ / Debian 10+
- **内存**: 至少 4GB（推荐 8GB）
- **CPU**: 至少 2核
- **磁盘**: 至少 20GB可用空间

### 2. 安装Docker和Docker Compose

#### Ubuntu/Debian系统

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# 安装Docker
curl -fsSL https://get.docker.com | bash -s docker --mirror Aliyun

# 安装Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 启动Docker
sudo systemctl start docker
sudo systemctl enable docker

# 将当前用户加入docker组（可选，避免每次使用sudo）
sudo usermod -aG docker $USER
newgrp docker

# 验证安装
docker --version
docker-compose --version
```

#### CentOS系统

```bash
# 更新系统
sudo yum update -y

# 安装Docker
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io

# 安装Docker Compose
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 启动Docker
sudo systemctl start docker
sudo systemctl enable docker

# 验证安装
docker --version
docker-compose --version
```

## 📦 项目部署

### 方式1: 从GitHub克隆（推荐）

```bash
# 克隆项目
git clone git@github.com:Xstly1014/CampusShare.git
cd CampusShare

# 一键启动所有服务
docker-compose up -d
```

### 方式2: 本地文件传输

如果您已经在本地开发，可以将整个项目目录传到虚拟机：

```bash
# 在虚拟机上执行（使用scp从本地传输）
scp -r /path/to/local/CampusShare user@vm-ip:/home/user/

# 或者使用rsync（更快）
rsync -avz --progress /path/to/local/CampusShare user@vm-ip:/home/user/

# 进入项目目录
cd CampusShare

# 启动服务
docker-compose up -d
```

## 🔧 服务管理

### 1. 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看启动日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f user-service
docker-compose logs -f frontend
```

### 2. 查看服务状态

```bash
# 查看所有容器状态
docker-compose ps

# 查看容器详细信息
docker inspect campushare-user-service
```

预期输出：
```
NAME                    STATUS              PORTS
campushare-mysql        Up (healthy)        0.0.0.0:3306->3306/tcp
campushare-redis        Up (healthy)        0.0.0.0:6379->6379/tcp
campushare-user-service Up (healthy)        0.0.0.0:8081->8081/tcp
campushare-gateway      Up (healthy)        0.0.0.0:8080->8080/tcp
campushare-frontend     Up (healthy)        0.0.0.0:80->80/tcp
```

### 3. 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart user-service
```

### 4. 停止服务

```bash
# 停止所有服务
docker-compose stop

# 停止并删除容器
docker-compose down

# 停止并删除容器和数据卷（谨慎使用）
docker-compose down -v
```

### 5. 重新构建服务

```bash
# 重新构建所有服务
docker-compose build

# 重新构建特定服务
docker-compose build user-service

# 构建并重启
docker-compose up -d --build user-service
```

## 🧪 测试登录功能

### 1. 等待服务完全启动

首次启动需要下载镜像和编译代码，大约需要 **5-10分钟**。

```bash
# 检查服务健康状态
docker-compose ps

# 确认所有服务都是 "Up (healthy)"
```

### 2. 测试注册接口

#### 方式A: 使用curl（命令行）

```bash
# 测试用户注册
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456",
    "email": "test@example.com",
    "phone": "13800138000"
  }'

# 预期返回：
# {"code":200,"message":"注册成功","data":{"userId":1,"username":"testuser"}}
```

#### 方式B: 直接访问用户服务（不经过网关）

```bash
# 直接访问用户服务（端口8081）
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456",
    "email": "test@example.com",
    "phone": "13800138000"
  }'
```

### 3. 测试登录接口

```bash
# 测试用户登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456"
  }'

# 预期返回：
# {"code":200,"message":"登录成功","data":{"token":"eyJhbGciOiJIUzI1NiIs...","userId":1}}
```

### 4. 测试前端访问

```bash
# 访问前端页面（浏览器）
http://localhost

# 或者使用curl测试前端是否正常
curl http://localhost

# 预期返回：HTML页面内容
```

### 5. 测试完整流程

```bash
# 1. 注册新用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123","email":"demo@test.com"}'

# 2. 登录获取token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}' | jq -r '.data.token')

echo "获取到的Token: $TOKEN"

# 3. 使用token获取用户信息
curl -X GET http://localhost:8080/api/user/info \
  -H "Authorization: Bearer $TOKEN"
```

## 📊 监控和调试

### 1. 查看服务日志

```bash
# 查看所有服务日志
docker-compose logs

# 实时查看特定服务日志
docker-compose logs -f user-service

# 查看最近100行日志
docker-compose logs --tail=100 user-service
```

### 2. 进入容器调试

```bash
# 进入用户服务容器
docker exec -it campushare-user-service sh

# 进入MySQL容器
docker exec -it campushare-mysql mysql -uroot -proot123456

# 查看数据库表
mysql> USE campushare;
mysql> SHOW TABLES;
mysql> SELECT * FROM user;
```

### 3. 检查网络连接

```bash
# 检查容器网络
docker network inspect campushare-network

# 测试服务间连接
docker exec campushare-user-service ping mysql
docker exec campushare-user-service ping redis
```

### 4. 查看资源使用

```bash
# 查看容器资源使用情况
docker stats

# 查看特定容器资源
docker stats campushare-user-service
```

## 🔍 常见问题

### 1. 服务启动失败

**问题**: 服务状态显示 "Exited" 或 "Restarting"

**解决方案**:
```bash
# 查看错误日志
docker-compose logs user-service

# 检查依赖服务是否正常
docker-compose ps mysql redis

# 重启失败的服务
docker-compose restart user-service
```

### 2. MySQL连接失败

**问题**: 用户服务无法连接MySQL

**解决方案**:
```bash
# 检查MySQL是否健康
docker-compose ps mysql

# 查看MySQL日志
docker-compose logs mysql

# 手动测试连接
docker exec campushare-user-service ping mysql

# 如果ping不通，重启网络
docker-compose down
docker-compose up -d
```

### 3. 前端无法访问后端

**问题**: 前端页面可以打开，但API调用失败

**解决方案**:
```bash
# 检查网关服务
docker-compose logs gateway-service

# 检查nginx配置
docker exec campushare-frontend cat /etc/nginx/conf.d/default.conf

# 测试网关是否正常
curl http://localhost:8080/actuator/health

# 重启网关服务
docker-compose restart gateway-service
```

### 4. 端口冲突

**问题**: 端口被占用，服务无法启动

**解决方案**:
```bash
# 查看端口占用情况
sudo netstat -tlnp | grep :80
sudo netstat -tlnp | grep :8080

# 修改docker-compose.yml中的端口映射
# 例如将 "80:80" 改为 "8080:80"
```

### 5. 内存不足

**问题**: 服务因内存不足而重启

**解决方案**:
```bash
# 检查系统内存
free -h

# 查看容器内存使用
docker stats

# 调整Java内存参数（在docker-compose.yml中）
environment:
  - JAVA_OPTS=-Xms128m -Xmx256m
```

## 🌐 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端应用 | http://localhost | React应用主页 |
| API网关 | http://localhost:8080 | API统一入口 |
| 用户服务 | http://localhost:8081 | 用户认证服务 |
| MySQL | localhost:3306 | 数据库（用户：root，密码：root123456） |
| Redis | localhost:6379 | 缓存服务 |

## 📝 配置文件说明

### docker-compose.yml

包含所有服务的配置：
- MySQL: 数据库服务
- Redis: 缓存服务
- user-service: 用户认证服务
- gateway-service: API网关
- frontend: 前端Web应用

### backend/Dockerfile

多阶段构建：
- Stage 1: 编译Java项目
- Stage 2: 用户服务镜像
- Stage 3: 网关服务镜像

### frontend/Dockerfile

多阶段构建：
- Stage 1: 编译React项目
- Stage 2: Nginx生产镜像

### application-docker.yml

Docker环境配置：
- MySQL连接: mysql:3306
- Redis连接: redis:6379
- JWT配置
- 健康检查

## 🎯 性能优化建议

### 1. 镜像优化

```bash
# 清理无用镜像
docker image prune -a

# 使用缓存构建
docker-compose build --no-cache user-service
```

### 2. 日志管理

```bash
# 设置日志大小限制（在docker-compose.yml中添加）
services:
  user-service:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### 3. 资源限制

```yaml
# 在docker-compose.yml中添加资源限制
services:
  user-service:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 512M
        reservations:
          cpus: '0.5'
          memory: 256M
```

## 🔄 更新和维护

### 1. 代码更新

```bash
# 拉取最新代码
git pull origin master

# 重新构建并启动
docker-compose build
docker-compose up -d
```

### 2. 数据备份

```bash
# 备份MySQL数据
docker exec campushare-mysql mysqldump -uroot -proot123456 campushare > backup.sql

# 备份Redis数据
docker exec campushare-redis redis-cli SAVE
docker cp campushare-redis:/data/dump.rdb ./backup/redis-dump.rdb
```

### 3. 数据恢复

```bash
# 恢复MySQL数据
docker exec -i campushare-mysql mysql -uroot -proot123456 campushare < backup.sql

# 恢复Redis数据
docker cp ./backup/redis-dump.rdb campushare-redis:/data/dump.rdb
docker-compose restart redis
```

## 🎓 下一步

部署成功后，您可以：

1. ✅ **测试登录功能** - 使用curl或Postman测试API
2. ✅ **访问前端页面** - http://localhost
3. ✅ **查看数据库数据** - 进入MySQL容器查看数据
4. ✅ **监控服务状态** - 使用docker stats查看资源使用
5. ✅ **开发新功能** - 在本地修改代码，重新构建镜像

---

**💡 提示**: 首次部署需要下载镜像和编译代码，请耐心等待5-10分钟。如果遇到问题，请查看日志排查原因。

**📧 支持**: 如有问题请查看项目文档或提交Issue: https://github.com/Xstly1014/CampusShare/issues