# Agent 工作前置文档

> **本文档仅供AI Agent内部使用，不上传到GitHub。**

## ⚠️ 强制阅读规则（必须严格遵守）

### 一、对话开始前

每次新对话/上下文压缩后开始工作前，Agent必须：

1. **完整阅读本文档**，以快速恢复项目上下文、了解必须遵守的工作流程、开发规范。
2. 根据当前任务类型，重点阅读对应专项文档：
   - **Agent模块开发任务**（AI智能助手相关功能）：**先读 `docs/agent-assistant/architecture-overview.md`**，再深入 `campushare-agent/` 模块代码 + `docs/agent-assistant/` 目录下相关设计文档。
   - **架构重构/技术升级任务**：重点阅读 `docs/architecture-upgrade/` 目录下的所有文档，严格按照定义的阶段顺序执行。
   - **性能优化/接口延迟优化任务**：读 `optimization-logs/README.md`（总索引）+ `optimization-logs/plans/00-master-plan.md`（总计划）+ 对应接口的 `plans/0X-xxx.md`。

### 二、大型任务执行中

对于微服务拆分、架构升级、大规模重构、性能优化等重型任务，必须在执行过程中**多次重读本文档**关键章节（零章快速索引、二章工作流、六章微服务规范、七章开发规范），防止上下文压缩导致遗忘规范。

***

## 零、项目快速索引（30秒上手）

### 0.1 项目简介

CampusShare 是校园资源共享平台，前后端分离+微服务架构：

- **前端**：React 18 + TypeScript(strict) + Vite 6 + Tailwind CSS + Zustand + TanStack Query + Sentry + Vitest + ESLint/Prettier/Husky
- **后端**：Java 17 + Spring Boot 3.2 + Spring Cloud Gateway + OpenFeign + MyBatis Plus
  - `campushare-common`：公共工具/常量/异常/响应封装
  - `campushare-user`：用户服务（用户认证/个人资料/关注关系/私信/通知/文件上传/创作者认证，端口8081）
  - `campushare-post`：帖子服务（帖子/评论/分类/子分类/点赞/收藏/浏览历史/数据初始化，端口8082）
  - `campushare-agent`：AI智能助手服务（SSE流式对话/知识库检索/会话管理，WebFlux响应式，端口8083）
  - `campushare-gateway`：Spring Cloud Gateway（静态路由+JWT认证，端口8080）
- **基础设施**：MySQL 8 + PostgreSQL 16 + pgvector（AI向量数据库）+ Redis 7
- **服务间通信**：OpenFeign（user-service ↔ post-service ↔ agent-service 双向调用）
- **监控**：Prometheus(9090) + Grafana(3000, admin/admin123) + Tempo(3200)
- **部署**：Docker Compose v2.27.1（`docker-compose`带横杠）
- **虚拟机IP**：`192.168.150.103`
- **本地开发入口**：前端 <http://localhost:5173> → Vite dev server代理 → gateway(8080)
- **部署后访问入口**：前端 <http://192.168.150.103> → nginx(80) → /api → gateway(8080) → 各后端服务
- **网关路由**：agent-service匹配`/api/agent/**`（优先级最高）；post-service匹配`/api/posts/**,/api/comments/**,/api/categories/**,/api/admin/**`；其余`/api/**`兜底到user-service；StripPrefix=1去除`/api`前缀

### 0.2 关键文件路径速查

| 用途                 | 路径                                                                                                         |
| ------------------ | ---------------------------------------------------------------------------------------------------------- |
| 生产环境编排             | `docker-compose.yml`（根目录）                                                                                  |
| 数据库初始化             | `backend/docker/mysql/init.sql`                                                                              |
| 后端Docker镜像         | `backend/Dockerfile`（多阶段+OTel Agent）                                                                       |
| 前端Docker镜像         | `frontend/Dockerfile`                                                                                        |
| 前端Nginx配置          | `frontend/nginx.conf`                                                                                        |
| user-service生产配置   | `backend/campushare-user/src/main/resources/application-docker.yml`                                        |
| post-service生产配置   | `backend/campushare-post/src/main/resources/application-docker.yml`                                        |
| 网关配置               | `backend/campushare-gateway/src/main/resources/application-docker.yml`                                     |
| agent-service配置    | `backend/campushare-agent/src/main/resources/application.yml`                                              |
| JWT常量              | `backend/campushare-common/src/main/java/com/campushare/common/constant/JwtConstants.java`                 |
| 统一响应               | `backend/campushare-common/src/main/java/com/campushare/common/result/Result.java`                         |
| 业务异常               | `backend/campushare-common/src/main/java/com/campushare/common/exception/BusinessException.java`           |
| 全局异常处理             | `backend/campushare-common/src/main/java/com/campushare/common/exception/GlobalExceptionHandler.java`      |
| 前端API统一管理          | `frontend/src/services/`（模块化拆分）                                                                         |
| 前端路由               | `frontend/src/router/index.tsx`                                                                            |
| Toast通知            | `frontend/src/components/common/Toast.tsx` + `frontend/src/stores/toastStore.ts`                           |
| 时间工具               | `frontend/src/utils/time.ts`                                                                               |
| Changelog（本地，不提交）  | `changelog/YYYY-MM-DD.md`                                                                                   |

### 0.3 服务端口映射

