# 知识库来源与结构

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、知识库定位

帮助中心知识库服务 HOW_TO / NAVIGATE 意图，回答「平台功能怎么用」「规则是什么」「入口在哪」。**与帖子检索库分离**——帖子库服务 SEARCH。

## 二、知识来源

| 来源 | 形式 | 获取方式 | 更新频率 |
|------|------|----------|----------|
| 平台功能说明（自撰） | Markdown 文档 | 手工编写，存仓库 `docs/agent-assistant/knowledge-docs/` | 随功能迭代 |
| 现有 docs/ | PRD/api-docs/tech-design | 解析提取 | 功能迭代后 |
| 用户高频 FAQ | Q-A 对 | 从反馈日志挖掘 | 每周 |
| 设置项说明 | 字段说明 | 从代码/配置提取 | 随代码 |

## 三、知识结构（分类树）

```
knowledge-docs/
├── 01-account/                 # 账号与认证
│   ├── register-login.md       # 注册登录、验证码
│   ├── password-recovery.md    # 忘记密码
│   ├── change-password.md      # 修改密码
│   ├── change-account.md       # 修改邮箱/手机号
│   └── jwt-token.md            # 双 Token 机制说明
├── 02-profile/                 # 个人资料
│   ├── edit-profile.md         # 改昵称/简介
│   ├── avatar-upload.md        # 头像上传与裁剪
│   └── privacy-settings.md     # 隐私开关(公开帖子/收藏/点赞/浏览/被搜)
├── 03-creator/                 # 创作者认证
│   ├── creator-rules.md        # 认证条件(获赞≥10000+发帖≥50)
│   ├── creator-apply.md        # 申请流程与材料
│   ├── creator-benefits.md     # 金色V权益
│   └── creator-admin.md        # 管理员审核
├── 04-posts/                   # 帖子
│   ├── create-post.md          # 发资源贴/讨论贴
│   ├── edit-delete-post.md     # 编辑删除
│   ├── file-upload.md          # 文件上传(≤100MB)
│   ├── like-star.md            # 点赞收藏
│   └── view-history.md         # 浏览历史
├── 05-comments/                # 评论
│   ├── comment-reply.md        # 评论与楼中楼
│   └── comment-like.md         # 评论点赞
├── 06-social/                  # 社交
│   ├── follow.md               # 关注/粉丝/互关
│   ├── message.md              # 私信
│   ├── message-rule.md         # 单向消息限制规则
│   └── message-hide.md         # 私信隐藏
├── 07-notifications/           # 通知
│   ├── notification-types.md   # 通知类型(点赞/收藏/关注/评论/回复)
│   ├── notification-prefs.md   # 通知偏好开关
│   └── notification-basket.md  # 收纳篮与红点规则
├── 08-categories/              # 分类与板块
│   ├── category-list.md        # 12 主分类清单
│   ├── school-list.md          # 8 所高校
│   └── subcategory.md          # 子板块说明
├── 09-search/                  # 搜索与发现
│   ├── search-posts.md         # 帖子搜索
│   └── browse.md               # 浏览与排序
└── 10-platform/                # 平台总览
    ├── what-is-campushare.md   # 平台介绍
    └── features-overview.md    # 功能总览
```

## 四、知识文档模板

每篇文档统一结构，便于分块与引用：

```markdown
---
id: creator-apply
title: 如何申请创作者认证
category: 03-creator
keywords: [创作者, 认证, 申请, 金色V, V]
related_features: [/creator-verification]
last_updated: 2026-06-30
---

# 如何申请创作者认证

## 认证条件
- 总获赞 ≥ 10000
- 发帖数 ≥ 50

## 申请入口
我的 → 设置 → 创作者认证，或直接访问 /creator-verification

## 申请材料
- 真实姓名
- 身份证号

## 审核流程
1. 提交申请 → 状态变为「审核中」
2. 管理员审核 → 「已通过」或「已驳回」(附驳回原因)
3. 通过后用户名旁显示金色 V 标识

## 常见问题
- Q: 获赞数怎么算？ A: 你所有帖子收到的点赞总数。
```

## 五、知识库与帖子库的边界

| 维度 | 知识库 | 帖子库 |
|------|--------|--------|
| 内容 | 平台功能说明 | 用户生成帖子 |
| 意图 | HOW_TO / NAVIGATE | SEARCH |
| 更新 | 人工维护 | 用户发帖自动同步 |
| 规模 | ~50 篇文档 | 数万~百万帖 |
| 向量库 | 独立小集合 | 大集合 |

## 六、决策记录 (ADR)

### ADR-016: 知识库与帖子库分离
- **理由**：两者检索目标、更新方式、规模、混排权重都不同；混在一起会污染 HOW_TO 结果。
- **实现**：两个向量集合（PG-Vector 不同表），分别检索。

### ADR-017: 知识文档放仓库版本化
- **理由**：知识随功能迭代，Git 版本化可追溯；后台更新走 PR。
- **运行时加载**：服务启动加载 + 定时热更新（检测文件变更）。
