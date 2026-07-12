# 前端重构文档 #1：引入 TanStack Query (React Query) 进行服务端状态管理

## 一、重构背景

### 1.1 当前状态

CampusShare 前端项目基于 React 18 + TypeScript + Vite 技术栈，使用 Zustand 进行客户端状态管理。当前项目中所有的数据获取和缓存逻辑均由开发者手动实现，存在以下问题：

### 1.2 存在的问题

**问题1：重复的状态管理代码**
- 每个页面组件都需要手动维护 `loading`、`error`、`data` 三个状态
- 大量重复的 `useEffect` + `useState` 模式代码
- 每个页面都要写 `try-catch` 错误处理逻辑

**问题2：缺乏智能缓存机制**
- 页面切换后返回，数据会重新加载，用户体验差
- 相同数据在不同组件中被重复请求，浪费网络资源
- 没有自动 stale-while-revalidate 策略

**问题3：请求竞态条件问题**
- 快速切换筛选条件或分页时，旧请求可能覆盖新请求的结果
- 缺乏请求去重机制

**问题4：乐观更新缺失**
- 点赞/收藏等交互需要等待接口返回后才更新UI，用户感知延迟
- 失败回滚逻辑需要手动实现，容易出错

**问题5：缺乏统一的错误重试机制**
- 网络波动导致请求失败时没有自动重试
- 401/403等认证错误需要在每个请求中单独处理

**问题6：内存泄漏风险**
- 组件卸载时未取消的请求可能导致状态更新警告
- 缺乏自动的垃圾回收机制

### 1.3 代码现状示例

```tsx
// 当前典型的数据获取模式（重复代码遍布所有页面）
const [data, setData] = useState(null)
const [loading, setLoading] = useState(true)
const [error, setError] = useState(null)

useEffect(() => {
  let mounted = true
  setLoading(true)
  const fetchData = async () => {
    try {
      const res = await postApi.getList(params)
      if (mounted) {
        setData(res.data)
        setError(null)
      }
    } catch (err) {
      if (mounted) setError(err)
    } finally {
      if (mounted) setLoading(false)
    }
  }
  fetchData()
  return () => { mounted = false }
}, [params])
```

## 二、技术选型理由

### 2.1 备选方案对比

| 方案 | 优点 | 缺点 | 适用性 |
|------|------|------|--------|
| **TanStack Query (React Query)** | 成熟稳定、功能全面、TypeScript支持好、社区活跃、文档完善 | 学习曲线稍陡 | ⭐⭐⭐⭐⭐ 首选 |
| SWR | 轻量、API简洁 | 功能相对较少、DevTools较弱 | ⭐⭐⭐ |
| RTK Query | 与Redux深度集成 | 依赖Redux、样板代码多 | ⭐⭐（项目用Zustand） |
| 继续手动实现 | 无依赖 | 维护成本高、易出bug | ⭐ |
| Axios拦截器+自定义hook | 灵活度高 | 需要重复造轮子 | ⭐⭐ |

### 2.2 选择 TanStack Query 的核心理由

1. **服务端状态与客户端状态分离**：Zustand 继续管理客户端状态（UI状态、全局主题等），TanStack Query 专门管理服务端状态，职责清晰

2. **开箱即用的核心功能**：
   - 自动缓存 + 后台刷新
   - 请求去重（相同参数的多个组件订阅同一请求）
   - 自动重试（可配置）
   - 乐观更新（Optimistic Updates）
   - 分页/无限滚动支持
   - 离线支持
   - 垃圾回收（GC）

3. **卓越的开发体验**：
   - 官方 DevTools 可视化查看缓存状态
   - 完善的 TypeScript 类型推导
   - 与 Suspense 完美集成

4. **渐进式迁移支持**：可以与现有代码共存，逐个页面迁移，不破坏现有功能

5. **行业标准地位**：npm周下载量超过2500万，被React官方推荐，长期维护有保障

## 三、本次重构完成内容

### 3.1 依赖安装

```bash
npm install @tanstack/react-query @tanstack/react-query-devtools
```

新增依赖版本：
- `@tanstack/react-query`: ^5.x
- `@tanstack/react-query-devtools`: ^5.x

### 3.2 全局配置

