---
title: 评论与回复指南
topic: COMMENT
tags: [评论, 回复, 点赞评论]
---

# 评论与回复指南

## 查看评论

- 接口：`GET /posts/{postId}/comments`
- 返回帖子的所有评论，包含评论者信息、点赞数、回复关系等

## 发表评论

- 接口：`POST /posts/{postId}/comments`
- 请求体字段：
  - `content`：评论内容（必填）
  - `parentId`：父评论ID（回复已有评论时填写）
  - `replyToUserId`：被回复用户ID（回复其他用户的评论时填写）

## 评论结构

每条评论包含以下信息：
| 字段 | 说明 |
|------|------|
| id | 评论ID |
| postId | 所属帖子ID |
| userId | 评论者ID |
| username | 评论者用户名 |
| avatarUrl | 评论者头像 |
| parentId | 父评论ID（顶级评论为空）|
| replyToUserId | 被回复用户ID |
| replyToUsername | 被回复用户名 |
| content | 评论内容 |
| likeCount | 评论点赞数 |
| liked | 当前用户是否已点赞 |
| isAuthor | 是否为帖子作者 |
| createTime | 评论时间 |

## 删除评论

- 接口：`DELETE /posts/comments/{commentId}`
- 只有评论者本人或帖子作者可以删除评论

## 评论点赞

- 接口：`POST /posts/comments/{commentId}/like`（切换状态）
- 评论点赞通知类型：COMMENT_LIKE

## 我的评论

- 接口：`GET /posts/my-comments`
- 查看自己发表的所有评论

## 通知

- 评论通知类型：COMMENT
- 回复通知类型：REPLY
- 评论点赞通知类型：COMMENT_LIKE
