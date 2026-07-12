# 移动端 UX 考量

> 状态: 草稿
> 最后更新: 2026-06-30

## 一、目标

校园场景以手机为主，Agent 助手必须在移动端有良好体验：键盘弹起、滚动、手势、小屏适配、网络波动。

## 二、键盘弹起处理

### 2.1 问题

移动端输入框聚焦时软键盘弹起，会：
- 挤压可视区域（iOS Safari 尤其严重）。
- 输入区被键盘遮挡。
- 消息流被推到屏幕外。

### 2.2 方案

- 使用 `100dvh`（dynamic viewport height）替代 `100vh`，自动适配键盘弹起。
- 输入框 `position: sticky; bottom: 0`，始终贴底。
- 键盘弹起时自动滚动消息流到底部（`scrollIntoView`）。
- 监听 `visualViewport.resize` 事件，动态调整布局。

```typescript
useEffect(() => {
  const onResize = () => {
    document.documentElement.style.setProperty('--vh', `${window.visualViewport.height * 0.01}px`)
  }
  window.visualViewport?.addEventListener('resize', onResize)
  return () => window.visualViewport?.removeEventListener('resize', onResize)
}, [])
```

CSS：
```css
.assistant-page { height: calc(var(--vh, 1vh) * 100); }
```

### 2.3 输入框失焦

- 用户发送消息后，输入框保持聚焦（便于连续提问）。
- 点击消息流区域时失焦，收起键盘。
- 不主动 blur（避免 iOS 键盘闪烁）。

## 三、滚动体验

### 3.1 滚动惯性

- 消息流用 `-webkit-overflow-scrolling: touch`（iOS 惯性滚动）。
- `overscroll-behavior: contain` 防止滚动到顶/底时触发页面整体滚动。

### 3.2 滚动到底部按钮

- 用户上滚查看历史时，右下角显示"↓ 回到最新"浮动按钮。
- 点击平滑滚动到底部，按钮消失。
- 按钮位置：`absolute bottom-20 right-4`（避开输入区）。

### 3.3 流式滚动

- token 流到达时，若用户在底部则自动滚动；若用户上滚则不强制滚动（见 ADR-128）。
- 滚动用 `scrollTo({ behavior: 'smooth' })`，避免突兀跳动。

## 四、手势支持

### 4.1 下拉刷新历史

- 在消息流顶部下拉，触发刷新（重新拉取当前会话最近消息）。
- 用 `touchstart/touchmove/touchend` 实现，下拉 >80px 触发。

### 4.2 左滑删除（历史抽屉）

- HistoryDrawer 中，左滑历史会话项显示删除按钮。
- 用 `framer-motion` 的拖拽实现。

### 4.3 长按复制

- 长按助手回答 500ms，弹出菜单"复制全文"/"复制选中文本"。
- 不用浏览器原生 `oncontextmenu`（移动端体验差）。

## 五、小屏适配

### 5.1 断点

- 默认移动端优先（max-width: 768px）。
- 桌面端（min-width: 768px）：消息流最大宽度 640px 居中，左右留白。

```css
.message-list {
  @apply mx-auto w-full;
  max-width: 640px;
}
```

### 5.2 字体大小

- 用户消息：16px（避免 iOS Safari <16px 自动缩放）。
- 助手消息：15px（稍小，容纳更多内容）。
- 引用卡：12px。
- 工具状态：12px。

### 5.3 触摸目标

- 所有可点击元素最小 44×44px（iOS HIG 标准）。
- 引用角标 [n] 虽小，但点击区域扩展到周围 20px（`::after` 透明扩展）。

## 六、网络波动

### 6.1 弱网提示

- SSE 连接建立 >3s 时，显示"网络较慢，正在连接..."。
- token 间隔 >5s 时，显示"网络波动，正在努力生成..."。

### 6.2 离线检测

- `navigator.onLine` 监听网络状态。
- 离线时输入框禁用，显示"当前离线，请检查网络"。
- 恢复在线后自动重连当前会话。

### 6.3 SSE 中断恢复

- SSE 连接中途断开（非用户主动中断）：自动重连，续传 token（见 [sse-client-implementation.md](./sse-client-implementation.md) 第五节）。
- 重连失败 2 次：显示错误气泡 + "重试"按钮。

