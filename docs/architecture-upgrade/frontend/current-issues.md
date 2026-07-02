# 前端当前技术债务与问题总结

> **文档版本**: v1.0
> **创建日期**: 2026-07-01
> **说明**: 本文档详细列出前端现有技术选型中落后、草率、存在风险的部分。每个问题包含：问题描述、影响、严重程度、代码位置参考。

---

## 一、数据层与状态管理问题（P0/P1）

### 1.1 缺少服务端状态管理/数据缓存层 ✅ 已完成

**修复方案**：引入 TanStack Query v5（详见 [01-tanstack-query-integration.md](./01-tanstack-query-integration.md)）

---

### 1.2 HTTP 客户端过于简陋 ✅ 已完成

**修复方案**：Axios 替换手写 fetch（详见 [02-axios-http-client.md](./02-axios-http-client.md)）。已支持拦截器、超时控制、统一错误处理、FormData自动处理、文件上传进度、Token刷新架构预留。

---

### 1.3 状态管理分散且不规范 ✅ 已完成

**修复方案**：Zustand 模块化拆分 + persist 中间件（详见 [04-zustand-state-management.md](./04-zustand-state-management.md)）。themeStore/uiStore/toastStore 按领域拆分，服务端状态(TanStack Query)与客户端状态(Zustand)边界明确。

---

### 1.4 轮询效率低且实时性差 ✅ 前端已规范化（WebSocket待后端）

**已完成**：所有手动 setInterval 轮询替换为 TanStack Query `refetchInterval`，页面后台自动暂停，离开页面自动停止。详见 Phase 1.4 升级路线。

**遗留问题**（需后端 WebSocket 配合）：
- 实时性仍为最多5秒延迟
- 多Tab独立轮询
- 后端就绪后升级为消息驱动刷新（Phase 5.1）

---

## 二、工程化问题（P0/P1）

### 2.1 完全没有测试体系 ✅ 基础已搭建

**修复方案**：Vitest + React Testing Library + happy-dom（详见 [03-vitest-testing.md](./03-vitest-testing.md)）。基础测试框架已搭建，17个工具函数测试已通过。后续需逐步补充组件测试和E2E测试。

---

### 2.2 缺少 TypeScript 类型安全保障