| 服务                 | 容器端口           | 宿主机端口                                 |
| ------------------ | -------------- | ------------------------------------- |
| frontend(nginx)    | 80             | 80                                    |
| gateway-service    | 8080           | 8080                                  |
| user-service       | 8081           | 8081                                  |
| post-service       | 8082           | 8082                                  |
| agent-service      | 8083           | 8083                                  |
| mysql              | 3306           | 3306 (root/root123456)                |
| postgres(agent-pg) | 5432           | 5432 (campushare/agent_pg_password) |
| redis              | 6379           | 6379 (无密码)                            |
| prometheus         | 9090           | 9090                                  |
| grafana            | 3000           | 3000 (admin/admin123)                 |
| tempo              | 3200/4317/4318 | 3200/4317/4318                        |

### 0.4 Git仓库信息

- **远程仓库**：`https://github.com/Xstly1014/CampusShare.git`（master分支，HTTPS协议）
- **部署机路径**：`/root/CampusShare`
- **Commit语言**：必须英文
- **Changelog语言**：中文（仅本地，不推送到GitHub）

***

## 一、环境与工具

### 1.1 本地开发环境

- **本地Java 17路径**：`E:\javaJdk17`（系统默认可能是Java 8，必须显式指定）
- **本地Maven编译命令**（PowerShell5）：
  ```powershell
  $env:JAVA_HOME = "E:\javaJdk17"
  $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
  cd E:\workspace_work\CampusShare\backend
  mvn clean compile -DskipTests
  ```
- **编译判定**：输出包含 `BUILD SUCCESS` 即为通过；`BUILD FAILURE` 必须先修复。
- **本地前端**：`cd frontend && npm install && npm run dev`（访问 <http://localhost:5173）>

### 1.2 部署环境

- **虚拟机IP**：`192.168.150.103`
- **部署命令模板**（部署机上执行）：
  ```bash
  cd /root/CampusShare && git pull origin master
  docker-compose up -d --build <改动服务名>
  ```
- **服务重启对应表**：
  | 改动内容                                         | 需重启服务                                                                 | 是否需重建MySQL                     |
  | -------------------------------------------- | --------------------------------------------------------------------- | ------------------------------ |
  | 前端代码/样式/组件                                   | `frontend`                                                            | 否                              |
  | user-service代码/配置                            | `user-service` + `post-service`（如果Feign接口有变更）                      | 否                              |
  | post-service代码/配置                            | `post-service` + `user-service`（如果Feign接口有变更）                      | 否                              |
  | agent-service代码/配置                           | `agent-service` + `gateway-service`（路由变更时）                            | 否                              |
  | gateway代码/路由/白名单                             | `gateway-service`                                                     | 否                              |
  | 后端common模块                                   | `user-service` + `post-service` + `agent-service` + `gateway-service` | 否                              |
  | docker-compose.yml                           | 所有相关服务                                                                | 视情况                            |
  | init.sql（仅新增表）                               | 对应业务服务 + **手动执行建表SQL**                                                | ❌ 不需要！直接进MySQL执行CREATE TABLE即可 |
  | init.sql（修改已有表结构/新增字段/删除字段）                  | 对应业务服务 + **手动执行ALTER TABLE**                                          | ❌ 不需要！                       |
  | init.sql（需要重置所有数据重新初始化）                      | 所有服务 + `docker-compose down -v` + `up -d --build`                     | ✅ 才需要（会清空所有数据！）                |
- **⚠️ 绝对不要轻易建议** **`docker-compose down -v`**：这会删除所有数据卷（MySQL、Redis、上传文件），导致所有用户数据丢失。仅当用户明确要求重置数据时才使用。

***

## 二、代码修改工作流（每次修复/开发必须严格遵守）

```
定位问题 → 充分阅读相关代码 → 修改代码 → 本地编译验证 → git commit(英文) + push →
追加changelog + 更新docs → 返回用户重启命令
```

### 2.1 修改前

1. **先搜索后修改**：用 `Grep`/`SearchCodebase` 充分了解相关代码上下文，不要凭猜测修改。
2. **读最新内容**：对要修改的文件，如果近几条消息内没读取过，必须先 `Read` 再 `Edit`，避免上下文过期。
3. **精准修改**：优先用 `Edit` 做小范围精准替换，避免大范围重写用户文件。

### 2.2 修改后、推送前（强制验证）

1. **本地编译验证**：运行上述Java编译命令，确认 `BUILD SUCCESS`。编译不通过不得推送。
2. **前端类型检查**（如果改了前端）：在frontend目录下运行 `npx tsc --noEmit` 检查TypeScript类型错误。
3. **检查改动范围**：`git status` 确认只改了必要文件，没有误改配置或其他模块。

### 2.3 Git推送前强制检查（重要！）

在执行 `git add -A` 之前，**必须**先执行 `git status` 确认没有误添加以下不提交的文件/目录：

- ❌ `AGENT-WORKFLOW.md`（Agent工作流文档，仅本地使用）
- ❌ `changelog/`（本地开发日志，不推送到GitHub）
- ❌ `resume/`和`简历.pdf`（简历文件，仅本地使用）
- ❌ `optimization-logs/`（性能优化日志，仅本地使用）
- ❌ `.env`（含真实密钥，不提交）；✅ `.env.example`（模板文件，需提交）
- ❌ `node_modules/`、`dist/`、`target/`（构建产物/依赖）
- ❌ `.trae/`、`.vscode/`、`.idea/`（IDE配置）

