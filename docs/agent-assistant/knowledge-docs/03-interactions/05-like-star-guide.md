---
title: 点赞与收藏指南
topic: INTERACTION
tags: [点赞, 收藏, 喜欢]
---

# 点赞与收藏指南

## 点赞功能

- **点赞/取消点赞**：`POST /posts/{postId}/like`（切换状态）
- 点赞后帖子 likeCount 增加
- 再次点击取消点赞
- 系统会通知帖子作者有人点赞

## 收藏功能

- **收藏/取消收藏**：`POST /posts/{postId}/star`（切换状态）
- 收藏后帖子 starCount 增加
- 再次点击取消收藏
- 系统会通知帖子作者有人收藏

## 查看状态

- `GET /posts/{postId}/status`：返回当前用户对该帖子的 `starred`（是否已收藏）和 `liked`（是否已点赞）状态

## 我的点赞和收藏

- `GET /posts/starred`：查看我收藏的所有帖子
- `GET /posts/liked`：查看我点赞过的所有帖子
- 均支持分页

## 通知

- 当其他用户点赞或收藏你的帖子时，你会收到通知
- 点赞通知类型：LIKE
- 收藏通知类型：STAR
