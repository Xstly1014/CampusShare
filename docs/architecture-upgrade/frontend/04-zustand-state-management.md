# 前端架构升级 04：Zustand 状态管理规范化

> **文档版本**: v1.0
> **完成日期**: 2026-07-03
> **对应阶段**: Phase 1.3 - Zustand 状态管理规范化

---

## 一、重构背景

### 1.1 原有问题

| 问题 | 影响 |
|------|------|
| Zustand 仅有一个 toastStore，其他客户端状态散落在组件 useState 中 | 全局UI状态无法跨组件共享 |
| 主题切换有 useTheme hook 但从未被任何组件引用（死代码） | 暗色模式功能实际不可用 |
| 主题持久化用裸 localStorage 操作 | 无统一持久化方案 |
| toastStore 的 setTimeout 存在内存泄漏 | 快速卸载/切换时定时器未清理 |
| Toast 无数量上限 | 连续触发时可能堆积大量通知 |
| 无统一的store导出入口 | import路径不一致 |

### 1.2 客户端状态 vs 服务端状态 边界划分

完成 TanStack Query 引入后，已明确划分：
- **服务端状态**（来自后端API）：TanStack Query 管理（缓存/重试/失效）
- **客户端状态**（UI状态/主题/弹窗/通知）：Zustand 管理（持久化/跨组件共享）

---

## 二、实施内容

### 2.1 Store 模块化拆分

将客户端状态按领域拆分为独立store，放在 `src/stores/` 目录下：

| Store | 职责 | 持久化 |
|-------|------|--------|
| `themeStore` | 主题（light/dark） | ✅ localStorage |
| `uiStore` | 全局UI状态（侧边栏/弹窗/全局loading/页面标题） | ❌ 会话级 |
| `toastStore` | Toast通知（优化原实现） | ❌ 临时状态 |

### 2.2 themeStore（新增）

使用 Zustand + `persist` 中间件替代原来未使用的 `useTheme` hook：

- **持久化**：`persist` 中间件自动同步到 localStorage（key: `campusshare-theme`）
- **立即生效**：`setTheme`/`toggleTheme` 调用时立即同步到 `document.documentElement` classList
- **SSR安全**：`getSystemTheme()`/`applyTheme()` 检查 `typeof window/document`
- **hydration回调**：`onRehydrateStorage` 在rehydrate后自动应用主题class
- **initTheme()**：在App模块顶层调用，确保首帧就应用正确主题（避免闪烁）
- **colorScheme**：同时设置 `document.documentElement.style.colorScheme`，让原生滚动条/表单等也跟随主题

### 2.3 uiStore（新增）

统一管理全局UI状态，为后续功能提供基础：

- `sidebarOpen` / `toggleSidebar()` - 移动端侧边栏开关
- `globalLoading` / `setGlobalLoading()` - 全局loading遮罩
- `modal` / `openModal()` / `closeModal()` - 全局弹窗（发帖/图片预览/确认框），支持传入data
- `pageTitle` / `setPageTitle()` - 页面标题（用于导航栏显示）

### 2.4 toastStore（优化）

在原有实现基础上修复以下问题：

1. **修复 setTimeout 内存泄漏**：将timer id存储在 `timers: Map<string, Timeout>` 中，removeToast时clearTimeout
2. **添加MAX_TOASTS=5限制**：超过5条时自动移除最旧的toast并清除其定时器
3. **新增clearAll()方法**：清除所有toast和定时器
4. **新增toast.dismiss(id)和toast.dismissAll()** 静态方法

### 2.5 Barrel Export

创建 `src/stores/index.ts` 统一导出所有store，import路径统一为 `@/stores` 或 `../stores`。

### 2.6 文件变更清单

新建：
- [src/stores/themeStore.ts](file:///E:/workspace_work/CampusShare/frontend/src/stores/themeStore.ts) - 主题store（Zustand + persist）
- [src/stores/uiStore.ts](file:///E:/workspace_work/CampusShare/frontend/src/stores/uiStore.ts) - 全局UI状态store
- [src/stores/index.ts](file:///E:/workspace_work/CampusShare/frontend/src/stores/index.ts) - 统一导出
- `docs/architecture-upgrade/frontend/04-zustand-state-management.md`

修改：
- [src/stores/toastStore.ts](file:///E:/workspace_work/CampusShare/frontend/src/stores/toastStore.ts) - 修复内存泄漏、加max limit
- [src/App.tsx](file:///E:/workspace_work/CampusShare/frontend/src/App.tsx) - 调用initTheme()初始化主题

删除：
- `src/hooks/useTheme.ts` - 死代码，功能已被themeStore替代

---

## 三、使用指南

### 组件中使用主题

```tsx
import { useThemeStore } from '@/stores'

function ThemeToggle() {
  const { theme, toggleTheme } = useThemeStore()
  return (
    <button onClick={toggleTheme}>
      {theme === 'dark' ? '🌙' : '☀️'}
    </button>
  )
}
```

### 组件外控制Toast（不变）

```ts
import { toast } from '@/stores'

toast.success('操作成功')
toast.error('操作失败')
```

### 使用UI store

```tsx
import { useUIStore } from '@/stores'

function Sidebar() {
  const sidebarOpen = useUIStore(s => s.sidebarOpen)
  const toggleSidebar = useUIStore(s => s.toggleSidebar)
  // ...
}
```

---

## 四、验证结果

- ✅ TypeScript 类型检查通过
- ✅ 17个单元测试全部通过（675ms）
- ✅ 生产构建成功（1966 modules，2.73s）
- ✅ 主题初始化在App模块加载时执行，首帧无闪烁
- ✅ 向后兼容：toast静态API保持不变