如果误添加了不应该提交的文件，用 `git rm --cached <文件>` 从缓存移除。

执行 `git add -A` 后，再次执行 `git status` 确认待提交文件列表正确无误，然后再commit。

### 2.4 Git推送规范

```powershell
cd E:\workspace_work\CampusShare
git add -A
git commit -m "<English commit message>"
git push origin master
```

- **Commit message必须英文**，格式：动词+对象，如 `Fix email SMTP DNS resolution`、`Add follow list page`
- **禁止操作**：不要`push --force`、不要`hard reset`（用户明确要求回滚时除外）、不要修改git config、不要加`--no-verify`
- **不要主动commit**：除非用户明确要求，通常在完成一轮功能修复后再按流程推送。

### 2.5 Changelog更新规范

- **文件位置**：`changelog/YYYY-MM-DD.md`（文件名是周期起始日期）
- **当前周期**：`changelog/2026-07-02.md`（覆盖2026-07-02 ~ 2026-07-06）
- **新周期创建**：每5天一个周期，到时间后创建新文件
- **追加位置**：新变更插入到文件头部说明之后、第一条`---`分隔线**之前**，保持**最新变更在最前面**
- **内容格式**：
  ```markdown
  ## [YYYY-MM-DD] 变更标题（中文）

  ### 新增功能 / Bug修复 / 技术细节
  - 具体描述...

  ### 改动文件
  - `path/to/file1`
  - `path/to/file2`
  ```

### 2.6 返回给用户的内容

完成推送后，必须返回：

1. **本次修改的简要说明**（做了什么、修复了什么）
2. **启动/重启服务命令**（部署机上copy-paste即可用）：
   - 仅改前端：`cd /root/CampusShare && git pull origin master && docker-compose up -d --build frontend`
   - 仅改user-service：`cd /root/CampusShare && git pull origin master && docker-compose up -d --build user-service post-service`
   - 仅改post-service：`cd /root/CampusShare && git pull origin master && docker-compose up -d --build post-service user-service`
   - 仅改gateway-service：`cd /root/CampusShare && git pull origin master && docker-compose up -d --build gateway-service`
   - 仅改agent-service：`cd /root/CampusShare && git pull origin master && docker-compose up -d --build agent-service`（⚠️ start-period=90s）
   - 改了多个服务或docker-compose.yml：`cd /root/CampusShare && git pull origin master && docker-compose up -d --build`
   - 仅改文档：`cd /root/CampusShare && git pull origin master`（无需重启）
   - 查看服务状态：`cd /root/CampusShare && docker-compose ps`
   - 查看服务日志：`cd /root/CampusShare && docker-compose logs -f <服务名>`
3. **验证方式**：告知用户如何验证修改生效

***

## 三、架构与代码组织

### 3.1 请求链路

```
浏览器 → Nginx(frontend:80) 
  ├─ /            → 前端静态资源
  └─ /api/*       → gateway(8080)（StripPrefix=1，去掉/api前缀）
       ├─ 白名单路径/GET公开接口 → 直接转发到对应服务
       └─ 其他路径 → JWT认证过滤器 → 注入X-User-Id/X-Username → 路由到对应服务
           ├─ /posts/**, /comments/**, /categories/**, /admin/** → post-service(8082)（优先匹配）
           └─ /** （兜底，包含/auth/**,/users/**,/follows/**,/messages/**,/notifications/**,/files/**,/creator/**等）→ user-service(8081)
       
跨服务调用（OpenFeign，Docker网络直连，不经过Gateway）：
  post-service → user-service: 获取用户信息、发送通知、批量创建测试用户
  user-service → post-service: 获取用户帖子列表、帖子统计
```

### 3.2 服务职责边界

| 服务           | 端口   | 职责范围                            | 拥有的数据库表                                                                                               |
| ------------ | ---- | ------------------------------- | ----------------------------------------------------------------------------------------------------- |
| user-service | 8081 | 用户认证、个人资料、关注关系、私信、通知、文件上传、创作者认证 | users, follows, messages, notifications, creator_verifications                                       |
| post-service | 8082 | 帖子、评论、分类/子分类、点赞、收藏、浏览历史、数据初始化   | posts, comments, post_likes, post_stars, comment_likes, view_history, categories, sub_categories |
| agent-service | 8083 | AI智能助手、RAG知识库、会话管理      | agent_conversations, agent_messages, knowledge_articles（PostgreSQL向量库） |
| gateway      | 8080 | JWT认证、路由转发、限流（未来）               | 无（无状态）                                                                                                |

⚠️ **重要原则**：每个服务只能直接访问自己拥有的数据库表，禁止跨服务直接访问其他服务的表。跨服务数据获取必须通过OpenFeign调用对方的内部API。

- **主键类型**：users/posts/comments/messages等核心业务表使用UUID（VARCHAR(36)，`IdType.ASSIGN_UUID`），关联表使用自增INT
- **逻辑删除**：users/posts/comments实体类有`@TableLogic`字段，删除为`SET deleted=1`

### 3.3 user-service模块结构

