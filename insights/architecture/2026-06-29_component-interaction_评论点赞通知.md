# 组件交互：评论点赞通知（跨服务事件链路）

- **日期：** 2026-06-29
- **涉及组件：** 前端 React、Gateway、post-service（CommentController/CommentServiceImpl）、user-service（InternalUserController/NotificationServiceImpl）、MySQL
- **STAR — S：** 评论被点赞时需向评论作者发送 `COMMENT_LIKE` 通知；评论数据在 post-service，通知数据在 user-service，跨服务事件需保证"点赞成功 + 通知投递"最终一致

---

## 交互序列图（强制 Mermaid）

> 场景：用户 A 点赞用户 B 的评论 → post-service 写 comment_likes → Feign 调 user-service 写 notifications
> 标注了超时、降级、不通知自己的判断。

```mermaid
sequenceDiagram
    actor A as 用户A（点赞者）
    participant FE as 前端
    participant GW as Gateway
    participant POST as post-service<br/>CommentController
    participant USER as user-service<br/>InternalUserController
    participant DB_POST as MySQL comments/comment_likes
    participant DB_USER as MySQL notifications

    A->>FE: 点击评论点赞
    FE->>GW: POST /api/comments/{id}/like
    GW->>GW: JWT 验证 + 注入 X-User-Id=A
    GW->>POST: 转发

    POST->>POST: 查评论作者 authorId<br/>(SELECT user_id FROM comments WHERE id=?)
    POST->>POST: 判断 authorId == A？<br/>自己赞自己 → 不发通知

    alt 自己赞自己
        POST->>DB_POST: INSERT comment_likes (uk_comment_user 防重)
        POST-->>GW: 200 OK（已点赞）
    else 点赞他人评论
        rect rgb(240, 248, 255)
            Note over POST,DB_POST: post-service 本地事务
            POST->>DB_POST: INSERT comment_likes (comment_id, user_id=A)
            alt 唯一索引冲突（已赞过）
                DB_POST-->>POST: DuplicateKeyException
                POST-->>GW: 200 OK（幂等，已点赞）
            else 插入成功
                DB_POST-->>POST: OK
                POST->>DB_POST: UPDATE comments SET like_count = like_count + 1
            end
        end

        rect rgb(255, 248, 240)
            Note over POST,USER: 跨服务 Feign 调用（超时 5s）
            POST->>USER: Feign POST /api/internal/notifications<br/>{userId=authorId, senderId=A,<br/>type=COMMENT_LIKE, targetId=commentId}
            alt Feign 调用成功
                USER->>DB_USER: INSERT notifications<br/>(user_id=authorId, sender_id=A, type=COMMENT_LIKE)
                USER-->>POST: 200 OK
            else Feign 超时/失败（降级）
                POST->>POST: try-catch 捕获<br/>仅打 warn 日志，不影响点赞主流程
                Note over POST: 通知丢失（当前可接受）<br/>未来：本地消息表+异步重试
            end
        end

        POST-->>GW: 200 OK
    end

    GW-->>FE: 200 OK
    FE-->>A: 点赞高亮 + Toast"点赞成功"
```

---

## 状态机图（通知类型流转）

> 通知从产生到被阅读的状态流转。COMMENT_LIKE 是新增的通知类型。

```mermaid
stateDiagram-v2
    [*] --> Unread: 触发者点赞/评论/收藏<br/>INSERT notifications (is_read=0)
    Unread --> Read: 用户点击通知<br/>UPDATE is_read=1
    Unread --> Hidden: 用户关闭该类通知偏好<br/>(notify_likes=0)<br/>收纳篮仍显示但 unreadCount=0
    Read --> [*]
    Hidden --> Read: 用户主动查看收纳篮<br/>(不推红点但可查看)

    note right of Hidden
        关闭通知 ≠ 隐藏通知
        收纳篮始终显示，仅抑制红点
    end note
```

---

## 关键设计考量

### 同步 vs 异步决策
- **此交互中同步环节：** 点赞写 comment_likes + 更新 like_count（post-service 本地事务，必须同步）
- **此交互中"伪异步"环节：** Feign 调用 user-service 写通知——技术上同步，但业务上 try-catch 降级，通知失败不影响点赞主流程
- **理由：** 当前通知量级（单校几千条）下，同步 Feign + try-catch 降级足够简单可靠；引入 MQ 会增加运维复杂度（Broker 部署、消费组管理、消息积压监控）

### 错误传播
- **comment_likes 插入失败（唯一索引冲突）：** 幂等处理，返回 200 OK"已点赞"，不报错
- **Feign 调用 user-service 失败：** 不透传给用户，仅打 warn 日志，点赞仍返回成功
- **为什么这样设计：** 点赞是核心功能，通知是辅助功能。辅助功能失败不应阻塞核心功能。但代价是通知可能丢失——未来用本地消息表补偿

### 超时与重试

| 调用环节 | 超时 | 重试次数 | 退避策略 | 理由 |
|----------|------|----------|----------|------|
| FE → GW | 10s | 0 | 无 | 浏览器超时控制 |
| GW → POST | 10s | 0 | 无 | 网关透传 |
| POST → DB（本地） | 5s（HikariCP） | 0 | 无 | 数据库连接池超时 |
| POST → USER（Feign） | 5s | 0 | 无 | 当前无重试，失败即降级；未来引入 Spring Retry + 指数退避 |
| USER → DB（本地） | 5s | 0 | 无 | 数据库连接池超时 |

### 通知偏好过滤的交互
- **过滤点在 user-service 的 `getNotificationFeed`**：所有类型收纳篮都显示，但被关闭类型（如 `notify_likes=0`）的 `unreadCount=0`（不推红点）
- **`getUnreadCount`**：按偏好排除被关闭类型的未读数，底部导航栏红点总数不含被关闭类型
- **设计理由：** 用户关闭通知 ≠ 隐藏通知，收纳篮始终可查看历史通知，仅抑制红点推送
