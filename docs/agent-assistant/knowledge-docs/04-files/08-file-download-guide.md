---
title: 文件下载指南
topic: FILE_DOWNLOAD
tags: [文件下载, 下载记录]
---

# 文件下载指南

## 下载方式

- 资源类帖子附带文件时，在帖子详情页可看到下载按钮
- 点击下载按钮即可下载文件
- 下载链接来自帖子的 fileUrl 字段

## 下载记录

- 接口：`GET /posts/my-downloads`
- 系统会记录用户的下载历史
- 可在个人中心查看"我的下载"

## Nginx 配置

- Nginx 配置了 client_max_body_size 100M 以支持大文件上传下载
- 文件通过 Nginx 反向代理提供服务

## 注意事项

- 只有资源类（resource）帖子才有文件
- 讨论类（discussion）帖子不附带文件
- 下载文件需要登录
- 请勿传播违规文件