```
com.campushare.user/
├── config/                # 配置类（CORS/Security/Redis/Metrics/Retry等）
├── controller/            # 外部API控制器 + InternalUserController（Feign内部接口）
├── service/               # Service接口
├── service/impl/          # Service实现类
├── mapper/                # MyBatis Plus Mapper（仅user相关表）
├── entity/                # 数据库实体
├── dto/                   # 数据传输对象
└── feign/                 # Feign客户端（调用post-service）
```

### 3.4 post-service模块结构

```
com.campushare.post/
├── config/                # 配置类
├── controller/            # 外部API控制器 + InternalPostController（Feign内部接口）
├── service/               # Service接口
├── service/impl/          # Service实现类
├── mapper/                # MyBatis Plus Mapper（仅post相关表）
├── entity/                # 数据库实体
├── dto/                   # 数据传输对象
└── feign/                 # Feign客户端（调用user-service）
```

### 3.5 前端目录结构

```
frontend/src/
├── components/            # 公共组件（auth/common/home等）
├── pages/                 # 页面组件
├── services/              # API服务层（模块化拆分：auth/file/post/user/message/notification等）
├── stores/                # Zustand状态管理（toastStore等）
├── context/               # React Context（AuthContext等）
├── router/                # 路由配置
├── hooks/                 # 自定义Hooks
├── lib/                   # 工具函数（cn等）
└── utils/                 # 时间格式化等工具
```

### 3.6 路由表（核心）

| 前端路由                                 | 页面                                         | 认证  |
| ------------------------------------ | ------------------------------------------ | --- |
| `/`                                  | 登录注册页（AuthPage）                            | 公开  |
| `/home`                              | 首页-分类广场（HomePage）                          | 需登录 |
| `/warehouse`                         | 收纳袋页（WarehousePage）                        | 需登录 |
| `/school/:schoolId`                  | 学校详情（帖子列表）                                 | 需登录 |
| `/school/:schoolId/post/:postId`     | 帖子详情（校园帖）                                  | 需登录 |
| `/category/:categoryId`              | 分类详情页                                       | 需登录 |
| `/category/:categoryId/post/:postId` | 帖子详情（分类帖）                                  | 需登录 |
| `/profile`                           | 个人中心（自己）                                   | 需登录 |
| `/profile/:type`                     | 通用列表（posts/history/starred/liked/comments） | 需登录 |
| `/user/:userId`                      | 他人主页                                       | 需登录 |
| `/messages`                          | 私信会话列表                                     | 需登录 |
| `/messages/:userId`                  | 聊天页                                        | 需登录 |
| `/notifications`                     | 通知中心                                       | 需登录 |
| `/creator-verification`              | 创作者认证申请                                    | 需登录 |

***

## 四、数据库核心设计

### 4.1 核心表与字段摘要

| 表名                     | 核心字段                                                                                                                                                                                         | 主键类型             |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| users                  | id(UUID), username, email, phone, password_hash, avatar_url, bio, school_id, status, deleted                                                                                              | VARCHAR(36) UUID |
| posts                  | id(UUID), school_id, category_id, sub_category_id, author_id, post_type(resource/discussion), title, content, file_url/name/type/size, view/like/star/comment_count, status, deleted | VARCHAR(36) UUID |
| comments               | id(UUID), post_id, user_id, parent_id(根评论为NULL), reply_to_user_id, content, like_count, deleted                                                                                       | VARCHAR(36) UUID |
| post_likes            | id(INT自增), user_id, post_id, create_time（唯一约束user_id+post_id）                                                                                                                        | INT AUTO         |
| post_stars            | 同上，收藏表                                                                                                                                                                                      | INT AUTO         |
| comment_likes         | 同上，评论点赞表                                                                                                                                                                                   | INT AUTO         |
| view_history          | id(INT自增), user_id, post_id, view_time（唯一索引user_id+post_id）                                                                                                                            | INT AUTO         |
| follows                | id(INT自增), follower_id, following_id, create_time                                                                                                                                         | INT AUTO         |
| messages               | id(UUID), sender_id, receiver_id, content, is_read, sender_hidden, receiver_hidden, create_time                                                                                        | VARCHAR(36) UUID |
| notifications          | id(INT自增), user_id, sender_id, type(LIKE/STAR/FOLLOW/COMMENT/REPLY), target_id, target_title, is_read                                                                                   | INT AUTO         |
| creator_verifications | id(INT自增), user_id, real_name, id_card, total_likes, total_posts, status(PENDING/APPROVED/REJECTED), reject_reason, review_time                                                       | INT AUTO         |
| categories             | id(UUID), name, icon, color, type(school/category), description, sort_order, post_count                                                                                                    | VARCHAR(36) UUID |
| sub_categories        | id(UUID), category_id, name, icon, sort_order, post_count                                                                                                                                 | VARCHAR(36) UUID |
| schools                | id(UUID), name, logo_url, region, description, resource_count（初始8所高校）                                                                                                                    | VARCHAR(36) UUID |

### 4.2 自动填充字段

通过 `MyMetaObjectHandler` 自动填充：`createTime`、`updateTime` → `LocalDateTime.now()`

***

## 五、认证与安全

### 5.1 JWT双Token机制

- **访问Token（accessToken）**：有效期24小时，放在请求头 `Authorization: Bearer <token>`
- **刷新Token（refreshToken）**：有效期7天
- Token中存储：userId、username
- 网关JWT认证通过后，在请求头注入 `X-User-Id` 和 `X-Username` 传递给下游服务

