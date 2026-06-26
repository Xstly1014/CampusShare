# CampusShare 后端项目

校园资源共享平台后端服务，基于 Spring Boot 微服务架构。

## 项目结构

```
campushare-backend/
├── campushare-common/          # 公共模块
│   ├── src/main/java/
│   │   └── com/campushare/common/
│   │       ├── constant/      # 常量定义
│   │       ├── exception/     # 异常处理
│   │       ├── result/        # 统一响应封装
│   │       └── utils/         # 工具类
├── campushare-user/           # 用户服务
│   ├── src/main/java/
│   │   └── com/campushare/user/
│   │       ├── controller/    # 控制器层
│   │       ├── service/       # 服务层
│   │       ├── mapper/        # 数据访问层
│   │       ├── entity/        # 实体类
│   │       └── dto/           # 数据传输对象
├── campushare-gateway/        # API网关
│   └── src/main/java/
│       └── com/campushare/gateway/
│           ├── filter/        # 网关过滤器
│           └── utils/         # 工具类
└── docker/                    # Docker配置
    ├── docker-compose.yml     # 环境配置
    └── mysql/
        └── init.sql           # 数据库初始化
```

## 技术栈

- **Java 17** - 主要开发语言
- **Spring Boot 3.2** - 应用框架
- **Spring Cloud 2023** - 微服务架构
- **Spring Cloud Alibaba** - 微服务组件
- **MyBatis Plus 3.5** - ORM框架
- **MySQL 8.0** - 关系数据库
- **Redis 7** - 缓存
- **JWT** - 身份认证
- **Nacos** - 配置中心和服务注册
- **Kafka** - 消息队列

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- Docker Desktop

### 2. 启动基础设施

```bash
cd docker
docker-compose up -d
```

这将启动：
- MySQL (端口: 3306)
- Redis (端口: 6379)
- Nacos (端口: 8848)
- Kafka (端口: 9092)
- MinIO (端口: 9000)
- Elasticsearch (端口: 9200)

### 3. 编译项目

```bash
mvn clean install
```

### 4. 启动服务

**启动用户服务：**
```bash
cd campushare-user
mvn spring-boot:run
```

**启动API网关：**
```bash
cd campushare-gateway
mvn spring-boot:run
```

### 5. 验证服务

访问 Nacos 控制台：http://localhost:8848/nacos
- 用户名: nacos
- 密码: nacos

## API 接口

### 认证接口

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/v1/auth/login` | POST | 用户登录 |
| `/api/v1/auth/register` | POST | 用户注册 |
| `/api/v1/auth/send-code` | POST | 发送验证码 |
| `/api/v1/auth/reset-password` | POST | 重置密码 |

### 用户接口（需认证）

| 接口 | 方法 | 描述 |
|------|------|------|
| `/api/v1/users/me` | GET | 获取当前用户信息 |

## 配置说明

### 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/campushare
    username: campushare
    password: campushare123
```

### Redis配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### JWT配置

JWT密钥在 `JwtConstants` 类中配置，生产环境应使用更安全的密钥并从配置中心读取。

## 开发规范

### 代码规范

- 使用 Google Java Style Guide
- 所有类和方法添加 Javadoc 注释
- 使用 Slf4j 进行日志记录
- 异常处理使用自定义 BusinessException

### Git提交规范

```
feat: 新功能
fix: Bug修复
docs: 文档更新
style: 代码格式调整
refactor: 重构
test: 测试相关
chore: 构建或辅助工具变动
```

## 文档

更多文档请查看 `docs/` 目录：

- [CampusShare后端项目规划文档](../.trae/documents/CampusShare后端项目规划文档.md)

## License

MIT License