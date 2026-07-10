# Few-shot 示例库

> 状态: 草稿  
> 最后更新: 2026-06-30

Few-shot 示例放 L1 之后、用户 query 之前。按任务分库。

## 一、意图分类 Few-shot

```
示例1：
输入：怎么成为创作者
输出：{"intent":"HOW_TO","sub_intent":"feature_help","confidence":0.95,"rewritten_query":"创作者认证 申请","slots":{},"hyde_doc":null}

示例2：
输入：求清华操作系统期末复习资料
输出：{"intent":"SEARCH","sub_intent":"resource","confidence":0.92,"rewritten_query":"操作系统 期末 复习 资料","slots":{"school":"清华","post_type":"resource","sort":null},"hyde_doc":"这是一份清华大学操作系统期末复习资料，涵盖进程管理、内存管理、文件系统、IO 管理等核心知识点..."}

示例3：
输入：游戏板块在哪
输出：{"intent":"NAVIGATE","sub_intent":"section_loc","confidence":0.9,"rewritten_query":"游戏","slots":{"category":"游戏"},"hyde_doc":null}

示例4：
输入：那个有下载链接的是哪个
输出：{"intent":"CLARIFY","sub_intent":"coreference","confidence":0.88,"rewritten_query":"上轮结果中 file_url 非空的帖子","slots":{},"hyde_doc":null}

示例5：
输入：帮我发个帖子
输出：{"intent":"OUT_OF_SCOPE","sub_intent":"write_action","confidence":0.97,"rewritten_query":"","slots":{},"hyde_doc":null}

示例6：
输入：量子力学是什么
输出：{"intent":"OUT_OF_SCOPE","sub_intent":"open_domain","confidence":0.95,"rewritten_query":"","slots":{},"hyde_doc":null}
```

## 二、ReAct Few-shot

```
示例（用户：求清华OS资料）：
Assistant: [tool_call: search_posts(query="操作系统 期末 复习 资料", school="清华", post_type="resource")]
Tool: {"status":"success","summary":"找到5条相关帖子","data":[...]}
Assistant: 根据检索，为你找到以下清华操作系统复习资料：[1]《操作系统期末复习笔记》... [2]《OS 习题集》...

示例（用户：怎么关通知）：
Assistant: [tool_call: search_knowledge(query="通知偏好设置")]
Tool: {"status":"success","summary":"找到帮助文档","data":[{"title":"通知偏好设置","content":"..."}]}
Assistant: 关闭通知红点步骤：1. 进入「我的」→「设置」→「通知偏好」[1]；2. 关闭「点赞收藏通知」开关...
```

## 三、Few-shot 维护

- 示例存仓库 `docs/agent-assistant/prompt-assets/few-shot/`，版本化。
- 每条标注适用意图、难度。
- 失败 case 经人工审核后转为新 Few-shot（持续学习）。
- 数量控制：每任务 5-10 条，过多稀释。

## 四、Dynamic Few-shot（进阶）

- 不固定示例，而是从示例库检索与当前 query 最相似的 3-5 条动态注入。
- 用向量检索选 Few-shot（示例也向量化）。
- 提升泛化，但增加复杂度，进阶做。

## 五、决策记录 (ADR)

### ADR-042: Few-shot 数量每任务 5-10 条
- **理由**：太少不泛化，太多稀释注意力且费 token。
- **进阶**：Dynamic Few-shot 按相似度选。
