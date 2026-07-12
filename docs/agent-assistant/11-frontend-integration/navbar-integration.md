# 底部 NavBar 插入助手入口

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

在现有 4 个底部导航（首页/仓库/通知/我的）中插入"助手"入口，位置在**首页和仓库之间**，使用 `Bot` 图标（lucide-react）。

## 二、当前 NavBar 状态

文件：[frontend/src/components/common/NavBar.tsx](../../../frontend/src/components/common/NavBar.tsx)

```ts
const navItems = [
  { path: '/home', icon: Home, label: '首页' },
  { path: '/warehouse', icon: Package, label: '仓库' },
  { path: '/notifications', icon: Bell, label: '通知', badge: unreadCount },
  { path: '/profile', icon: User, label: '我的' },
]
```

当前 4 tab，每 tab 占 25% 宽度。

## 三、改动方案

### 3.1 新增 navItems

```ts
import { Home, Bot, Package, Bell, User } from 'lucide-react'

const navItems = [
  { path: '/home', icon: Home, label: '首页' },
  { path: '/assistant', icon: Bot, label: '助手', badge: hasActiveSession ? '·' : undefined },
  { path: '/warehouse', icon: Package, label: '仓库' },
  { path: '/notifications', icon: Bell, label: '通知', badge: unreadCount },
  { path: '/profile', icon: User, label: '我的' },
]
```

- 5 tab，每 tab 占 20% 宽度。
- 助手 badge：若有 ACTIVE 会话显示小圆点 `·`（不是数字），暗示"有对话进行中"。
- 图标用 `Bot`（机器人），符合 AI 助手语义。

### 3.2 宽度适配

当前 `flex justify-around` 自动均分，5 tab 无需改 CSS。但需验证：
- 图标 + 文字在 20% 宽度下不换行（iPhone SE 320px 宽，每 tab 64px，够用）。
- active 下划线宽度 `w-12`（48px）在 64px tab 内不溢出。

### 3.3 路由新增

文件：[frontend/src/router/index.tsx](../../../frontend/src/router/index.tsx)

新增：
```tsx
import AssistantPage from '../pages/AssistantPage'

<Route
  path="/assistant"
  element={<PrivateRoute><AssistantPage /></PrivateRoute>}
/>
```

## 四、hasActiveSession 状态来源

- 通过 Zustand assistantStore 订阅当前是否有 ACTIVE 会话。
- 进入助手页面时调 `POST /api/agent/sessions` 创建/恢复会话。
- 离开页面时会话保持 ACTIVE（不主动关闭），由后端 30 分钟超时归档。
- NavBar 上的 `·` badge 在会话归档后自动消失。

## 五、视觉对比

### 5.1 改动前（4 tab）

```
[ 首页 ][ 仓库 ][ 通知 ][ 我的 ]
```

### 5.2 改动后（5 tab）

```
[ 首页 ][助手][ 仓库 ][ 通知 ][ 我的 ]
```

助手 tab 用蓝色高亮（active 时），与现有 active 样式一致。

## 六、与 ProfilePage 通知按钮的关系

- ProfilePage 原有的"通知与私信"按钮已在之前迭代中移除（见 project_memory 硬约束）。
- 助手入口只在 NavBar，ProfilePage 不重复放置。

## 七、可访问性

- 助手 tab 添加 `aria-label="AI 助手"`。
- 图标 + 文字双重标识，色盲用户可识别。
- 键盘 Tab 导航可达。

## 八、决策记录 (ADR)

### ADR-102: 助手入口在首页和仓库之间
- **理由**：用户使用频率排序：首页 > 助手 ≈ 仓库 > 通知 > 我的。助手放第二位便于拇指够到（右手用户拇指自然落在屏幕中下部偏左）。
- **替代**：放最右——离拇指太远；放最左——挤占首页。

### ADR-103: 用 Bot 图标而非 Sparkles/MessageCircle
- **理由**：Bot 明确表达"AI 助手"语义；Sparkles 太抽象；MessageCircle 易与私信混淆。
- **一致性**：lucide-react 内置 Bot 图标，无需额外依赖。

### ADR-104: 5 tab 而非折叠更多按钮
- **理由**：5 tab 在 320px 屏宽下仍可容纳（每 tab 64px），校园场景导航项固定，不需要折叠。
- **风险**：若未来加更多入口（如"动态"），需重新评估折叠方案。

### ADR-105: 助手 badge 用小圆点而非数字
- **理由**：助手没有"未读"概念，只有"有对话进行中"。小圆点比数字更轻量，不制造焦虑感。
- **消除**：会话归档后自动消失。
