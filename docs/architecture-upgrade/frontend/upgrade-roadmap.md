# 前端升级路线图

> **文档版本**: v1.0
> **创建日期**: 2026-07-01
> **说明**: 本文档定义了前端各阶段的升级方案、技术选型对比、实施步骤和验证标准。Agent 实施每个升级前必须仔细阅读对应章节。

---

## Phase 1: 数据层重构（P0，1-2周）

### 1.1 引入 TanStack Query (React Query) 作为服务端状态管理

**选型决策**：选择 **TanStack Query v5**（而非 SWR/RTK Query/Apollo Client）

**选型对比**：
| 方案 | 缓存 | 请求去重 | 乐观更新 | 无限滚动 |  DevTools | 生态 | 学习曲线 |
|------|------|---------|---------|---------|-----------|------|---------|
| TanStack Query v5 | ✅ 自动缓存+GC | ✅ | ✅ | ✅ | ✅ 强大 | React/Vue/Solid 多框架 | 低 |
| SWR | ✅ | ✅ | ✅ | 需手动 | ❌ | React Only | 极低 |
| RTK Query | ✅ | ✅ | ✅ | ✅ | ❌（Redux DevTools） | Redux 生态 | 中 |
| Apollo Client | ✅ | ✅ | ✅ | ✅ | ✅ | GraphQL 生态 | 高（针对REST重） |

**决策理由**：
- 功能最全面：自动缓存、后台刷新、请求去重、乐观更新、无限滚动、分页查询、离线支持
- DevTools 非常强大，能看到所有查询状态、缓存内容，方便调试
- 与 Zustand 完美配合：TanStack Query 管服务端状态（来自API的数据），Zustand 管客户端状态（UI状态、全局状态如Toast、Auth）
- 社区活跃，文档完善，v5 是最新稳定版
- 支持 React 18 Suspense、Transitions 等新特性

**核心概念**：
- **Query**：用于获取数据（GET 请求），自动缓存、后台刷新
- **Mutation**：用于修改数据（POST/PUT/DELETE），支持乐观更新、失效缓存
- **QueryClient**：全局配置、缓存管理
- **Query Keys**：缓存键，数组形式，自动关联

**实施步骤**：

1. **安装依赖**：
   ```bash
   npm install @tanstack/react-query @tanstack/react-query-devtools
   ```

