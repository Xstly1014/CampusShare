---
title: 发布帖子指南
topic: POSTING
tags: [发布帖子, 资源, 讨论, 文件上传, 分类]
---

# 发布帖子指南

## 帖子类型

CampusShare 支持两种帖子类型：

- **resource（资源）**：分享学习资料、文件等资源
- **discussion（讨论）**：发起话题讨论、交流想法

## 发帖必填字段

发布帖子需要提供以下信息：

| 字段 | 说明 |
|------|------|
| schoolId | 所属学校ID |
| categoryId | 主分类ID |
| subCategoryId | 子分类ID |
| postType | 帖子类型（resource/discussion）|
| title | 帖子标题 |
| content | 帖子正文内容 |

## 可选字段（资源类帖子）

| 字段 | 说明 |
|------|------|
| fileUrl | 上传文件的URL |
| fileName | 文件名 |
| fileType | 文件类型 |
| fileSize | 文件大小（字节）|

## 发帖流程

1. 点击页面右上角的发布按钮
2. 选择帖子类型（资源/讨论）
3. 选择学校和分类、子分类
4. 填写标题和正文内容
5. 如需上传文件，先通过文件上传接口上传文件
6. 点击"发布"完成

## 文件上传

- 上传接口：`POST /files/upload`（multipart/form-data 格式）
- 上传后返回文件 URL、文件名、文件类型、文件大小
- 将返回的信息填入帖子的 fileUrl/fileName/fileType/fileSize 字段
- 文件大小限制：100MB

## 帖子编辑与删除

- 编辑帖子：`PUT /posts/{postId}`
- 删除帖子：`DELETE /posts/{postId}`
- 只有帖子作者可以编辑和删除自己的帖子