### 5.2 网关白名单（无需登录即可访问）

- **绝对白名单（任何方法）**：
  - `/api/auth/login`、`/api/auth/register`、`/api/auth/send-code`、`/api/auth/reset-password`、`/api/auth/refresh-token`
  - `/api/files/`（文件访问）、`/api/admin/`（调试/数据初始化接口）
- **公开GET接口（GET方法放行）**：
  - `/api/posts/`、`/api/comments/`、`/api/categories/`（浏览公开，写操作仍需认证）

### 5.3 密码加密

- 使用 `BCryptPasswordEncoder` 加密存储，永不存明文
- 测试用户默认密码：`123456` 或 `Test123456`（BCrypt哈希后存入init.sql）

### 5.4 登录方式

**仅支持手机号和邮箱两种，不支持用户名登录。**
- 登录接口字段为 `account`，值可以是手机号或邮箱
- 用户名（username）仅作展示名称，不能用于登录

***

## 六、微服务开发规范（强制）

> ⚠️ **本章是后续所有Agent开发微服务时必须遵守的核心规范。**

### 6.1 服务边界原则（铁律）

1. **数据库隔离**：每个服务只能直接访问自己拥有的表，禁止跨服务注入其他服务的Mapper。跨服务数据必须通过Feign调用获取。
2. **单一职责**：user-service负责用户/认证/关注/私信/通知/文件/创作者认证；post-service负责帖子/评论/分类/点赞/收藏/浏览历史。
3. **禁止循环依赖**：Feign调用可以双向，但不能形成更复杂的循环链（如A→B→C→A）。
4. **未来新服务**：新增业务域时创建独立Maven模块和服务，不要塞进已有服务。

### 6.2 Feign客户端开发规范

1. **Feign接口定义**：
   ```java
   @FeignClient(name = "campushare-user", url = "${feign.user.url:http://localhost:8081}")
   public interface UserFeignClient {
       @GetMapping("/api/internal/users/{userId}")
       Result<UserProfileDTO> getUserById(@PathVariable("userId") String userId);
   }
   ```
2. **内部API约定**：路径统一以 `/api/internal/服务名/` 开头，由对应服务的 `InternalXxxController` 暴露，不经过Gateway，Docker网络直连，不需要JWT认证。
3. **Feign调用容错**：对Feign调用结果必须判空和检查code字段（`result.getCode() == 2000`），失败时应有降级策略。
4. **批量接口优先**：Feign接口应提供批量获取方法，避免N+1问题。

### 6.3 跨服务数据获取禁止项

- ❌ 禁止跨服务JOIN（即使共享同一个数据库）
- ❌ 禁止跨服务写其他服务的表（只有表的拥有者才能INSERT/UPDATE/DELETE）
- ❌ 禁止跨服务直接注入其他服务的Mapper

### 6.4 本地开发微服务启动顺序

1. MySQL(3306)、Redis(6379)、PostgreSQL(5432，开发agent时)
2. user-service(8081)
3. post-service(8082)
4. agent-service(8083，开发AI时)
5. gateway-service(8080)
6. 前端(5173)

### 6.5 Docker部署注意事项

1. **depends_on健康检查**：post-service依赖user-service，必须等user-service健康后再启动。
2. **服务间访问**：Docker环境下通过服务名访问（如 `http://user-service:8081`），不使用localhost。
3. **共享uploads卷**：user-service和post-service共享 `uploads_data` volume，都挂载到 `/app/uploads`。
4. **DNS配置**：user-service在docker-compose中配置 `dns: [8.8.8.8, 114.114.114.114]`（发邮件需要解析外网SMTP域名）。
5. **agent-service注意**：双数据源（MySQL+PostgreSQL）初始化慢，start-period=90s，重启后至少等90s再操作；Controller必须返回`Mono<T>`类型，阻塞操作必须用`subscribeOn(Schedulers.boundedElastic())`包裹。

***

## 七、开发规范（强制）

### 7.1 UI交互规范

1. **禁止原生弹窗**：绝对不能用 `alert()`、`confirm()`、`prompt()`。用Toast提示和自定义Modal。
2. **Toast用法**：从 `useToastStore` 获取，自动3秒消失。
3. **Loading状态**：异步操作必须显示loading或禁用按钮，防止重复提交。

### 7.2 数据显示规范

1. **时间格式（后端）**：所有时间返回 `yyyy-MM-dd HH:mm:ss` 格式字符串（Asia/Shanghai）。
2. **时间显示（前端）**：使用 `utils/time.ts` 的 `formatTime()` 函数智能显示。
3. **头像路径**：后端返回相对路径，前端拼接 `/api` 前缀；为空时用dicebear默认头像。
4. **作者信息**：列表和详情接口必须返回 `authorName`、`authorAvatar`，禁止用userId前8位或随机头像。

### 7.3 后端开发规范

