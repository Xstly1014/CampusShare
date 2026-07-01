---
title: 浏览帖子指南
topic: BROWSING
tags: [浏览帖子, 筛选, 排序, 分页, 分类]
---

# 浏览帖子指南

## 按学校浏览

- 接口：`GET /posts/school/{schoolId}`
- 可选参数：
  - `postType`：帖子类型筛选（resource/discussion/all），默认 all
  - `sortType`：排序方式，默认 latest
  - `page`：页码，默认 1
  - `size`：每页数量，默认 20

## 按分类浏览

- 按主分类：`GET /categories/{categoryId}/posts`
- 按子分类：`GET /categories/sub/{subCategoryId}/posts`
- 额外支持 `keyword` 参数进行关键词搜索

## 排序方式

| 排序方式 | 说明 |
|----------|------|
| latest | 按发布时间排序（最新优先）|
| hottest | 按收藏数排序（最热优先）|
| active | 按评论数排序（最活跃优先）|

## 学校统计

- `GET /posts/school-counts`：获取各学校的帖子数量
- `GET /categories/counts`：获取各分类的帖子数量

## 帖子详情

- `GET /posts/{postId}`：查看帖子完整内容，包含作者信息、文件信息、点赞/收藏/评论数等

## 浏览历史

- 系统会自动记录用户的浏览历史
- 可在个人中心查看"浏览历史"

## 分页说明

- 所有列表接口均支持分页
- 默认每页 20 条
- 返回结果包含总数、当前页、每页数量等信息
