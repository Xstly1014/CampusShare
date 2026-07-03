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

### 1.4 轮询规范化（TanStack Query refetchInterval）

**方案**：
- 将所有手动 `setInterval` 轮询替换为 TanStack Query 的 `refetchInterval` 配置
- `refetchIntervalInBackground: false` 确保页面不可见（后台Tab）时自动暂停轮询，节省资源
- WebSocket 升级后（Phase 5.1），改为消息驱动的 `queryClient.invalidateQueries()` 替代轮询

**已实施**：
- ✅ NavBar 未读通知数：30秒轮询 → `useUnreadNotificationCount()`，未登录时自动禁用
- ✅ 私信会话消息列表：5秒轮询 → `useConversationMessages()`，离开会话页面自动停止
- ✅ 私信发送权限状态：5秒轮询 → `useCanSendMessage()`，离开会话页面自动停止
- ✅ NotificationPage/NotificationBasketPage 迁移到 TanStack Query hooks

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

### 2.2 错误边界（Error Boundary） ✅ 已完成

**已实施**：
1. 创建 `components/common/ErrorBoundary.tsx`，支持三个级别：`root`/`page`/`component`
2. 根级别：App.tsx 包裹整个应用，崩溃时显示"刷新页面"按钮
3. 页面级别：router/index.tsx 中 `withPageBoundary()` 包裹每个页面路由，单页出错不影响其他页面
4. 组件级别：可手动包裹任意子组件，支持自定义 fallback
5. 开发环境显示详细错误堆栈（可展开），生产环境友好降级UI
6. 集成 Sentry/captureException 错误上报
7. 重试按钮可重置错误状态（root级别刷新页面，其他级别重新渲染）

---

### 2.3 前端监控和埋点 ✅ 基础设施已完成（DSN待配置）

**已实施**：
1. 安装 `@sentry/react`，创建 `src/lib/monitoring.ts` 封装层
2. `initMonitoring()` 在 main.tsx 中调用，仅在生产环境且配置了 `VITE_SENTRY_DSN` 时才初始化Sentry
3. 已集成 BrowserTracing（性能追踪，采样率10%）和 Session Replay（错误时100%采样，正常时10%采样）
4. ErrorBoundary 的 componentDidCatch 中调用 `captureException()` 上报错误到Sentry
5. 导出 `captureException()` 和 `captureMessage()` 工具函数，开发/未配置DSN时降级为 console.error/console.log
6. 添加 VITE_SENTRY_DSN 环境变量类型定义（vite-env.d.ts）

**待完成**：
- 获取 Sentry DSN 并配置到生产环境变量 `VITE_SENTRY_DSN`
- Web Vitals 采集（可后续添加）
- 用户行为埋点 `track()` 函数（可后续按需添加）

---

### 2.4 代码规范与格式化 ✅ 已完成

**已实施**：
1. **Prettier**：`.prettierrc` 配置（无分号、单引号、2空格缩进、100字符行宽、末尾逗号、LF换行）；`.prettierignore` 排除构建产物
2. **ESLint**：更新 `eslint.config.js`（ESLint 9 flat config）
   - 继承 recommended + stylistic + prettier 规则
   - react-hooks 推荐规则
   - react-refresh 组件导出规范
   - `@typescript-eslint/no-explicit-any`：warn（逐步消除）
   - `@typescript-eslint/no-unused-vars`：warn，忽略 `_` 前缀变量
   - `@typescript-eslint/consistent-type-imports`：warn（推荐type-only import）
3. **Husky**：`.husky/pre-commit` 在commit前自动运行 lint-staged
4. **lint-staged**：对git暂存的 .ts/.tsx 文件自动运行 `eslint --fix` + `prettier --write`，其他文件 prettier --write
5. **package.json scripts**：新增 `lint:fix`、`format`、`format:check` 命令

**注**：commitlint（commit-msg hook）暂未启用，需在 monorepo 根目录配置后生效。

---

### 2.5 TypeScript 类型强化 ✅ 严格模式已开启

**已实施**：
1. tsconfig.json 开启 `strict: true`、`noUnusedLocals: true`、`noUnusedParameters: true`、`noFallthroughCasesInSwitch: true`、`forceConsistentCasingInFileNames: true`、`noUncheckedIndexedAccess: true`、`noUncheckedSideEffectImports: true`
2. 修复所有严格模式下的类型错误（约50处）：
   - 未使用的变量/导入：删除或加 `_` 前缀
   - 可能为 undefined：添加可选链 `?.`、非空断言 `!`（确认安全的场景）、类型守卫
   - 类型不匹配：`boolean | ""` → boolean（用 `!!()`转换）、`string | null` → `string | undefined`、数组索引添加可选链
3. vite-env.d.ts 添加 ImportMetaEnv 类型定义
4. 项目目前 `npx tsc --noEmit` 零错误

**待完成**：
- Zod 运行时类型校验（可后续Phase3表单处理中结合react-hook-form引入）
- services/types.ts 类型完善（部分API响应类型可进一步细化）

---

### 2.6 CI/CD 配置 ✅ 已完成

**已实施**：
创建 `.github/workflows/frontend-ci.yml`：
- 触发条件：push/PR 到 master 且修改了 frontend/ 或 workflow 文件
- 运行环境：ubuntu-latest + Node.js 20
- 缓存：npm cache（基于 package-lock.json）
- 步骤：checkout → setup-node → npm ci → tsc类型检查 → eslint lint → vitest测试 → vite build
- 任一环节失败则CI红色，阻止合入


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

1. **第一步**：~~TanStack Query 引入 + 核心 hooks 封装 + 页面改造~~ ✅ 已完成
2. **第二步**：~~Axios 替换 fetch 封装~~ ✅ 已完成
3. **第三步**：~~Vitest + React Testing Library 测试框架搭建~~ ✅ 已完成
4. **第四步**：~~ESLint/Prettier/Husky 代码规范~~ ✅ 已完成
5. **第五步**：~~Error Boundary + Sentry 错误监控~~ ✅ 已完成（Sentry DSN待配置）
6. **第六步**：Radix UI 基础组件库封装 → 开发效率和一致性
7. **第七步**：react-hook-form + Zod 表单处理 → 替换手写表单
8. **第八步**：路由代码分割 + 性能优化
9. **第九步**：~~TypeScript 严格模式 + 类型补全~~ ✅ 已完成
10. **第十步**：WebSocket 客户端（等后端就绪）
11. **第十一步**：虚拟滚动 + 图片优化
12. **第十二步**：PWA + 其他进阶特性

> **Phase 1 + Phase 2 已全部完成**，下一个阶段是 Phase 3（UI组件库建设）。

> **注意**：
> 1. 每一步升级后必须保证所有现有功能正常运行（无回归bug）
> 2. 升级过程记录到 optimization-logs/ 目录
> 3. AGENT-WORKFLOW.md 中前端相关的命令、路径、规范需要同步更新
> 4. 不要一次性做大改动，小步提交，每改完一个模块验证一个
