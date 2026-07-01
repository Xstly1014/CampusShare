---
title: 分类体系总览
topic: CATEGORIES
tags: [分类, 子分类, 学校, 资源分类]
---

# 分类体系总览

## 分类类型

CampusShare 的分类分为两种类型：

- **school**：学校分类，用于按学校筛选帖子
- **category**：内容分类，用于按主题筛选帖子

## 主分类列表

共 12 个主分类：

| 分类名 | 类型 | 说明 |
|--------|------|------|
| 校园 | school | 按学校浏览帖子 |
| 音乐 | category | 音乐相关资源与讨论 |
| 影视 | category | 电影、电视剧相关 |
| 动漫 | category | 动画、漫画相关 |
| 游戏 | category | 游戏相关资源与讨论 |
| 股市 | category | 股票投资相关 |
| 面经 | category | 面试经验分享 |
| 软件 | category | 软件资源分享 |
| 美食 | category | 美食相关内容 |
| 旅行 | category | 旅行经验与攻略 |
| 摄影 | category | 摄影作品与技巧 |
| 读书 | category | 书籍推荐与读后感 |

## 子分类示例

| 主分类 | 子分类示例 |
|--------|------------|
| 音乐 | 华语流行、欧美流行 |
| 影视 | 动作、喜剧 |
| 动漫 | 热血、恋爱 |
| 游戏 | PC游戏、手机游戏 |
| 股市 | A股、港股 |
| 面经 | 互联网、金融 |
| 软件 | Windows、macOS |
| 美食 | 菜谱分享、探店 |
| 旅行 | 国内游、出境游 |
| 摄影 | 人像、风光 |
| 读书 | 小说文学、技术书籍 |

## 按分类浏览

- 按主分类：`GET /categories/{categoryId}/posts`
- 按子分类：`GET /categories/sub/{subCategoryId}/posts`
- 支持关键词搜索：`keyword` 参数
- 分类帖子统计：`GET /categories/counts`
