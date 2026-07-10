# 结构化输出

> 状态: 草稿  
> 最后更新: 2026-06-30

## 一、为什么结构化

- 意图分类、改写、反思需程序解析 → JSON。
- 答案面向用户 → 自然语言 + 引用标记。
- 工具调用 → Function Calling 结构。

## 二、JSON 输出保障

### 2.1 Prompt 约束
- 明确「只输出 JSON，无其他文字」。
- 给 JSON schema 示例。

### 2.2 解析容错
- 提取首个 `{` 到末尾 `}` 的子串再 parse。
- parse 失败 → 修复重试（再调一次 LLM「请修复 JSON」）。
- 仍失败 → 兜底默认值（意图兜底 SEARCH）。

### 2.3 JSON Schema 校验
- 用 Jackson 校验字段类型与必填。
- 缺字段用默认值（confidence 缺省 0.5）。

## 三、引用标记规范

答案中引用用 `[n]`，n 从 1 递增，对应 refs 列表第 n 项。
```
根据检索，为你找到 [1]《操作系统期末复习笔记》和 [2]《OS 习题集》...
```
refs:
```json
[{"ref_id":"post:uuid1","type":"post","title":"操作系统期末复习笔记","jump_url":"/school/1/post/111"},
 {"ref_id":"post:uuid2","type":"post","title":"OS 习题集","jump_url":"/school/1/post/222"}]
```

## 四、跳转卡标记

答案中需要跳转的地方用 `[跳转:label](action)` 或单独 refs type=`feature`。
```
申请创作者认证请前往 [创作者认证页]。
```
refs 含 `{"type":"feature","title":"创作者认证","jump_url":"/creator-verification"}`。

## 五、决策记录 (ADR)

### ADR-044: JSON 输出 + 容错解析
- **理由**：LLM 偶发不严格遵守 JSON，需容错。
- **实现**：提取 + 重试 + 兜底三段。