1. **统一响应格式**：所有接口返回 `Result<T>`（code/message/data/timestamp），成功code=2000。
2. **错误处理**：业务错误抛 `BusinessException(ResultCode, message)`，由 `GlobalExceptionHandler` 统一捕获。
3. **常用错误码**：2000成功、4000参数错误、4001未登录、4002账号密码错误、4003账号已存在、4030无权限、5000服务器内部错误、3001帖子不存在。
4. **计数原子操作**：使用 `setSql("field = field + 1")`，禁止读-改-写模式。
5. **权限校验**：编辑/删除前必须校验资源归属，失败抛 `BusinessException(4030, "无权操作")`。
6. **逻辑删除**：帖子/评论/用户使用 `@TableLogic`，调用 `deleteById` 自动变为 `SET deleted=1`。
7. **Redis配置**：RedisTemplate使用 `StringRedisSerializer` 序列化key和value。
8. **Redis缓存策略**：DB为数据源，Redis为缓存（TTL 30天），写操作先写DB再同步缓存。
9. **文件上传路径（Docker环境）**：必须用绝对路径 `/app/uploads/`，在 `@PostConstruct` 中自动创建目录。
10. **健康检查**：必须依赖 `spring-boot-starter-actuator`，禁用mail健康检查（`management.health.mail.enabled: false`）。
11. **YAML配置**：`spring:` 只能出现一次，避免YAML重复key错误。
12. **文件上传限制**：Nginx `client_max_body_size 100M` + Gateway Netty `max-content-length: 104857600`。

### 7.4 前端开发规范

1. **API统一管理**：所有接口调用写在 `services/` 对应模块文件中，页面不要直接fetch。
2. **乐观更新**：点赞/收藏等操作先更新本地state再调接口，失败回滚。
3. **响应解包**：axios拦截器已自动解包 `.data`，调用时直接获取data字段。
4. **路由参数**：用 `:param` 动态路由，通过 `useParams()` 获取。
5. **返回刷新**：从详情页返回列表时用 `useEffect` 依赖 `location.key` 触发数据重新拉取。
6. **下载文件**：用fetch + blob方式，便于错误处理。
7. **禁止本地node_modules复制到Docker**：`.dockerignore` 必须排除 `node_modules`、`dist`、`.git`。
8. **列表页滚动恢复**：使用 `useScrollRestoration` hook，支持无限滚动重试和back按钮清位置。

### 7.5 Docker/部署规范

1. **Docker Compose**：使用`docker-compose`带横杠命令（v2.27.1兼容）。
2. **时区**：所有Docker容器必须设置 `TZ=Asia/Shanghai`，Alpine镜像需 `apk add --no-cache tzdata`。
3. **文件持久化**：
   - MySQL数据：`campushare-mysql-data` volume
   - Redis数据：`campushare-redis-data` volume
   - 用户上传文件：`uploads_data` volume 挂载到 `/app/uploads`
4. **Nginx代理**：`location ^~ /api/` 必须用 `^~` 前缀匹配，优先级高于正则。
5. **数据卷清理**：重新初始化数据库前需要删除 `campushare-mysql-data` volume。
6. **本地构建镜像**：frontend/user-service/gateway-service是本地构建镜像，不能从Docker Hub拉取，必须用 `docker-compose up -d --build`。
7. **后端Dockerfile**：多阶段构建（maven编译 → 运行），通过OTel Java Agent实现无侵入链路追踪；ENTRYPOINT用`sh -c`包裹以展开环境变量。
8. **邮件发送**：虚拟机需要关闭防火墙（`systemctl stop firewalld`）放行587端口出站流量；QQ邮箱用授权码非登录密码。

***

## 八、常用调试命令

### 8.1 Docker常用命令（部署机）

```bash
# 查看服务状态
docker-compose ps

# 查看某服务日志
docker-compose logs -f user-service
docker-compose logs --tail=100 user-service

# 查看健康检查历史
docker inspect --format='{{json .State.Health}}' campushare-user-service | python3 -m json.tool

# 进入容器
docker exec -it campushare-user-service sh

# 容器内测试健康检查
curl -s http://localhost:8081/actuator/health

# 重启单个服务
docker-compose restart user-service

# 重新构建并启动
docker-compose up -d --build user-service

# 停止所有服务
docker-compose down
```

### 8.2 API测试（curl）

```bash
# 登录（账号支持手机号或邮箱）
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"130******77","password":"123456"}'

# 获取分类列表（公开接口）
curl "http://localhost:8080/api/categories/"

# 清空测试帖子数据
curl -X POST http://localhost:8080/api/admin/clear-posts

# 生成学校测试帖子
curl -X POST "http://localhost:8080/api/admin/init-test-data?postsPerSchool=10"
```

### 8.3 本地编译验证

```powershell
# 后端编译
$env:JAVA_HOME = "E:\javaJdk17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
cd E:\workspace_work\CampusShare\backend
mvn clean compile -DskipTests

# 前端类型检查
cd E:\workspace_work\CampusShare\frontend
npx tsc --noEmit

# 前端全量验证（提交前必跑）
cd E:\workspace_work\CampusShare\frontend
npx tsc --noEmit; npx eslint src/; npx vitest run; npm run build
```

***

## 九、快速参考卡

