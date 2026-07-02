# 前端架构升级 02：HTTP 客户端升级（Axios 替代手写 fetch）

> **文档版本**: v1.0
> **完成日期**: 2026-07-02
> **对应阶段**: Phase 1.2 - HTTP 客户端升级

---

## 一、重构背景

### 1.1 原有问题

重构前使用原生 `fetch` 进行了简陋封装（[http.ts](file:///E:/workspace_work/CampusShare/frontend/src/services/http.ts)），存在以下问题：

| 问题 | 影响 |
|------|------|
| 没有请求/响应拦截器机制 | Token注入、错误处理、响应解包散落在各处 |
| 没有超时控制 | 网络差时请求无限等待，用户体验差 |
| 没有请求取消 | 组件卸载后请求仍在继续，可能导致内存泄漏 |
| 401无统一处理 | Token过期每个页面需要自己处理 |
| 文件上传独立实现 | `file.ts` 自己直接写 fetch，不走统一封装，代码重复 |
| 无上传进度支持 | 大文件上传无法显示进度条 |
| 错误处理简陋 | 所有错误统一抛 `new Error(message)`，无错误分类 |

### 1.2 为什么选 Axios

| 候选方案 | 拦截器 | 超时 | 进度监控 | 取消请求 | TypeScript | 生态 |
|----------|--------|------|----------|----------|------------|------|
| **Axios** | ✅ 强大 | ✅ | ✅ | ✅ (AbortController) | ✅ | 成熟 |
| Ky | ✅ | ✅ | ❌ | ✅ | ✅ | 轻量，基于fetch |
| ofetch | ✅ | ✅ | ❌ | ✅ | ✅ | Nuxt生态 |
| redaxios | ✅ | ❌ | ❌ | ❌ | ✅ | 极小，fetch风格 |

**决策理由**：
- Axios 拦截器机制最成熟，适合统一处理认证、错误、响应转换
- 原生支持上传/下载进度监控
- 生态完善，社区活跃，TypeScript 支持一流
- TanStack Query 与 Axios 无绑定，可以完美配合

---

## 二、实施内容

### 2.1 核心架构设计

**关键兼容性设计**：`api.get/post/put/delete` 对外接口签名**完全不变**，返回类型仍然是 `ApiResponse<T>`（即 `{code, message, data, timestamp}`），上层调用方无需任何修改。响应拦截器内部解包 axios 的 `AxiosResponse` 外壳，直接返回 `response.data`（即后端的 `ApiResponse`），保持 services/queries/mutations 层代码零改动。

```
请求流程：
  调用方 → api.get<T>(url) → 请求拦截器(加Token) → axios发送请求
                ↓
  调用方 ← ApiResponse<T> ← 响应拦截器(解包+错误处理) ← 后端返回
```

### 2.2 新功能特性

#### 1. 请求拦截器
- 自动从 sessionStorage 获取 Token 注入 `Authorization: Bearer <token>`
- FormData 请求自动删除 `Content-Type`，让浏览器自动设置 multipart boundary
- 超时时间默认 15 秒

#### 2. 响应拦截器
- 成功响应：自动解包 `response.data`（去除 axios 外层包装），检查业务 code !== 200 时抛出业务错误
- 网络错误（无 response）：提示"网络连接失败"
- **401 Unauthorized**：
  - 预留 Token 自动刷新机制（队列防并发刷新），等后端实现 `/auth/refresh-token` 接口后即可启用
  - 当前无 refreshToken 或刷新失败时，清除本地认证信息并跳转登录页
- 403 Forbidden：提示"没有权限执行此操作"
- 5xx 错误：提示"服务器错误，请稍后重试"

#### 3. Token 刷新架构（预留）
实现了标准的"并发请求刷新Token队列"模式：
- `isRefreshing` 标志防止并发刷新
- 401时第一个请求触发刷新，其余请求进入 `failedQueue` 等待
- 刷新成功后批量重试队列中所有请求
- 刷新失败时队列全部reject，跳登录页

后端目前只在登录/注册时返回 refreshToken 但未实现 `/auth/refresh-token` 端点，当前行为是直接跳登录页。等后端实现刷新接口后，只需确保接口请求/响应格式匹配即可自动生效，无需改动前端架构。

#### 4. 文件上传统一
- 新增 `api.upload()` 方法，统一走 axios 实例
- 支持 `onUploadProgress` 回调，返回 `{loaded, total, percent}` 方便显示进度条
- `file.ts` 重构为调用 `api.upload()`，删除重复的 fetch 代码

#### 5. 错误分类
不同错误场景给出明确中文提示，不再是千篇一律的"请求失败"。

### 2.3 文件变更清单

**修改文件**：
- [http.ts](file:///E:/workspace_work/CampusShare/frontend/src/services/http.ts) - 核心HTTP客户端，从手写fetch重写为axios
- [file.ts](file:///E:/workspace_work/CampusShare/frontend/src/services/file.ts) - 文件上传改为使用统一api.upload
- [AuthContext.tsx](file:///E:/workspace_work/CampusShare/frontend/src/context/AuthContext.tsx) - login/register时存储refreshToken，logout时清除
- `package.json` / `package-lock.json` - 新增axios依赖

**无修改文件**（上层接口兼容，零改动）：
- services/ 下所有其他API文件（auth.ts/post.ts/user.ts/message.ts等）
- hooks/queries/ 下所有TanStack Query hooks
- hooks/mutations/ 下所有mutation hooks
- 所有页面组件

---

## 三、升级效果

| 能力 | 重构前 | 重构后 |
|------|--------|--------|
| 请求超时 | ❌ 无限制 | ✅ 15秒超时 |
| 拦截器 | ❌ 手动实现 | ✅ axios拦截器，统一处理 |
| Token注入 | ✅ 手动在request函数中 | ✅ 请求拦截器自动注入 |
| 401处理 | ❌ 无统一处理，各页面自行处理 | ✅ 统一拦截+预留自动刷新+跳登录 |
| Token自动刷新 | ❌ 不存在 | ✅ 架构预留，后端实现接口即可启用 |
| 并发刷新保护 | ❌ - | ✅ 队列机制，不会多次刷新 |
| 文件上传 | ❌ 独立fetch实现，重复代码 | ✅ 统一api.upload，支持进度回调 |
| 上传进度 | ❌ 不支持 | ✅ onUploadProgress回调 |
| 错误分类 | ❌ 统一"请求失败" | ✅ 网络/401/403/5xx/业务错误分类提示 |
| FormData支持 | ❌ 手动处理headers | ✅ 自动识别，删除Content-Type |
| services层兼容性 | - | ✅ 完全兼容，零改动 |

---

## 四、验证结果

- ✅ TypeScript 类型检查通过（`npx tsc --noEmit`）
- ✅ 生产构建成功（1962 modules，2.72s）
- ✅ 所有上层 services/queries/mutations 零改动
- ✅ 未迁移页面不受影响

---

## 五、后续工作

1. **后端实现 `/auth/refresh-token` 接口**后，前端自动刷新逻辑即可生效（当前有refreshToken但无刷新端点，401直接跳登录）
2. 后续在调用fileApi.upload的发帖页面添加上传进度条UI
3. 后续配合TanStack Query的queryClient，401刷新后可invalidateQueries重发失败的查询
