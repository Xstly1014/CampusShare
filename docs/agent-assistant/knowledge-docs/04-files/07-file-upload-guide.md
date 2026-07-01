---
title: 文件上传指南
topic: FILE_UPLOAD
tags: [文件上传, 文件大小限制, multipart]
---

# 文件上传指南

## 上传接口

- 接口：`POST /files/upload`
- 请求格式：multipart/form-data
- 需要登录（携带 JWT token）

## 文件大小限制

- **最大文件大小：100MB**
- 超过限制将上传失败

## 上传流程

1. 在发帖页面，选择要上传的文件
2. 系统自动调用 `POST /files/upload` 上传
3. 上传成功后返回：
   - `url`：文件访问URL
   - `fileName`：文件名
   - `fileType`：文件类型
   - `fileSize`：文件大小（字节）
4. 将返回的信息填入帖子的文件字段

## 支持的文件类型

- 学习资料（PDF、DOC、DOCX、PPT、PPTX）
- 压缩包（ZIP、RAR、7Z）
- 图片（JPG、PNG、GIF）
- 其他常见文件类型

## 文件存储

- 文件按日期目录存储在服务器
- 上传路径使用绝对路径 /app/uploads/
- 文件通过 uploads_data 卷持久化，容器重启不丢失

## 注意事项

- 上传大文件时请耐心等待
- 请勿上传违规内容
- 文件上传成功后才能发布带有文件的帖子