| 事项                  | 值/位置                                                                                                                                                                               |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Java版本              | 17（`E:\javaJdk17`）                                                                                                                                                                 |
| 后端编译                | `cd backend && mvn clean compile -DskipTests`                                                                                                                                      |
| 前端类型检查              | `cd frontend && npx tsc --noEmit`                                                                                                                                                  |
| 编译成功标志              | `BUILD SUCCESS`                                                                                                                                                                    |
| Git远程               | `https://github.com/Xstly1014/CampusShare.git` (master)                                                                                                                            |
| Commit语言            | **英文**                                                                                                                                                                             |
| Changelog语言         | **中文**（本地文件，不提交）                                                                                                                                                             |
| **Git提交前必须**        | `git status` 两次检查（add前+add后），确认无AGENT-WORKFLOW/changelog/.env/optimization-logs/resume                                                                                       |
| 部署方式                | `docker-compose`（带横杠，v2.27.1兼容）                                                                                                                                                |
| 部署机路径               | `/root/CampusShare`                                                                                                                                                                |
| 重启命令（改user-service） | `cd /root/CampusShare && git pull origin master && docker-compose up -d --build user-service post-service`                                                                         |
| 重启命令（改post-service） | `cd /root/CampusShare && git pull origin master && docker-compose up -d --build post-service user-service`                                                                         |
| 重启命令（改gateway）      | `cd /root/CampusShare && git pull origin master && docker-compose up -d --build gateway-service`                                                                                   |
| 重启命令（改agent-service） | `cd /root/CampusShare && git pull origin master && docker-compose up -d --build agent-service`（等待90s）                                                                              |
| 重启命令（改前端）           | `cd /root/CampusShare && git pull origin master && docker-compose up -d --build frontend`                                                                                          |
| 新增表/字段（不丢数据）        | 重启服务后，`docker exec -it campushare-mysql mysql -uroot -proot123456 campushare -e "CREATE TABLE IF NOT EXISTS ..."`                                                                  |
| ⚠️ 严禁轻易执行           | `docker-compose down -v`（会删除所有数据卷，清空所有用户数据）                                                                                                                                        |
| 查看服务状态              | `cd /root/CampusShare && docker-compose ps`                                                                                                                                        |
| 禁止弹窗                | 不用`alert/confirm/prompt`，用Toast+自定义Modal                                                                                                                                           |
| 文件上传路径              | `/app/uploads/`（Docker绝对路径，user和post服务共享volume）                                                                                                                                    |
| 文件大小限制              | 100MB（Nginx+Netty双重配置）                                                                                                                                                             |
| 时区                  | Asia/Shanghai（Docker+JVM双重配置）                                                                                                                                                      |
| Nginx API代理         | `location ^~ /api/`（^~前缀必加）                                                                                                                                                       |
| 密码加密                | BCryptPasswordEncoder                                                                                                                                                              |
| Redis序列化            | StringRedisSerializer                                                                                                                                                              |
| 健康检查端点              | `/actuator/health`                                                                                                                                                                 |
| Mail健康检查            | 必须禁用（`management.health.mail.enabled: false`）                                                                                                                                      |
| Docker DNS          | 8.8.8.8, 114.114.114.114                                                                                                                                                           |
| Grafana账号           | admin / admin123（端口3000）                                                                                                                                                           |
| MySQL账号             | root / root123456（端口3306）                                                                                                                                                          |
| 测试学校ID              | 1-8（校园分类type=school）                                                                                                                                                               |
| 测试用户默认密码            | `123456`                                                                                                                                                                             |
| 前端Toast             | `useToastStore()` from `stores/toastStore`                                                                                                                                         |
| 前端时间工具              | `formatTime()` from `utils/time.ts`                                                                                                                                                |
| Feign内部API路径        | `/api/internal/{服务名}/` 前缀                                                                                                                                                          |
| agent-service启动等待   | 至少90秒（双数据源初始化）                                                                                                                                                                    |

***

## 十、接口优化文档维护流程（optimization-logs目录）

### 10.1 目录结构

```
optimization-logs/
├── README.md                          # 总索引：目录结构、接口清单、工作流说明
├── baseline-summary.xlsx              # 基线/优化前后数据对比汇总表（Excel）
├── plans/                             # 优化计划
│   ├── README.md                      # 计划目录索引
│   └── 00-master-plan.md              # 总计划：优先级、排期、依赖关系
├── records/                           # 优化过程记录（STAR格式，用于面试/总结）
│   ├── README.md                      # 记录目录索引
│   ├── 01-like-star-download.md       # 批次1记录
│   ├── 02-comment-like.md             # 批次2记录
│   └── 03-file-upload.md              # 批次3记录
└── baselines/                         # 基线压测数据
    ├── README.md                      # 基线说明（环境、工具、数据格式、注意事项）
    ├── 01-like-star-download-baseline.md  # 01基线数据
    ├── 02-comment-like-baseline.md    # 02基线数据
    ├── 03-file-upload-baseline.md     # 03基线数据
    └── jmeter/
        ├── README.md                  # JMeter使用指南
        ├── 01-like-star-download-baseline.jmx
        ├── 02-comment-like.jmx
        └── 03-file-upload.jmx
```

> ⚠️ **重要**：`optimization-logs/`、`AGENT-WORKFLOW.md`、`changelog/` 都是本地文档，**严禁提交到Git**。提交前必须通过 `git status` 确认这些文件未被添加。

### 10.2 每完成一个接口优化必须更新的文档

按顺序更新，确保所有索引同步：

