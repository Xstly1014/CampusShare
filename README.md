<div align="center">

# 🎓 CampusShare

**校园资源共享平台 | 连接知识，共享未来**

[![React](https://img.shields.io/badge/React-18.3.1-61DAFB?style=for-the-badge&logo=react&logoColor=white)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.8-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-6.3-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-3.4-38B2AC?style=for-the-badge&logo=tailwind-css&logoColor=white)](https://tailwindcss.com/)

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=java&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io/)

[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen?style=for-the-badge)](CONTRIBUTING.md)

</div>

---

## 📖 项目简介

CampusShare 是一款旨在打破校际壁垒、促进优质教育资源流通的校园资源共享平台。我们连接全国高校学生，构建一个开放、互助、活跃的学习社区，让每一位学生都能高效获取所需的学习资料。

### ✨ 核心特色

- 🏫 **跨校资源共享** - 打破信息孤岛，连接全国高校
- 📚 **智能分类管理** - 多维度分类，精准搜索推荐
- 💬 **社区互动交流** - 类贴吧的讨论社区，知识碰撞
- 🔐 **安全可靠** - JWT认证，数据加密，权限管理
- 🎨 **现代UI设计** - 简约清爽，响应式布局

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
| Spring Cloud | 2023.0 | ☁️ 微服务架构，服务治理 |
| Spring Cloud Alibaba | 2022.0 | 🎯 微服务组件（Nacos、Sentinel） |
| MyBatis Plus | 3.5.6 | 📊 ORM框架，简化数据库操作 |
| MySQL | 8.0 | 🗄️ 关系数据库，数据存储 |
| Redis | 7.0 | ⚡ 缓存系统，性能优化 |
| JWT | 0.12.5 | 🔐 身份认证，无状态Token |
| Kafka | 3.6 | 📨 消息队列，异步处理 |
| MinIO | Latest | 📦 对象存储，文件管理 |
| Elasticsearch | 8.12 | 🔍 全文搜索，智能检索 |

---

## 🎯 功能特性

### ✅ 已实现功能

#### 前端功能
- 🔐 **用户认证系统**
  - 手机号/邮箱注册登录
  - JWT Token认证
  - 密码找回功能
  - 记住密码选项

- 🏫 **首页展示**
  - 学校卡片列表（横向排版，一行4个）
  - 真实校徽展示和资源统计
  - 实时搜索功能
  - 响应式网格布局

- 📱 **导航系统**
  - 底部导航栏（首页、仓库、通知、个人主页）
  - 路由守卫（未登录跳转）
  - 页面切换动画

- 🏫 **学校社区**
  - 帖子列表（资源贴/讨论贴）
  - 类型筛选 + 排序（最新/最热/活跃）
  - 发布帖子（资料贴+讨论贴）
  - 帖子编辑/删除
  - 多格式文件上传与下载（PDF/Word/Excel/PPT/ZIP/图片等）
  - 帖子详情页
  - 评论区（一级评论+楼中楼回复+点赞+删除）

- 👤 **个人中心**
  - 用户信息展示与编辑（头像/昵称/简介）
  - 认证创作者申请入口（UI）
  - 我的帖子/我的回复/浏览历史/我的收藏/我的点赞（独立列表页）
  - 数据统计（总浏览/获赞/被收藏/关注/粉丝/互关，前三个弹窗展示，后三个跳转列表）
  - 设置（账号与安全/隐私设置/通用设置/帮助与反馈）
  - 退出登录功能

- 🔔 **通知与社交**
  - 通知中心（点赞/收藏/关注/私信通知，收纳组+独立列表）
  - 关注/取消关注用户
  - 关注/粉丝/互关列表
  - 用户主页（查看他人资料与帖子，头像点击查看大图）
  - 私信功能（会话列表+聊天页+单向消息限制）
  - 通知标记已读与置顶

#### 后端功能
- 🔐 **认证服务**
  - 用户注册（手机号/邮箱）
  - 用户登录（账号密码）
  - 验证码发送（Redis存储，5分钟有效）
  - JWT Token生成和验证（双Token机制）
  - 重置密码

- 🌐 **API网关**
  - 统一入口管理
  - JWT认证过滤
  - 路由转发
  - 跨域配置