**新增文件**：[queryClient.ts](file:///E:/workspace_work/CampusShare/frontend/src/lib/queryClient.ts)

配置内容：
```typescript
{
  queries: {
    staleTime: 5 * 60 * 1000,        // 数据5分钟内视为新鲜
    gcTime: 30 * 60 * 1000,         // 未使用缓存30分钟后清理
    retry: 智能重试策略,             // 401/403不重试，其他错误重试1次
    refetchOnWindowFocus: 生产环境启用,
    refetchOnReconnect: true,       // 网络恢复自动刷新
  },
  mutations: {
    retry: false,                   // 修改操作不自动重试
  }
}
```

### 3.3 Provider 注入

**修改文件**：[main.tsx](file:///E:/workspace_work/CampusShare/frontend/src/main.tsx)

- 在应用根组件外层包裹 `QueryClientProvider`
- 集成 `ReactQueryDevtools`（默认折叠，不影响生产环境）

### 3.4 Hooks 目录结构搭建

新建 `src/hooks/queries/` 目录，按业务模块组织 query hooks：

```
src/hooks/
└── queries/
    ├── index.ts              # 统一导出
    ├── useCategories.ts      # 分类相关query hooks
    ├── usePosts.ts           # 帖子相关query hooks
    └── useUsers.ts           # 用户相关query hooks
```

### 3.5 已封装的核心 Hooks

#### 分类模块 [useCategories.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/queries/useCategories.ts)

| Hook | 功能 | 缓存策略 |
|------|------|----------|
| `useCategories()` | 获取全部分类列表 | staleTime: 10分钟 |
| `useCategoryDetail(id)` | 获取单个分类详情 | 按ID缓存 |
| `useCategoryCounts()` | 获取分类帖子数量 | staleTime: 5分钟 |
| `useInvalidateCategories()` | 失效分类缓存 | - |

#### 帖子模块 [usePosts.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/queries/usePosts.ts)

| Hook | 功能 | 缓存策略 |
|------|------|----------|
| `usePostDetail(id)` | 获取帖子详情 | 按ID缓存 |
| `usePostStatus(id)` | 获取点赞/收藏状态 | 按ID缓存 |
| `usePostComments(id)` | 获取帖子评论列表 | 按帖子ID缓存 |
| `useMyPostStats()` | 获取我的帖子统计 | staleTime: 2分钟 |
| `useWarehouseStats()` | 获取仓库统计数据 | staleTime: 5分钟 |
| `useSchoolPostCounts()` | 获取各学校帖子数 | staleTime: 5分钟 |
| `useInvalidatePosts()` | 精细化失效缓存 | 支持按帖子/列表/我的帖子失效 |

#### 用户模块 [useUsers.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/queries/useUsers.ts)

| Hook | 功能 | 缓存策略 |
|------|------|----------|
| `useCurrentUser()` | 获取当前登录用户 | staleTime: 10分钟，401不重试 |
| `useUserProfile(userId)` | 获取用户公开资料 | 按用户ID缓存 |
| `useFollowStats()` | 获取关注统计 | staleTime: 2分钟 |
| `useFollowingList()` | 获取关注列表 | 默认配置 |
| `useFollowerList()` | 获取粉丝列表 | 默认配置 |
| `useMutualList()` | 获取互关列表 | 默认配置 |
| `useInvalidateUsers()` | 精细化失效缓存 | 支持按当前用户/资料/关注数据失效 |

### 3.6 Query Key 规范化设计

采用分层级的 Query Key 设计，支持精细化缓存失效：

```typescript
// 示例：posts模块的key结构
POSTS_KEYS = {
  all: ['posts'],
  lists: () => ['posts', 'list'],
  detail: (id) => ['posts', 'detail', id],
  comments: (id) => ['posts', 'detail', id, 'comments'],
  myPosts: () => ['posts', 'my'],
  // ...
}
```

这种设计的优势：
- 可以精确失效某一条帖子：`invalidateQueries({ queryKey: POSTS_KEYS.detail(postId) })`
- 可以批量失效所有帖子列表：`invalidateQueries({ queryKey: POSTS_KEYS.lists() })`
- 可以失效所有帖子相关缓存：`invalidateQueries({ queryKey: POSTS_KEYS.all })`

## 四、达成效果

### 4.1 完整落地情况

✅ TanStack Query 核心库已安装并配置完成
✅ DevTools 已集成，开发环境可可视化调试缓存状态
✅ 全局 QueryClient 已配置合理的默认策略（缓存时间、重试、窗口聚焦刷新等）
✅ 核心业务模块的 **query hooks** 已封装完成（分类/帖子/用户）
✅ 核心业务模块的 **mutation hooks** 已封装完成（点赞/收藏/评论/设置更新等），含乐观更新
✅ 核心页面已完成迁移并实际使用 TanStack Query
✅ TypeScript 类型全部正确，类型检查通过
✅ 生产构建成功（1910 modules transformed，构建时间2.65s）

### 4.2 已迁移页面清单

| 页面 | 文件 | 迁移内容 |
|------|------|----------|
| 首页 | [HomePage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/HomePage.tsx) | 分类列表数据改用 `useCategories()` |
| 帖子详情页 | [PostDetailPage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/PostDetailPage.tsx) | 帖子详情、点赞/收藏状态、评论列表、评论增删点赞 |
| 设置页 | [SettingsPage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/SettingsPage.tsx) | 当前用户信息、隐私设置、通知设置 |

### 4.3 已封装的 Mutation Hooks

**帖子交互模块** [usePostMutations.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/mutations/usePostMutations.ts)：
- `useTogglePostLike()` - 点赞/取消点赞（带乐观更新 + 失败自动回滚）
- `useTogglePostStar()` - 收藏/取消收藏（带乐观更新 + 失败自动回滚）
- `useDeletePost()` - 删除帖子（自动失效列表缓存）
- `useRecordDownload()` - 记录下载（自动失效统计缓存）

**评论模块** [useCommentMutations.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/mutations/useCommentMutations.ts)：
- `useCreateComment()` - 发表评论（自动刷新评论列表）
- `useDeleteComment()` - 删除评论（自动刷新评论列表）
- `useToggleCommentLike()` - 评论点赞（自动刷新评论列表）

**用户模块** [useUserMutations.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/mutations/useUserMutations.ts)：
- `useUpdateProfile()` - 更新个人资料（自动失效当前用户缓存）
- `useUpdatePrivacy()` - 更新隐私设置（自动失效当前用户缓存）
- `useUpdateNotificationSettings()` - 更新通知设置（自动失效当前用户缓存）
- `useToggleFollow()` - 关注/取关（自动失效关注相关缓存）

### 4.4 实际代码改进效果

**代码量减少**：
- 首页：移除1个useState + 1个useEffect（约10行手动数据获取代码）→ 改用1行hook调用
- 帖子详情页：移除8个useState + 2个useEffect + 手动乐观更新逻辑（约80行代码）→ 改用声明式hooks
- 设置页：移除6个useState + 3个useEffect（约70行重复代码）→ 改用声明式hooks + mutations
- **总计减少约160行手动状态管理样板代码**

**功能提升**：
- ✨ 点赞/收藏按钮现在使用 **乐观更新**，点击瞬间UI立即响应，无需等待接口返回
- ✨ 相同数据自动缓存，返回上一页时数据秒开，后台静默刷新
- ✨ 请求失败自动重试（401/403等认证错误除外）
- ✨ 网络恢复后自动刷新数据
- ✨ 组件卸载自动取消请求，消除内存泄漏警告
- ✨ 生产环境窗口重新获得焦点时静默刷新数据
- ✨ DevTools支持：开发时可在页面右下角看到React Query图标，点击可查看缓存状态

### 4.5 代码量对比

**迁移前（手动管理）：**
```tsx
const [categories, setCategories] = useState<Category[]>([])
const [loading, setLoading] = useState(true)

useEffect(() => {
  let mounted = true
  const fetch = async () => {
    try {
      const res = await categoryApi.getAll()
      if (mounted) setCategories(res.data || [])
    } finally {
      if (mounted) setLoading(false)
    }
  }
  fetch()
  return () => { mounted = false }
}, [])
```

**迁移后（TanStack Query）：**
```tsx
const { data: categories = [], isLoading } = useCategories()
```

**点赞功能迁移对比：**

迁移前（手动乐观更新，约25行代码）：
```tsx
const handleLike = async () => {
  if (toggling) return
  setToggling(true)
  const prevLiked = isLiked
  setIsLiked(!prevLiked)
  setLikeCount(prevLiked ? likeCount - 1 : likeCount + 1)
  try {
    const res = await postApi.toggleLike(postId)
    setIsLiked(res.data)
    setLikeCount(res.data ? likeCount + 1 : likeCount - 1)
  } catch {
    setIsLiked(prevLiked)
    setLikeCount(prevLiked ? likeCount + 1 : likeCount - 1)
    toast.error('操作失败')
  } finally {
    setToggling(false)
  }
}
```

迁移后（使用封装好的hook，1行调用）：
```tsx
const handleLike = () => toggleLike.mutate(postId)
// 乐观更新、失败回滚、缓存失效全部在hook中统一处理
```

## 五、使用指南（后续开发）

### 5.1 基本查询示例

```tsx
import { useCategories, usePostDetail } from '@/hooks/queries'

function CategoryList() {
  const { data: categories = [], isLoading, error } = useCategories()
  
  if (isLoading) return <Spin />
  if (error) return <Alert message="加载失败" />
  
  return <div>{categories.map(c => <CategoryCard key={c.id} {...c} />)}</div>
}
```

### 5.2 修改数据 + 缓存失效

```tsx
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { postApi, POSTS_KEYS } from '@/hooks/queries'

function CreatePostButton() {
  const queryClient = useQueryClient()
  
  const createPost = useMutation({
    mutationFn: postApi.create,
    onSuccess: () => {
      // 创建成功后，失效列表缓存，下次访问自动刷新
      queryClient.invalidateQueries({ queryKey: POSTS_KEYS.lists() })
    },
  })
  
  return <Button onClick={() => createPost.mutate(data)}>发布</Button>
}
```

### 5.3 乐观更新示例

```tsx
const toggleLike = useMutation({
  mutationFn: () => postApi.toggleLike(postId),
  // 立即更新UI
  onMutate: async () => {
    await queryClient.cancelQueries({ queryKey: POSTS_KEYS.status(postId) })
    const previous = queryClient.getQueryData(POSTS_KEYS.status(postId))
    queryClient.setQueryData(POSTS_KEYS.status(postId), old => ({
      ...old,
      liked: !old.liked,
    }))
    return { previous }
  },
  // 失败回滚
  onError: (err, _, context) => {
    queryClient.setQueryData(POSTS_KEYS.status(postId), context.previous)
  },
  // 最终从服务器确认
  onSettled: () => {
    queryClient.invalidateQueries({ queryKey: POSTS_KEYS.status(postId) })
  },
})
```

## 六、后续可扩展方向

核心功能已落地，剩余页面可在后续开发中按需逐步迁移，不影响现有功能：

1. **分页列表页**：使用 `useInfiniteQuery` 实现无限滚动
2. **路由预取**：在用户hover链接时 `prefetchQuery` 实现页面秒开
3. **发帖/编辑页**：使用mutation的onSuccess进行乐观列表更新
4. **个人中心/仓库页**：迁移个人帖子列表、下载记录等
5. **学校详情页/分类详情页**：迁移帖子列表分页数据
6. **通知/消息页**：利用轮询或聚焦刷新实现消息自动更新

## 七、变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| [package.json](file:///E:/workspace_work/CampusShare/frontend/package.json) | 修改 | 新增 @tanstack/react-query、@tanstack/react-query-devtools 依赖 |
| [main.tsx](file:///E:/workspace_work/CampusShare/frontend/src/main.tsx) | 修改 | 添加QueryClientProvider和ReactQueryDevtools |
| [queryClient.ts](file:///E:/workspace_work/CampusShare/frontend/src/lib/queryClient.ts) | 新增 | QueryClient全局配置（缓存策略、重试、刷新等） |
| [useCategories.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/queries/useCategories.ts) | 新增 | 分类相关query hooks |
| [usePosts.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/queries/usePosts.ts) | 新增 | 帖子相关query hooks |
| [useUsers.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/queries/useUsers.ts) | 新增 | 用户相关query hooks |
| [queries/index.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/queries/index.ts) | 新增 | query hooks统一导出 |
| [usePostMutations.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/mutations/usePostMutations.ts) | 新增 | 帖子交互mutation hooks（点赞/收藏/删除/下载，含乐观更新） |
| [useCommentMutations.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/mutations/useCommentMutations.ts) | 新增 | 评论mutation hooks（发表/删除/点赞） |
| [useUserMutations.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/mutations/useUserMutations.ts) | 新增 | 用户mutation hooks（资料更新/隐私/通知/关注） |
| [mutations/index.ts](file:///E:/workspace_work/CampusShare/frontend/src/hooks/mutations/index.ts) | 新增 | mutation hooks统一导出 |
| [HomePage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/HomePage.tsx) | 修改 | 迁移分类列表使用useCategories() |
| [PostDetailPage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/PostDetailPage.tsx) | 修改 | 完整迁移帖子详情/点赞收藏/评论到TanStack Query |
| [SettingsPage.tsx](file:///E:/workspace_work/CampusShare/frontend/src/pages/SettingsPage.tsx) | 修改 | 迁移用户信息/隐私/通知设置到TanStack Query |

---

**重构完成时间**：2026-07-02
**重构任务编号**：FRONTEND-01
**技术选型**：TanStack Query v5
**影响范围**：基础设施层 + 3个核心页面（首页、帖子详情、设置页）
**代码减少**：约160行手动状态管理样板代码
**验证结果**：TypeScript类型检查通过 ✅，生产构建成功 ✅（1910 modules，2.65s）