| 顺序 | 文件 | 更新内容 |
|------|------|----------|
| 1 | `baselines/XX-xxx-baseline.md` | **优化前**：创建基线压测文档，记录环境、并发、JMeter数据、问题诊断、优化方案 |
| 2 | `baselines/jmeter/XX-xxx.jmx` | **优化前**：创建对应JMeter压测脚本 |
| 3 | `plans/XX-xxx.md` | **优化前**：创建该接口的详细优化计划（问题分析→方案→验收标准） |
| 4 | `records/XX-xxx.md` | **优化后**：按STAR格式记录完整优化过程（S背景→T目标→A行动→R结果，含代码对比、数据对比、学到的经验） |
| 5 | `plans/00-master-plan.md` | **优化后**：将接口状态从"⬜待优化"改为"✅已完成"，更新排期树 |
| 6 | `baselines/README.md` | **优化后**：在「四、基线数据索引」表中添加该接口的基线文件链接、测试日期、核心指标 |
| 7 | `baselines/jmeter/README.md` | **优化后**：在「测试计划」表中添加jmx文件说明，添加参数获取方式，添加该接口特殊注意事项 |
| 8 | `optimization-logs/README.md` | **最终**：<br>1. 更新「二、目录结构」添加新文件<br>2. 更新「三、接口优化清单」将状态改为✅已完成，添加记录链接<br>3. 如属特殊接口（如文件上传），更新「五、工作流程说明」补充注意事项 |
| 9 | `AGENT-WORKFLOW.md` | 如有新的通用经验/坑点/最佳实践，更新本文件对应章节 |

### 10.3 基线文档（baseline）标准格式

```markdown
# XX-[接口名] 优化前基线压测

## 一、测试环境
（复用 baselines/README.md §1，如有特殊配置额外说明）

## 二、压测配置
| 配置项 | 值 |
|--------|-----|
| JMeter计划 | [jmx](jmeter/XX-xxx.jmx) |
| 并发数 | N线程 |
| 压测时长 | N秒 |
| 测试数据 | 说明测试用的ID范围/数据量 |

## 三、基线压测结果（优化前）
### 3.1 JMeter Summary Report
（粘贴完整数据表格）

### 3.2 关键指标解读
- QPS：xxx/s
- P95延迟：xxx ms
- 错误率：xxx%
- 瓶颈分析：

## 四、问题诊断
### 4.1 代码问题定位
（贴关键代码片段，说明问题）

### 4.2 根因分析
（从代码/架构层面分析根本原因）

## 五、优化方案
（分点列出优化措施，说明预期效果）
```

### 10.4 优化记录（record）STAR标准格式

```markdown
# XX-[接口名]优化记录：[一句话总结]

## S - Situation（业务背景）
（为什么优化这个接口？业务场景是什么？优先级P0/P1/P2的原因）

## T - Task（目标）
- 基线指标：错误率xx%，QPS xx/s，P95 xx ms
- 目标指标：错误率0%，QPS提升xx%，P95降低xx%
- 不影响：正确性、功能完整性、其他接口

## A - Action（行动）
### 1. [优化点1标题]
- 优化前代码：（代码片段）
- 问题：
- 优化后代码：（代码片段）
- 原理：

### 2. [优化点2标题]
...

## R - Result（结果）
### 1. 压测数据对比（同配置下）
| 指标 | 优化前 | 优化后 | 提升 |
|------|--------|--------|------|
| QPS |  |  |  |
| P95延迟 |  |  |  |
| P99延迟 |  |  |  |
| Max延迟 |  |  |  |
| 错误率 |  |  |  |

### 2. 数据说明
（对结果的解读，是否达到目标，有没有超出预期的地方）

## 经验总结
### 可复用经验
1. ...
2. ...

### 踩过的坑
1. ...
2. ...
```

### 10.5 特殊接口额外检查清单

**文件上传类接口（数据累积型）**：
- [ ] 压测前确认磁盘可用空间≥10GB
- [ ] 压测后立即执行清理命令（见 baselines/README.md §五.7）
- [ ] 记录中必须包含磁盘保护、并发控制的实现
- [ ] jmeter/README.md 必须提醒使用小文件压测、压测后清理

**toggle类写接口（点赞/收藏/关注）**：
- [ ] 基线必须明确记录"Check-Then-Act"竞态条件导致的错误率（理论值≈1-1/e≈37%）
- [ ] 优化方案必须使用数据库原子操作（DELETE-First或INSERT ... ON DUPLICATE KEY UPDATE）
- [ ] 跨服务通知必须异步发送，不能在数据库事务内同步调用

**N+1查询类接口**：
- [ ] 基线必须记录N+1查询次数（如N条帖子产生1+N次SQL）
- [ ] 优化方案必须使用批量查询（IN查询），单次IN不超过1000条
- [ ] 必须有Redis缓存策略（考虑一致性问题）

### 10.6 文档一致性检查

每次新增/修改优化文档后，必须做以下检查：

1. **状态一致性**：主README、master-plan、各子README中的接口状态必须一致（不能一个地方写已完成，另一个地方写待优化）
2. **索引完整性**：新创建的 .md/.jmx 文件必须在所有上级README的索引表中有对应条目
3. **链接有效性**：所有相对链接（[文字](路径)）必须能正确跳转到目标文件
4. **不提交Git**：完成文档更新后，运行 `git status` 确认 optimization-logs/ 下没有文件被add（本地文档不提交）