- 📝 **帖子服务**
  - 发布/编辑/删除帖子（资源贴/讨论贴）
  - 帖子详情（浏览量原子+1+浏览历史记录）
  - 学校帖子列表（分页+筛选+排序，含作者信息）
  - 收藏/取消收藏（DB持久化+Redis缓存）
  - 点赞/取消点赞（DB持久化+Redis缓存）
  - 评论系统（发表/删除/点赞/楼中楼回复）
  - 个人主页（浏览历史/收藏/点赞/我的帖子/我的回复/数据统计）
  - 关注/粉丝/互关系统
  - 私信系统（单向消息限制）
  - 通知系统（点赞/收藏/关注通知，容错设计）

- 📁 **文件上传服务**
  - 多格式文件上传（最大50MB）
  - 按日期分目录存储
  - UUID命名防重名
  - 静态资源访问与下载

- 🗄️ **数据存储**
  - MySQL数据库（13张核心表，含关注表、私信表、通知表）
  - Redis缓存（验证码/收藏状态/点赞状态）
  - 完整的表结构设计

### 🔄 待开发功能

- 🔍 **Elasticsearch 全文检索**
- ✍️ **创作者认证审核流程**
- 💰 **积分/激励体系**
- 📱 **小程序版本**

### ⚠️ 已知问题（待修复）

| 编号 | 问题 | 说明 |
|------|------|------|
| BUG-1 | 私信页面返回路径不一致 | 返回行为取决于入口路径 |
| BUG-2 | 头像上传缺少裁剪 | 未圆形裁剪，大图查看也非圆形 |
| BUG-3 | 陌生人私信释放后图标错误 | 应显示头像而非聊天气泡图标 |
| BUG-4 | 缺少私信隐藏功能 | 无法隐藏（不删除）特定会话 |
| BUG-5 | 通知页缺少底部导航栏 | 通知页底部导航栏消失 |
| BUG-6 | 通知红点未实现 | 铃铛无红点，通知计数需逐条统计 |

---

## 📁 项目结构

```
CampusShare/
├── 📂 frontend/                 # 前端项目
│   ├── 📂 src/
│   │   ├── 📂 components/      # 可复用组件
│   │   ├── 📂 pages/           # 页面组件
│   │   ├── 📂 services/        # API 服务层
│   │   ├── 📂 store/           # 状态管理 (Zustand)
│   │   ├── 📂 utils/           # 工具函数
│   │   └── 📂 types/           # TypeScript 类型定义
│   ├── package.json             # 前端依赖配置
│   ├── vite.config.ts           # Vite 配置
│   └── Dockerfile               # 前端 Docker 镜像
│
├── 📂 backend/                  # 后端项目
│   ├── 📂 campushare-common/    # 公共模块（工具/常量/异常）
│   ├── 📂 campushare-user/      # 用户服务（含帖子、文件）
│   │   ├── 📂 controller/      # 控制器层
│   │   ├── 📂 service/         # 业务逻辑层
│   │   ├── 📂 mapper/          # 数据访问层
│   │   ├── 📂 entity/          # 实体类
│   │   └── 📂 dto/             # 数据传输对象
│   ├── 📂 campushare-gateway/   # API 网关
│   ├── 📂 docker/mysql/        # 数据库初始化脚本
│   ├── pom.xml                  # Maven 父 POM
│   └── Dockerfile               # 后端 Docker 镜像
│
├── 📂 docs/                     # 📚 项目文档
│   ├── PRD.md                  # 产品需求文档
│   ├── database-design.md      # 数据库设计文档
│   ├── api-docs.md             # API 接口文档
│   ├── tech-design.md          # 技术方案设计文档
│   └── deployment.md           # 部署运维文档
│
├── docker-compose.yml           # 🐳 服务编排
├── .gitignore                   # Git 忽略配置
└── README.md                    # 📖 项目总览（本文件）
```

---

## 🏃 快速开始

### 前端启动

```bash
# 📦 安装依赖
cd frontend
npm install

# 🚀 启动开发服务器
npm run dev
```

访问地址：http://localhost:5173

### 后端启动

#### 1️⃣ 启动基础设施

```bash
cd backend/docker
docker-compose up -d
```