2. **配置 QueryClientProvider**：
   - 在 [main.tsx](file:///E:/workspace_work/CampusShare/frontend/src/main.tsx) 中添加 QueryClientProvider
   - 配置合理的默认值：
     - `staleTime`: 5分钟（数据5分钟内认为新鲜，不重新请求）
     - `cacheTime`: 30分钟（未使用的缓存保留30分钟）
     - `retry`: 1次（网络错误重试1次，业务错误不重试）
     - `refetchOnWindowFocus`: 生产环境 true，开发环境 false
   - 开发环境开启 ReactQueryDevtools

3. **封装 API Hooks**：
   所有 API 调用封装为自定义 hooks，放在 `src/hooks/queries/` 和 `src/hooks/mutations/` 下：
   ```
   src/hooks/
   ├── queries/
   │   ├── usePosts.ts          # 帖子列表、详情
   │   ├── useComments.ts       # 评论
   │   ├── useCategories.ts     # 分类
   │   ├── useUser.ts           # 用户信息
   │   ├── useNotifications.ts  # 通知
   │   └── useMessages.ts       # 私信
   └── mutations/
       ├── usePostMutations.ts  # 发帖/删帖/点赞/收藏
       ├── useAuthMutations.ts  # 登录/注册
       └── useCommentMutations.ts # 评论/回复
   ```

   示例：
   ```typescript
   // hooks/queries/usePosts.ts
   export function usePostDetail(postId: string) {
     return useQuery({
       queryKey: ['posts', postId],
       queryFn: () => postApi.getPostDetail(postId),
       enabled: !!postId,
     })
   }
   
   // hooks/mutations/usePostMutations.ts
   export function useLikePost() {
     const queryClient = useQueryClient()
     return useMutation({
       mutationFn: (postId: string) => postApi.likePost(postId),
       // 乐观更新
       onMutate: async (postId) => {
         await queryClient.cancelQueries({ queryKey: ['posts', postId] })
         const previousPost = queryClient.getQueryData(['posts', postId])
         queryClient.setQueryData(['posts', postId], (old: any) => ({
           ...old,
           liked: true,
           likeCount: old.likeCount + 1,
         }))
         return { previousPost }
       },
       onError: (err, postId, context) => {
         queryClient.setQueryData(['posts', postId], context.previousPost)
         toast.error('点赞失败')
       },
       // 失效相关缓存，后台刷新
       onSettled: (data, error, postId) => {
         queryClient.invalidateQueries({ queryKey: ['posts', postId] })
       },
     })
   }
   ```

4. **改造页面组件**：
   - 移除各页面中的 `useState` 管理 loading/error/data
   - 使用封装好的 hooks 获取数据
   - 利用 `isLoading`、`isError`、`data` 等状态渲染 UI
   - 列表页使用 `useInfiniteQuery` 实现无限滚动

5. **替换轮询**：
   - 私信、通知等需要实时更新的场景，使用 TanStack Query 的 `refetchInterval` 配置轮询（比手动轮询规范，页面不可见时自动暂停）
   - 等后端 WebSocket 升级后，改为 `queryClient.invalidateQueries()` 在收到消息时触发刷新

6. **验证标准**：
   - [ ] 切换页面回来不重新请求（缓存命中）
   - [ ] 多个组件使用同一个数据只发一次请求（请求去重）
   - [ ] 点赞/收藏乐观更新生效，失败自动回滚
   - [ ] 无限滚动正常工作（翻页不重复、不丢失）
   - [ ] 页面隐藏时轮询暂停，可见时恢复
   - [ ] DevTools 能看到所有查询状态
   - [ ] 所有现有功能正常运行（无回归）

---

### 1.2 升级 HTTP 客户端（Axios 替代手写 fetch）

**选型决策**：选择 **Axios**（而非 Ky / ofetch / redaxios）

**选型理由**：
- 拦截器机制强大，统一处理请求/响应
- 自动 JSON 转换
- 请求取消（AbortController 已支持但Axios封装更好）
- 超时控制
- 上传/下载进度监控
- 拦截器中统一处理 401 Token 刷新、错误提示
- 生态成熟，TypeScript 支持好

**注意**：TanStack Query 不强制绑定 HTTP 客户端，可以用 Axios 替换现有 fetch 封装，TanStack Query 调用 Axios 方法即可。

**实施步骤**：

1. **安装依赖**：
   ```bash
   npm install axios
   ```

2. **重构 http.ts 为 axios 实例**：
   - 创建 axios 实例，配置 baseURL、超时时间（10秒）
   - 请求拦截器：统一添加 Authorization header
   - 响应拦截器：统一解包 `.data.data`（后端返回 `{code, message, data}`）、统一错误处理
   - Token 刷新逻辑：401 时用 refreshToken 获取新 token，重试原请求（防止并发刷新用队列）
   - 文件上传进度回调支持

3. **API 层保持不变**：services/ 下的 auth.ts/post.ts 等改为调用 axios 实例，对外接口不变，上层无感

4. **验证标准**：
   - [ ] 所有接口正常调用
   - [ ] 请求自动携带 Token
   - [ ] Token 过期自动刷新，用户无感知
   - [ ] 网络错误、超时统一提示
   - [ ] 文件上传显示进度

---

### 1.3 状态管理规范化（Zustand + TanStack Query 分工）

**方案**：
- **TanStack Query**：所有服务端状态（来自API的数据）——帖子、评论、用户信息、通知列表等
- **Zustand**：纯客户端 UI 状态 —— Toast、当前主题、WebSocket 连接状态、全局弹窗状态等
- **AuthContext**：保留但精简，只存当前用户信息和登录状态（用户信息也可以用 TanStack Query 缓存，但认证状态是客户端状态）

**实施**：
1. 拆分现有 Zustand store，按功能域创建多个 store（不要一个大 store）
   - `useToastStore`：Toast 通知（已有）
   - `useUIStore`：全局UI状态（侧边栏展开、弹窗状态）
   - `useWebSocketStore`：WebSocket连接状态、未读消息数实时更新
2. 所有 API 数据从 TanStack Query hooks 获取，不要存在 Zustand 中
3. 使用 `zustand/middleware` 的 `persist` 中间件持久化需要跨刷新保留的UI状态

---

## Phase 2: 工程化体系建设（P0/P1，1-2周）

### 2.1 测试体系搭建

**选型决策**：
- **单元测试/组件测试**：Vitest + React Testing Library
- **E2E 测试**：Playwright（而非 Cypress，Playwright 更现代、更快、多浏览器支持）

**理由**：
- Vitest 与 Vite 原生集成，速度极快，兼容 Jest API
- React Testing Library 是测试 React 组件的标准库，以用户行为为中心
- Playwright 由微软维护，支持 Chromium/Firefox/WebKit，自动等待、网络拦截能力强

**实施步骤**：

1. **安装依赖**：
   ```bash
   npm install -D vitest @testing-library/react @testing-library/jest-dom @testing-library/user-event jsdom
   npm install -D @playwright/test
   ```

2. **配置 Vitest**：
   - vite.config.ts 中添加 test 配置
   - 设置 setup 文件，引入 jest-dom matchers
   - 配置测试覆盖率（c8）

3. **编写测试用例**（从核心功能开始）：
   - 工具函数测试（utils/ 下所有函数）
   - 自定义 hooks 测试
   - 核心组件测试（LoginForm、Toast、NavBar）
   - 关键流程 E2E 测试（注册→登录→发帖→评论→登出）

4. **验证标准**：
   - [ ] `npm run test` 能运行单元测试
   - [ ] `npm run test:e2e` 能运行 E2E 测试
   - [ ] 核心工具函数覆盖率 100%
   - [ ] CI 配置后自动运行测试

---

### 2.2 错误边界（Error Boundary）

**实施**：
1. 创建 `components/common/ErrorBoundary.tsx`
2. 不同级别的错误边界：
   - 根级别：整个应用崩溃兜底
   - 页面级别：单个页面出错不影响其他页面
   - 组件级别：小组件（如评论列表）出错降级显示
3. 错误发生时显示友好降级 UI（"出错了，点击重试"按钮）
4. 开发环境显示错误详情，生产环境上报错误

---

### 2.3 前端监控和埋点

**选型决策**：
- **错误监控 + 性能监控**：接入 Sentry（开源，可自建或用SaaS）
- **Web Vitals**：web-vitals 库采集 FP/FCP/LCP/CLS/FID/TTFB
- **用户行为埋点**：手动埋点关键操作（登录、发帖、评论、点击等），先打日志到控制台，后续接后端埋点接口

**实施步骤**：
1. 安装 `@sentry/react`
2. main.tsx 初始化 Sentry（DSN 从环境变量读取）
3. ErrorBoundary 集成 Sentry 上报
4. 采集 Web Vitals 指标上报
5. 关键操作埋点（用封装的 `track()` 函数）

---

### 2.4 代码规范与格式化

**实施**：
1. **安装 Prettier**：统一代码格式化
2. **完善 ESLint**：
   - 启用 `eslint-plugin-react-hooks`（检查 hooks 依赖数组）
   - 启用 `eslint-plugin-react-refresh`（热更新规范）
   - 考虑 `eslint-plugin-jsx-a11y`（可访问性检查）
   - 启用 `@typescript-eslint` 严格规则
3. **配置 Husky + lint-staged**：
   - pre-commit hook：运行 lint-staged（eslint --fix + prettier --write）
   - commit-msg hook：校验 commit message 格式（Conventional Commits）
4. **tsconfig 开启严格模式**：`strict: true`、`noUncheckedIndexedAccess: true` 等

---

### 2.5 TypeScript 类型强化

**实施**：
1. tsconfig.json 开启严格模式
2. 移除所有 `any`，或用 `unknown` 替代并做类型收窄
3. 定义完整的 API 类型（services/types.ts 完善）
4. 引入 Zod 做运行时类型校验（后端返回数据校验，防止格式变化导致前端崩溃）

**为什么用 Zod**：
- 与 TypeScript 完美集成，类型自动推导
- API 响应数据做运行时校验，不符合预期给出清晰错误
- 也可用于表单校验

---

### 2.6 CI/CD 配置

**实施**：
创建 `.github/workflows/ci.yml`：
1. PR 和 push 到 master 时触发
2. 步骤：安装依赖 → lint → 类型检查 → 单元测试 → 构建

---

## Phase 3: UI 组件库建设（P1，1-2周）

### 3.1 Headless UI 组件库选择

**选型决策**：选择 **Radix UI**（而非 Headless UI / Ariakit / 自研）

**选型对比**：
| 方案 | 组件数量 | 可访问性 | 样式自由度 | 维护 | TypeScript |
|------|---------|---------|-----------|------|-----------|
| Radix UI Primitives | 多（40+） | ✅ 一流（WAI-ARIA 完整实现） | ✅ 完全无样式，自己用Tailwind写 | 活跃（WorkOS） | ✅ 完美 |
| Headless UI (Tailwind Labs) | 少（10+） | ✅ 好 | ✅ 无样式 | 活跃 | ✅ 好 |
| Ant Design | 极多 | ✅ 好 | ❌ 有自己的样式，定制难 | 活跃 | ✅ 好 |
| 自研 | 按需 | ❌ 难以做对 | ✅ | 维护成本高 | - |

**决策理由**：
- CampusShare 使用 Tailwind CSS，不需要 Ant Design/MUI 这种带样式的组件库（风格不一致，定制难）
- Radix UI 是无样式（headless）组件，只提供逻辑和可访问性，样式完全用 Tailwind 写
- 可访问性（a11y）做的非常专业，键盘导航、ARIA属性、屏幕阅读器都处理好了
- 基于 Radix 封装我们自己的设计系统组件

**需要封装的基础组件（基于 Radix UI）**：
| 组件 | 基于 Radix | 说明 |
|------|-----------|------|
| Button | - | 变体：primary/secondary/ghost/danger，支持loading |
| Input / Textarea | - | 表单输入，支持错误状态 |
| Modal/Dialog | @radix-ui/react-dialog | 弹窗，支持拖拽、动画 |
| Drawer | @radix-ui/react-dialog | 抽屉（从侧边滑出） |
| Popover | @radix-ui/react-popover | 气泡弹出 |
| Tooltip | @radix-ui/react-tooltip | 文字提示 |
| Dropdown Menu | @radix-ui/react-dropdown-menu | 下拉菜单 |
| Select | @radix-ui/react-select | 选择器 |
| Tabs | @radix-ui/react-tabs | 标签页（通知页面已手写，可替换） |
| Toast | -（现有） | 已实现，保留 |
| Skeleton | - | 骨架屏 |
| InfiniteScroll | - | 无限滚动（基于 IntersectionObserver） |
| ConfirmDialog | Dialog封装 | 确认弹窗（替代confirm） |
| Avatar | - | 头像组件（支持圆形、在线状态、fallback） |
| Badge | - | 徽标（未读数、V认证） |
| Spinner | - | Loading 指示器 |

**实施步骤**：
1. 安装 Radix UI 依赖（按需引入，不是全安装）
2. 按上面列表逐个封装组件，放在 `src/components/ui/` 目录下
3. 所有组件用 Tailwind CSS 写样式，保持设计风格统一（蓝色主题 #2563EB）
4. 现有页面逐步替换为新组件（不要一次性全改，改一个页面验证一个）

---

### 3.2 表单处理：react-hook-form + Zod

**选型决策**：**react-hook-form** + **zod**（而非 Formik）

**理由**：
- react-hook-form 性能最好（非受控组件，减少重渲染）
- 体积小
- 与 Zod 集成完美（@hookform/resolvers/zod）做表单校验
- TypeScript 支持一流

**实施**：
1. 安装 `react-hook-form @hookform/resolvers zod`
2. 封装 Form、FormField、FormItem 等组件（基于 Radix Form 或自封装）
3. 改造登录/注册/发帖/个人资料编辑等表单

---

## Phase 4: 性能优化（P1，持续）

### 4.1 路由级代码分割

**实施**：
1. 修改 [router/index.tsx](file:///E:/workspace_work/CampusShare/frontend/src/router/index.tsx)，用 `React.lazy` + `Suspense` 懒加载页面
2. 大组件（头像裁剪、富文本编辑器、Markdown渲染器）单独懒加载
3. 添加 Suspense fallback（Skeleton 或 Loading）
4. 预加载策略：鼠标悬停链接时预加载对应页面chunk（可选）

---

### 4.2 虚拟滚动（长列表）

**选型决策**：**@tanstack/virtual**（TanStack Virtual，而非 react-window/react-virtualized）

**理由**：
- TanStack 出品，与 TanStack Query 同作者，配合好
- 支持可变高度、水平/垂直、网格、动态尺寸
- TypeScript 支持好
- 比 react-window 更现代，维护活跃

**实施**：
1. 帖子评论列表（PostDetailPage）使用虚拟滚动
2. 私信聊天记录（MessagePage）使用虚拟滚动（滚到底部功能）
3. 通知列表（NotificationPage）数据量大时使用
4. 关注/粉丝列表使用虚拟滚动

---

### 4.3 图片优化

**实施**：
1. 封装 `Image` 组件，基于原生 `img` 但增强：
   - 懒加载（`loading="lazy"` + IntersectionObserver 提前加载）
   - 占位符（blur 或 骨架）
   - 错误兜底（加载失败显示默认图）
   - 响应式图片（srcset）
2. 后端支持图片缩略图（后端升级对象存储后，通过参数获取不同尺寸）
3. 头像 dicebear 国内访问慢的问题：后端做一层代理或搭建本地头像服务
4. 图片使用 WebP 格式（后端处理）

---

### 4.4 重渲染优化

**实施**：
1. 使用 React DevTools Profiler 定位不必要的重渲染
2. 合理拆分 Context，避免大范围重渲染
3. 列表项用 `React.memo` 包裹
4. 传递给子组件的函数用 `useCallback`，对象/数组用 `useMemo`
5. 使用 Zustand 的 selector 只订阅需要的状态，避免全量订阅

---

## Phase 5: 实时通信与体验升级（P1，配合后端）

### 5.1 WebSocket 客户端

**实施（等后端 WebSocket 就绪后）**：
1. 封装 `useWebSocket` hook
2. 连接管理：登录后连接、断线重连、心跳保活
3. 消息处理：
   - 收到新消息 → 更新 TanStack Query 缓存（或invalidateQueries）→ UI自动更新
   - 收到通知 → 更新未读数 → Toast 提示
4. 连接状态展示（Navbar 上网络状态图标）
5. 降级：WebSocket 连接失败自动降级为轮询（TanStack Query refetchInterval）

---

### 5.2 加载体验优化

**实施**：
1. 全局 Suspense + Skeleton 骨架屏
2. 按钮点击 loading 状态防止重复提交
3. 路由切换进度条（nprogress 类似效果）
4. 乐观更新（TanStack Query mutation onMutate 已支持）
5. 离线检测：断网时提示，网络恢复自动刷新数据

---

### 5.3 PWA 支持

**实施**：
1. 安装 `vite-plugin-pwa`
2. 配置 manifest.json（应用名、图标、主题色）
3. 配置 Service Worker 缓存策略（静态资源缓存、API 缓存策略）
4. 添加到主屏幕提示
5. 离线页面（离线时展示缓存内容或友好提示）

---

## Phase 6: 进阶特性（P2，远期）

1. **暗色模式**：Tailwind darkMode: 'class' + useTheme 完善
2. **国际化 i18n**：react-i18next，文案统一管理（中文/英文）
3. **富文本编辑器**：引入 Tiptap（基于 ProseMirror，现代、可扩展、React 友好），支持 Markdown
4. **Markdown 渲染**：react-markdown + remark-gfm 支持帖子 Markdown
5. **动画**：Framer Motion 添加页面转场动画、微交互
6. **移动端优化**：PWA、触摸手势、下拉刷新（已有部分，完善）
7. **SEO 优化**：虽然是SPA，但要考虑社交分享meta标签、React Helmet Async
8. **Bundle 分析**：rollup-plugin-visualizer 分析包大小，优化大依赖（按需引入 lodash-es、moment 换 dayjs 等）

---

## 升级顺序建议（Agent 执行时按此顺序）

1. **第一步**：TanStack Query 引入 + 核心 hooks 封装 + 页面改造 → 这是最大的架构变化，先把数据层理顺
2. **第二步**：Axios 替换 fetch 封装 → HTTP 层升级，上层 API 调用保持不变
3. **第三步**：Vitest + React Testing Library 测试框架搭建 → 后续重构有保障
4. **第四步**：ESLint/Prettier/Husky 代码规范 → 保证代码质量
5. **第五步**：Error Boundary + Sentry 错误监控 → 稳定性
6. **第六步**：Radix UI 基础组件库封装 → 开发效率和一致性
7. **第七步**：react-hook-form + Zod 表单处理 → 替换手写表单
8. **第八步**：路由代码分割 + 性能优化
9. **第九步**：TypeScript 严格模式 + 类型补全
10. **第十步**：WebSocket 客户端（等后端就绪）
11. **第十一步**：虚拟滚动 + 图片优化
12. **第十二步**：PWA + 其他进阶特性

> **注意**：
> 1. 每一步升级后必须保证所有现有功能正常运行（无回归bug）
> 2. 升级过程记录到 optimization-logs/ 目录
> 3. AGENT-WORKFLOW.md 中前端相关的命令、路径、规范需要同步更新
> 4. 不要一次性做大改动，小步提交，每改完一个模块验证一个