## 七、性能

### 7.1 首屏加载

- AssistantPage 懒加载（`React.lazy`），不进入主 bundle。
- 路由级代码分割：`const AssistantPage = lazy(() => import('./pages/AssistantPage'))`。

### 7.2 流式渲染性能

- token 流用 `requestAnimationFrame` 节流（见 [state-management.md](./state-management.md) ADR-122）。
- 长回答（>2000 字）时，每 500ms 检查 DOM 节点数，>500 节点时暂停渲染旧部分（折叠为"查看更多"）。

### 7.3 图片懒加载

- 引用卡中的头像/缩略图用 `loading="lazy"`。
- 历史抽屉中的会话项懒加载。

## 八、iOS Safari 特殊处理

### 8.1 100vh 问题

- iOS Safari 的 `100vh` 包含地址栏，导致底部被遮挡。
- 用 `100dvh` 或 `calc(var(--vh, 1vh) * 100)` 替代（见 2.2）。

### 8.2 输入框聚焦放大

- iOS 输入框 font-size <16px 时聚焦会自动放大页面。
- 强制 `font-size: 16px` 规避。

### 8.3 滚动橡皮筋

- `body { overscroll-behavior: none }` 禁用整体橡皮筋。
- 消息流区域 `overscroll-behavior: contain` 保留局部橡皮筋。

### 8.4 安全区域

- 底部输入区预留 `env(safe-area-inset-bottom)`（iPhone X+ 底部 Home 条）。
- 顶部 Header 预留 `env(safe-area-inset-top)`（刘海屏）。

```css
.assistant-input {
  padding-bottom: env(safe-area-inset-bottom);
}
```

## 九、Android Chrome 特殊处理

### 9.1 键盘弹起

- Android Chrome 键盘弹起会 resize viewport，`visualViewport` 监听有效。
- 部分机型键盘弹起时输入框不自动上移，需 `scrollIntoView` 强制。

### 9.2 返回键

- Android 物理返回键应触发 `navigate(-1)` 而非退出 App。
- 用 `history.pushState` + `popstate` 监听处理。

## 十、测试设备清单

MVP 阶段必须测试以下设备：

| 设备 | 屏幕 | 关注点 |
|------|------|-------|
| iPhone SE | 320×568 | 最小屏，5 tab 宽度验证 |
| iPhone 12 | 390×844 | 主流尺寸，刘海屏安全区 |
| iPhone 14 Pro | 393×852 | Dynamic Island |
| 小米 Redmi Note | 360×640 | Android 中低端 |
| 华为 P40 | 360×780 | Android 主流 |
| iPad Mini | 768×1024 | 平板（桌面布局断点） |

## 十一、决策记录 (ADR)

### ADR-131: 100dvh 替代 100vh
- **理由**：移动端 `100vh` 在键盘弹起时表现不一致（iOS 包含地址栏，Android 不含）。`100dvh` 自动适配，但需 fallback。
- **兼容**：iOS 15.4+ / Android Chrome 108+ 支持。旧版用 `visualViewport` 监听兜底。

### ADR-132: 输入框 font-size: 16px
- **理由**：iOS Safari 在 font-size <16px 时聚焦会放大页面，体验差。16px 是规避放大的最小值。
- **代价**：字体偏大，但移动端可接受。

### ADR-133: 懒加载 AssistantPage
- **理由**：Agent 相关依赖（react-markdown/remark-gfm）约 80KB gzip，不进入主 bundle 首屏更快。
- **代价**：首次进入助手页有 200-300ms 加载（显示骨架屏）。

### ADR-134: 流式渲染节点数监控
- **理由**：长回答（>2000 字）DOM 节点 >500 时滚动卡顿。监控 + 折叠旧内容保证流畅。
- **阈值**：500 节点（实测 iPhone SE 在 500 节点以下流畅）。

### ADR-135: 不实现 PWA 离线
- **理由**：Agent 强依赖后端 LLM，离线无意义。PWA 增加复杂度但收益低。
- **未来**：若做"历史会话离线查看"可再考虑。

### ADR-136: 物理返回键处理
- **理由**：Android 用户习惯物理返回键导航，若不处理会直接退出 App，体验差。
- **实现**：`history.pushState` + `popstate` 拦截，触发 `navigate(-1)`。