这将启动：
- 🗄️ MySQL (端口: 3306)
- ⚡ Redis (端口: 6379)
- ☁️ Nacos (端口: 8848)
- 📨 Kafka (端口: 9092)
- 📦 MinIO (端口: 9000)
- 🔍 Elasticsearch (端口: 9200)

#### 2️⃣ 编译项目

```bash
cd backend
mvn clean install
```

#### 3️⃣ 启动服务

```bash
# 🚀 启动用户服务
cd backend/campushare-user
mvn spring-boot:run

# 🌐 启动API网关
cd backend/campushare-gateway
mvn spring-boot:run
```

### 访问服务

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端应用 | http://localhost:5173 | React应用 |
| API网关 | http://localhost:8080 | 统一入口 |
| 用户服务 | http://localhost:8081 | 用户认证 |
| Nacos控制台 | http://localhost:8848/nacos | 配置中心 |

---

## 📚 文档导航

### 产品与设计
| 文档 | 说明 |
|------|------|
| [产品需求文档 (PRD)](./docs/PRD.md) | 📋 详细的功能需求、业务流程、迭代规划 |
| [数据库设计文档](./docs/database-design.md) | 🗄️ 表结构设计、ER图、索引设计、Redis数据设计 |
| [API 接口文档](./docs/api-docs.md) | 🔌 完整的接口清单、请求/响应示例、错误码 |

### 技术与架构
| 文档 | 说明 |
|------|------|
| [技术方案设计文档](./docs/tech-design.md) | 🏗️ 技术选型、架构设计、安全方案、性能优化 |
| [部署运维文档](./docs/deployment.md) | 🚀 Docker Compose 部署、运维命令、故障排查 |

### 开发指南
| 文档 | 说明 |
|------|------|
| 前端开发规范 | (待补充) |
| 后端开发规范 | (待补充) |
| 代码审查指南 | (待补充) |

---

## 🎨 设计理念

### UI设计风格
- 🎨 **简约现代** - 类似 Twitter/Google 的清爽风格
- 🎯 **蓝色主题** - 主色调蓝色，辅以白色和浅灰色
- 📱 **响应式布局** - 完美适配桌面和移动设备
- 🔄 **流畅动画** - 页面切换和交互动效优化

### 架构设计理念
- 🏗️ **微服务架构** - 服务解耦，独立部署
- 📊 **数据库设计** - 范式化设计，索引优化
- 🔐 **安全优先** - JWT认证，数据加密
- ⚡ **性能优化** - Redis缓存，异步处理

---

## 🤝 贡献指南

我们欢迎所有形式的贡献！

### 如何贡献

1. 🔀 **Fork 本仓库**
2. 🌿 **创建特性分支** (`git checkout -b feature/AmazingFeature`)
3. 💾 **提交更改** (`git commit -m 'Add some AmazingFeature'`)
4. 📤 **推送到分支** (`git push origin feature/AmazingFeature`)
5. 📬 **提交 Pull Request**

### 开发规范

- 📝 **代码规范** - 遵循 Google Java Style Guide 和 ESLint 规则
- 📖 **文档要求** - 所有类和方法添加必要注释
- 🧪 **测试要求** - 单元测试覆盖率不低于 80%
- 🔐 **安全检查** - 代码提交前进行安全扫描

---

## 📊 项目统计

- 📁 **前端文件**: 25+ TypeScript/TSX 文件
- ☕ **后端文件**: 30+ Java 文件
- 📚 **项目文档**: 5+ 专业文档（PRD/数据库/API/技术方案/部署）
- 🗄️ **数据库表**: 13 张核心业务表
- 🐳 **Docker 服务**: 5 个容器化服务（MySQL/Redis/用户服务/网关/前端）
- 🔌 **API 接口**: 30+ 已实现接口

---

## 📞 联系方式

- 📧 **Email**: campusshare@example.com
- 💬 **微信**: CampusShare官方账号
- 🐛 **问题反馈**: [GitHub Issues](https://github.com/Xstly1014/CampusShare/issues)

---

## 📜 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

---

<div align="center">

**⭐ 如果这个项目对您有帮助，请给一个Star支持一下！⭐**

Made with ❤️ by CampusShare Team

</div>