**问题描述**：
- API 响应类型定义不完整，很多地方用 `any`（[http.ts](file:///E:/workspace_work/CampusShare/frontend/src/services/http.ts) 中 `ApiResponse<T = any>` 默认 any）
- DTO/VO/Entity 类型没有和后端对齐，手动定义容易出错
- 没有运行时类型校验（后端返回数据格式变了前端不知道）
- 第三方库的类型补全可能缺失
- tsconfig 配置比较宽松，没有开启严格模式（需要检查）

**示例问题**：[types.ts](file:///E:/workspace_work/CampusShare/frontend/src/services/types.ts) 类型定义可能不够完整

**严重程度**：🟡 P1

---

### 2.3 没有组件库，所有 UI 组件手写

**问题描述**：所有组件（Modal、Dialog、Toast、Dropdown、Pagination、InfiniteScroll、ImageCropper、Form、Input 等）都是手写的或依赖原生元素。

**问题**：
- 重复造轮子：Modal、Dialog、Tooltip、Popover 等基础组件没有抽象复用
- 交互一致性差：不同页面的加载状态、错误状态、空状态展示不一致
- 可访问性（a11y）差：没有 ARIA 属性、键盘导航、屏幕阅读器支持
- 动画不统一：弹窗、转场动画效果不一致

**涉及组件**：
- Toast 是手写的（虽然有，但其他基础组件缺失）
- 没有统一的 Modal/Dialog 组件（确认弹窗目前是直接删，没有确认步骤或用Toast代替）
- 没有 Loading 组件（按钮loading、页面loading、Skeleton 骨架屏）
- 没有表单组件和表单校验

**严重程度**：🟡 P1（不需要引入重型组件库，但需要自己封装基础组件或引入轻量Headless UI）

---

### 2.4 没有错误边界（Error Boundary）

**问题描述**：React 应用没有任何 Error Boundary 组件。

**影响**：
- 任何一个组件报错，整个应用白屏崩溃
- 没有降级 UI，用户体验极差
- 没有错误上报，无法感知线上问题

**涉及文件**：[App.tsx](file:///E:/workspace_work/CampusShare/frontend/src/App.tsx)、[main.tsx](file:///E:/workspace_work/CampusShare/frontend/src/main.tsx)

**严重程度**：🟡 P1

---

### 2.5 没有前端监控和错误上报

**问题描述**：
- 没有 JS 错误捕获和上报
- 没有性能监控（FP/FCP/LCP/TTI 等 Web Vitals）
- 没有用户行为埋点（PV/UV、点击率、功能使用率）
- 没有接口报错监控
- 无法了解线上运行情况

**严重程度**：🟡 P1

---

### 2.6 没有 Mock 方案

**问题描述**：前端开发依赖后端接口，后端没开发完前端无法开发；接口报错时无法排查是前端还是后端问题。没有 MSW（Mock Service Worker）等 Mock 方案。

**严重程度**：🟡 P1（影响协作效率）

---

### 2.7 ESLint 规则不完善，没有 Prettier，没有代码规范检查

**问题描述**：
- [eslint.config.js](file:///E:/workspace_work/CampusShare/frontend/eslint.config.js) 只有基础配置，没有 react-hooks 规则、import 排序、a11y 规则
- 没有 Prettier 统一代码格式化
- 没有 commit 前 lint 检查（husky + lint-staged）
- 代码风格不统一

**严重程度**：🟢 P2

---

### 2.8 没有 CI/CD 配置

**问题描述**：没有 GitHub Actions 或其他 CI 配置，每次手动构建部署。

**严重程度**：🟡 P1

---

## 三、性能问题（P1）

### 3.1 没有虚拟滚动（Virtual Scroll）

**问题描述**：帖子列表、评论列表、私信列表、通知列表、关注列表等长列表场景，一次性渲染所有 DOM。数据量大时（如几百上千条评论）会导致页面卡顿。

**影响**：
- 滚动掉帧
- 内存占用高
- 初始加载慢

**涉及页面**：
- [PostDetailPage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/PostDetailPage.tsx)（评论列表可能很长）
- [MessagePage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/MessagePage.tsx)（私信历史）
- [NotificationPage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/NotificationPage.tsx)（通知列表）
- 各类列表页

**严重程度**：🟡 P1（当前数据量小时不明显，但架构要支持）

---

### 3.2 没有图片优化

**问题描述**：
- 没有图片懒加载（虽然 loading="lazy" 是原生的，但没有占位符和错误兜底）
- 没有响应式图片（不同屏幕尺寸加载不同尺寸图片）
- 没有 WebP/AVIF 等现代格式
- 头像使用 dicebear 外部服务，国内可能访问慢
- 没有图片压缩和缩略图（帖子图片加载原图）

**严重程度**：🟡 P1

---

### 3.3 路由和代码分割不足

**问题描述**：
- [router/index.tsx](file:///E:/workspace_work/CampusShare/frontend/src/router/index.tsx) 中所有页面都是静态 import，没有用 `React.lazy` 做路由级代码分割
- 首屏加载所有 JS 代码，首屏时间长
- 没有组件级懒加载（大组件如头像裁剪、Markdown 编辑器应该懒加载）

**严重程度**：🟡 P1

---

### 3.4 没有记忆化优化，不必要的重渲染

**问题描述**：
- 组件没有合理使用 `React.memo`、`useMemo`、`useCallback`
- Context 没有拆分（AuthContext 可能导致大量重渲染）
- 列表渲染没有用稳定的 key（或用 index 作为 key）
- 内联函数和对象作为 props 传递，导致子组件不必要重渲染

**严重程度**：🟡 P1

---

## 四、用户体验问题（P1/P2）

### 4.1 没有加载状态分级

**问题描述**：
- 页面初次加载只有 loading... 文字，没有 Skeleton 骨架屏
- 按钮点击没有 loading 状态（可能重复提交）
- 下拉刷新/触底加载没有统一的加载指示器
- 没有全局 Loading 遮罩（如发帖子时）

**严重程度**：🟡 P1

---

### 4.2 没有 PWA 支持

**问题描述**：
- 没有 Service Worker，无法离线访问
- 没有添加到主屏幕能力
- 没有离线缓存策略
- 移动端体验差（没有状态栏颜色、启动画面等）

**严重程度**：🟢 P2（README 待开发功能列表中提到了）

---

### 4.3 没有国际化（i18n）框架

**问题描述**：所有中文硬编码在组件中，未来如果要支持多语言需要改动所有组件。

**严重程度**：🟢 P2（国内产品可暂缓，但架构要预留）

---

### 4.4 表单处理原始

**问题描述**：
- 没有表单状态管理和校验库（react-hook-form / formik）
- 每个表单自己维护 value/onChange/error 状态
- 没有统一的校验规则和错误提示
- 复杂表单（如设置页）代码臃肿

**涉及组件**：
- [LoginForm.tsx](file:///E:/workspace_work/CampusShare/frontend/src/components/auth/LoginForm.tsx)
- [RegisterForm.tsx](file:///E:/workspace_work/CampusShare/frontend/src/components/auth/ForgotPasswordForm.tsx)

**严重程度**：🟡 P1

---

### 4.5 没有暗色模式支持

**问题描述**：[useTheme.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/useTheme.ts) 存在但可能只是基础，Tailwind 暗色模式配置可能不完整。

**严重程度**：🟢 P2

---

## 五、架构与代码组织问题（P1）

### 5.1 没有自定义 Hooks 抽象复用逻辑

**问题描述**：
- 只有一个 [useTheme.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/useTheme.ts)，大量可复用的逻辑没有抽成 hooks：
  - `useRequest`：封装请求 loading/error/data
  - `useInfiniteScroll`：无限滚动
  - `useDebounce`：防抖
  - `useThrottle`：节流
  - `useClickOutside`：点击外部关闭
  - `useIntersectionObserver`：元素可见性检测（用于懒加载）
  - `useLocalStorage`/`useSessionStorage`：本地存储
  - `useMediaQuery`：响应式断点
- 相同逻辑在不同组件中重复实现

**严重程度**：🟡 P1

---

### 5.2 工具函数库不完整且不规范

**问题描述**：
- [utils/time.ts](file:///E:/workspace_work/CampusShare/frontend/src/utils/time.ts) 只有时间格式化
- [lib/utils.ts](file:///E:/workspace_work/CampusShare/frontend/src/lib/utils.ts) 只有 `cn` 函数（clsx+tailwind-merge）
- 缺少常用工具函数：防抖、节流、深拷贝、类型判断、数字格式化（文件大小、数量简写）、URL 处理等
- 没有引入成熟工具库如 lodash-es（按需引入）

**严重程度**：🟢 P2

---

### 5.3 目录结构可以更清晰

**问题描述**：
- 页面组件都平铺在 `pages/` 下，没有按功能域分组
- 组件没有按业务组件/基础组件明确区分
- 没有 `constants/` 目录放常量
- 没有 `types/` 目录统一放类型定义（types 在 services 下）

**严重程度**：🟢 P2

---

### 5.4 没有环境变量区分

**问题描述**：`API_BASE_URL` 通过 `import.meta.env.VITE_API_BASE_URL` 读取，但没有 `.env.development`、`.env.production` 等多环境配置。

**严重程度**：🟢 P2

---

## 六、安全问题（P1/P2）

### 6.1 XSS 防护依赖框架，没有主动防御

**问题描述**：
- React 默认会转义，但如果使用 `dangerouslySetInnerHTML` 可能引入 XSS（需要检查是否有使用）
- 用户输入的内容（帖子、评论）展示时，没有做 XSS 过滤（后端应该过滤，但前端也要防御）
- 没有 Content Security Policy (CSP)

**严重程度**：🟡 P1

---

### 6.2 Token 存储在 sessionStorage，有 XSS 风险

**问题描述**：当前 Token 存在 sessionStorage 中，XSS 攻击可以窃取。

**方案建议**：
- 更安全的方案是 Access Token 存在内存中，Refresh Token 用 HttpOnly Cookie（但需要后端配合）
- 或者短期保持现状，但要做好 XSS 防护

**严重程度**：🟡 P1（需要后端配合调整）

---

## 七、问题优先级汇总

| 类别 | P0 | P1 | P2 |
|------|----|----|----|
| 数据层 | 数据缓存层（TanStack Query） | HTTP客户端升级、状态管理规范化、轮询改WebSocket | - |
| 工程化 | 单元/组件/E2E测试 | TS类型严格化、错误边界、监控埋点、Mock方案、CI/CD | ESLint/Prettier规范 |
| 性能 | - | 虚拟滚动、图片优化、路由代码分割、重渲染优化 | - |
| UX | - | 加载状态分级、表单处理 | PWA、暗色模式、i18n |
| 架构 | - | 自定义Hooks抽象 | 工具库、目录结构、环境变量 |
| 安全 | - | XSS防护、Token存储安全 | - |

---

**下一步**：阅读 [前端升级路线图](./upgrade-roadmap.md) 了解具体的升级方案和技术选型。
