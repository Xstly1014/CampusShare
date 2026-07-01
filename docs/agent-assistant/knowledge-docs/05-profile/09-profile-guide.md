---
title: 个人主页指南
topic: PROFILE
tags: [个人主页, 头像, 昵称, 简介, 创作者等级]
---

# 个人主页指南

## 查看个人主页

- 接口：`GET /users/{userId}/profile`
- 可查看任何用户的公开主页信息

## 主页信息

| 字段 | 说明 |
|------|------|
| username | 用户名 |
| avatarUrl | 头像URL |
| bio | 个人简介 |
| postCount | 发布帖子数 |
| totalViews | 总浏览量 |
| totalLikes | 总获赞数 |
| totalStars | 总收藏数 |
| followerCount | 粉丝数 |
| followingCount | 关注数 |
| following | 当前用户是否关注了TA |
| self | 是否是自己 |
| creator | 是否为创作者 |
| admin | 是否为管理员 |
| creatorLevel | 创作者等级 |
| creatorLevelName | 创作者等级名称 |

## 编辑个人信息

- `PUT /users/me`：修改用户名、头像、简介等基本信息
- `PUT /users/me/password`：修改密码
- `PUT /users/me/email`：修改邮箱
- `PUT /users/me/phone`：修改手机号
- `PUT /users/me/privacy`：修改隐私设置
- `PUT /users/me/notification-settings`：修改通知设置

## 实名认证

- 接口：`POST /users/me/real-name-verify`
- 实名认证后可能获得更多权限

## 隐私设置

- 可控制公开帖子、公开收藏、公开点赞、公开浏览历史的可见性
