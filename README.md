<div align="center">

# 🎓 CampusShare

**校园资源共享平台 | 连接知识，共享未来**

[![React](https://img.shields.io/badge/React-18.3.1-61DAFB?style=for-the-badge&logo=react&logoColor=white)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.8-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-6.3-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-3.4-38B2AC?style=for-the-badge&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)

[![Docker](https://img.shields.io/badge/Docker-24.0-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

</div>

---

## 📖 项目简介

CampusShare 是一款旨在打破校际壁垒、促进优质教育资源流通的校园资源共享平台。我们连接全国高校学生，构建一个开放、互助、活跃的学习社区，让每一位学生都能高效获取所需的学习资料。

### ✨ 核心特色

- 🏫 **跨校资源共享** - 打破信息孤岛，连接全国高校
- 📚 **智能分类管理** - 多维度分类，精准搜索推荐
- 💬 **社区互动交流** - 类贴吧的讨论社区，知识碰撞
- 👥 **社交关系网络** - 关注/粉丝/互关，私信交流
- 🔔 **实时通知中心** - 点赞/收藏/关注/私信及时提醒
- 🔐 **安全可靠** - JWT认证，BCrypt密码加密，权限管理
- 📊 **可观测性** - Prometheus + Grafana + Tempo 全链路监控
- 🎨 **现代UI设计** - 简约清爽，响应式布局，移动端友好

---

## 🚀 技术栈

### 前端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| React | 18.3.1 | 🎨 前端框架，组件化开发 |
| TypeScript | 5.8 | 🔒 类型安全，提升代码质量 |
| Vite | 6.3 | ⚡ 极速构建工具，开发体验优化 |
| Tailwind CSS | 3.4 | 🎨 现代CSS框架，快速样式开发 |
| React Router | 7.3 | 🛣️ 路由管理，页面导航 |
| Zustand | 5.0 | 📦 轻量级状态管理 |
| Lucide React | 0.511 | 🎯 简洁优雅的图标库 |

### 后端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | ☕ 主开发语言，LTS版本 |
| Spring Boot | 3.2 | 🚀 应用框架，快速开发 |
| MyBatis Plus | 3.5.6 | 📊 ORM框架，简化数据库操作 |
| MySQL | 8.0 | 🗄️ 关系数据库，数据存储 |
| Redis | 7.0 | ⚡ 缓存系统，性能优化 |
| JWT | 0.12.5 | 🔐 身份认证，无状态Token |
| Spring Boot Mail | - | 📧 邮件验证码发送 |
| Spring Retry | - | 🔄 重试机制，提高可靠性 |
| Micrometer | - | 📈 指标采集，Prometheus集成 |
| OpenTelemetry | - | 🔍 分布式链路追踪 |

### 运维与监控
| 技术 | 版本 | 说明 |
|------|------|------|
| Docker | 24.0+ | 🐳 容器化部署 |
| Docker Compose | v1 | 🎼 服务编排 |
| Prometheus | 2.52 | 📊 指标采集与存储 |
| Grafana | 10.4 | 📈 可视化仪表盘 |
| Tempo | 2.5 | 🔍 分布式链路追踪 |
| Nginx | Alpine | 🌐 前端Web服务器 + 反向代理 |

---

## 🎯 功能特性

### ✅ 已实现功能

#### 🔐 用户认证系统
- 邮箱注册/登录（真实邮箱验证码）
- JWT 双Token认证（访问Token + 刷新Token）
- BCrypt密码加密存储
- 密码找回功能
- 记住密码选项

#### 🏫 首页与学校社区
- 学校卡片列表（多所高校，真实校徽展示）
- 学校真实帖子数动态统计
- 实时搜索功能
- 帖子列表（资源贴/讨论贴分类）
- 类型筛选 + 排序（最新/最热/活跃）
- 响应式网格布局

#### 📝 帖子功能
- 发布帖子（资料贴+讨论贴）
- 帖子编辑/删除（仅作者）
- 帖子详情页
- 多格式文件上传与下载（PDF/Word/Excel/PPT/ZIP/图片等，最大100MB）
- 浏览量计数 + 浏览历史记录
- 收藏/取消收藏（持久化存储 + 缓存加速）
- 点赞/取消点赞（持久化存储 + 缓存加速）

#### 💬 评论系统
- 一级评论 + 楼中楼回复
- 评论点赞
- 评论删除（评论作者或帖子作者）
- 评论数计数
- 评论列表含用户头像/昵称

#### 👤 个人中心
- 用户信息展示与编辑（头像/昵称/简介）
- 头像上传与预览
- 我的帖子列表
- 浏览历史列表
- 我的收藏列表
- 我的点赞列表
- 我的回复列表
- 数据统计（总浏览/获赞/被收藏/关注/粉丝/互关）
- 前三项统计点击弹窗展示成就文案
- 后三项统计点击跳转对应列表
- 设置页面（账号与安全/隐私设置/通用设置/帮助与反馈）
- 修改密码
- 退出登录

#### 👥 社交与私信
- 关注/取消关注用户
- 关注列表/粉丝列表/互关列表
- 他人主页（查看资料、帖子、私信入口）
- 头像点击查看大图
- 私信功能（会话列表 + 聊天页面）
- **单向消息限制**：对方未关注你且未回复你时，只能发送一条消息；互相回复后可自由聊天

#### 🔔 通知中心
- 点赞通知
- 收藏通知
- 关注通知
- 陌生人私信收纳
- 已对话私信直接列表展示
- 通知标记已读
- 未读计数

#### 📁 文件服务
- 多格式文件上传（最大100MB）
- 按日期分目录存储
- UUID命名防重名
- 数据卷持久化（容器重启不丢失）
- 文件下载（流式下载，支持大文件）

#### 📊 可观测性
- Prometheus 指标采集
- Grafana 预配置仪表盘
- Tempo 分布式链路追踪（OpenTelemetry无侵入接入）
- 业务自定义指标
- 重试机制保障可靠性

### 🔄 待开发功能

- 🔍 **Elasticsearch 全文检索**
- ✍️ **创作者认证审核流程**
- 💰 **积分/激励体系**
- 📱 **小程序版本**
- 🔔 **通知红点实时推送**
- 🖼️ **头像裁剪功能**
- 🙈 **私信隐藏功能**

---

## 📁 项目结构

```
CampusShare/
├── 📂 frontend/                 # 前端项目（React + TypeScript + Vite）
│   ├── 📂 src/
│   │   ├── 📂 components/      # 可复用组件
│   │   │   ├── 📂 auth/       # 认证相关组件
│   │   │   ├── 📂 common/     # 通用组件（NavBar、Toast）
│   │   │   └── 📂 home/       # 首页组件
│   │   ├── 📂 pages/           # 页面组件
│   │   ├── 📂 services/        # API 服务层
│   │   ├── 📂 stores/          # 状态管理 (Zustand)
│   │   ├── 📂 context/         # React Context
│   │   ├── 📂 hooks/           # 自定义Hooks
│   │   ├── 📂 router/          # 路由配置
│   │   └── 📂 utils/           # 工具函数
│   ├── package.json             # 前端依赖配置
│   ├── vite.config.ts           # Vite 配置
│   ├── nginx.conf               # Nginx 配置
│   └── Dockerfile               # 前端 Docker 镜像（多阶段构建）
│
├── 📂 backend/                  # 后端项目（Spring Boot 多模块）
│   ├── 📂 campushare-common/    # 公共模块（工具/常量/异常/统一响应）
│   ├── 📂 campushare-user/      # 用户服务（核心业务服务）
│   ├── 📂 campushare-gateway/   # API 网关（Spring Cloud Gateway）
│   ├── 📂 docker/               # Docker 环境配置
│   │   ├── 📂 mysql/           # 数据库初始化脚本
│   │   ├── 📂 prometheus/      # Prometheus 配置
│   │   ├── 📂 grafana/         # Grafana 配置与仪表盘
│   │   └── 📂 tempo/           # Tempo 链路追踪配置
│   ├── pom.xml                  # Maven 父 POM
│   ├── settings.xml             # Maven 镜像配置
│   └── Dockerfile               # 后端 Docker 镜像（多阶段构建）
│
├── 📂 docs/                     # 📚 项目文档
│   ├── PRD.md                  # 产品需求文档
│   ├── database-design.md      # 数据库设计文档
│   ├── api-docs.md             # API 接口文档
│   ├── tech-design.md          # 技术方案设计文档
│   └── deployment.md           # 部署运维文档
│
├── 📂 changelog/                # 📝 开发变更记录
├── docker-compose.yml           # 🐳 Docker Compose 服务编排
├── .env.example                 # 🔧 环境变量模板
├── .gitignore                   # Git 忽略配置
└── README.md                    # 📖 项目总览（本文件）
```

---

## 🏃 快速开始

### 🐳 Docker Compose 一键部署

适合快速体验和生产部署，一条命令启动所有服务。

#### 前置要求
- Docker 24.0+
- Docker Compose v1（`docker-compose` 命令）
- Docker Buildx 0.17.0+

#### 部署步骤

1. **克隆项目**
```bash
git clone https://github.com/Xstly1014/CampusShare.git
cd CampusShare
```

2. **配置环境变量（可选，邮件功能需要）**
```bash
cp .env.example .env
# 编辑 .env，填入邮箱SMTP配置（QQ邮箱需开启SMTP并获取授权码）
# MAIL_USERNAME=your-email@qq.com
# MAIL_PASSWORD=your-smtp-authorization-code
```

3. **构建并启动所有服务**
```bash
docker-compose up -d --build
```

4. **等待服务启动（约2-5分钟）**
```bash
# 查看服务状态
docker-compose ps

# 查看服务日志
docker-compose logs -f user-service
```

#### 服务访问地址

| 服务 | 地址 | 说明 | 默认账号 |
|------|------|------|----------|
| 前端应用 | http://localhost | 🌐 主入口 | - |
| API网关 | http://localhost:8080 | 🔌 API统一入口 | - |
| 用户服务 | http://localhost:8081 | ⚙️ 后端核心服务 | - |
| Grafana监控 | http://localhost:3000 | 📊 可视化仪表盘 | admin / admin123 |
| Prometheus | http://localhost:9090 | 📈 指标查询 | - |
| Tempo | http://localhost:3200 | 🔍 链路追踪 | - |
| MySQL | localhost:3306 | 🗄️ 数据库 | root / root123456 |
| Redis | localhost:6379 | ⚡ 缓存 | 无密码 |

---

### 💻 本地开发环境

适合开发调试，需要分别启动前后端。

#### 前置要求
- JDK 17
- Maven 3.8+
- Node.js 18+
- Docker Desktop（用于启动基础设施）

详细本地开发指南请参考 [docs/deployment.md](./docs/deployment.md)。

---

## 📚 文档导航

| 文档 | 说明 |
|------|------|
| [产品需求文档 (PRD)](./docs/PRD.md) | 📋 详细的功能需求、业务流程、迭代规划 |
| [数据库设计文档](./docs/database-design.md) | 🗄️ 核心表结构设计、ER图、索引设计 |
| [API 接口文档](./docs/api-docs.md) | 🔌 完整的接口清单、请求/响应示例、错误码 |
| [技术方案设计文档](./docs/tech-design.md) | 🏗️ 技术选型、架构设计、安全方案、性能优化 |
| [部署运维文档](./docs/deployment.md) | 🚀 Docker 部署、本地开发、运维命令 |

---

## 🎨 设计理念

### UI设计风格
- 🎨 **简约现代** - 类似 Twitter/Google 的清爽风格
- 🎯 **蓝色主题** - 主色调蓝色，辅以白色和浅灰色
- 📱 **移动端优先** - 完美适配移动设备，底部导航栏
- 🔄 **流畅交互** - Toast通知、弹窗动画、乐观更新

### 架构设计理念
- 🏗️ **简洁实用** - 避免过度设计，网关直连业务服务
- 📊 **数据一致** - DB为数据源，缓存加速，原子操作避免并发问题
- 🔐 **安全优先** - JWT认证、BCrypt密码加密、权限校验
- ⚡ **性能优化** - 缓存策略、乐观更新、文件分目录存储
- 🔍 **可观测** - Metrics + Tracing + Logging 三位一体
- 🐳 **容器优先** - 容器化部署，数据卷持久化，健康检查

---

## 📋 数据库设计

核心数据表：

| 表名 | 说明 |
|------|------|
| users | 用户表 |
| posts | 帖子表（资源贴/讨论贴） |
| comments | 评论表（支持楼中楼） |
| post_likes | 帖子点赞表 |
| post_stars | 帖子收藏表 |
| comment_likes | 评论点赞表 |
| view_history | 浏览历史表 |
| follows | 关注关系表 |
| messages | 私信消息表 |
| notifications | 通知表 |

详见 [database-design.md](./docs/database-design.md)

---

## 📞 联系方式

- 🐛 **问题反馈**: [GitHub Issues](https://github.com/Xstly1014/CampusShare/issues)
- 📂 **源码仓库**: [GitHub](https://github.com/Xstly1014/CampusShare)

---

## 📜 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

---

<div align="center">

**⭐ 如果这个项目对您有帮助，请给一个Star支持一下！⭐**

Made with ❤️ by CampusShare Team

</div>
