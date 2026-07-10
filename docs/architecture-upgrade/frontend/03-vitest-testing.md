# 前端架构升级 03：测试体系搭建（Vitest + React Testing Library）

> **文档版本**: v1.0
> **完成日期**: 2026-07-02
> **对应阶段**: Phase 2.1 - 测试体系搭建

---

## 一、重构背景

### 1.1 原有问题

项目前端完全没有自动化测试，所有功能验证依赖手动测试：
- 工具函数（时间格式化、class合并等）修改后无法自动验证
- 核心hooks（如useTheme）行为无法保障
- 重构时缺少安全网，容易引入回归bug
- 没有持续集成基础

### 1.2 技术选型

| 候选方案 | 速度 | Vite集成 | React支持 | TypeScript | 生态 |
|----------|------|----------|-----------|------------|------|
| **Vitest** | ⚡ 极快（Vite原生，复用transform） | ✅ 一等公民 | ✅ | ✅ 原生 | 活跃，与Jest API兼容 |
| Jest | 🐢 较慢（需单独transform） | ❌ 需配置 | ✅ | ❌ 需额外配置 | 最成熟 |

**为什么选 Vitest + RTL**：
- Vitest 与 Vite 共享配置，零额外配置开箱即用
- 兼容 Jest API，团队上手成本低
- 支持 happy-dom/jsdom 两种DOM环境（选择happy-dom，ESM兼容性更好，启动更快）
- React Testing Library（RTL）是React组件测试的事实标准，鼓励"以用户方式测试"
- 内置覆盖率报告（v8 provider）

---

## 二、实施内容

### 2.1 基础设施

**依赖安装**：
- `vitest` v2.x - 测试运行器和断言库
- `@testing-library/react` v16 - React组件渲染和查询
- `@testing-library/jest-dom` v6 - 扩展DOM断言（toBeInTheDocument等）
- `@testing-library/user-event` v14 - 模拟用户交互
- `happy-dom` - 轻量DOM环境模拟（替代jsdom，解决ESM兼容性问题）

**配置文件**：
- [vite.config.ts](file:///E:/workspace_work/CampusShare/frontend/vite.config.ts) - 添加test配置（环境、setup文件、覆盖率）
- [src/test/setup.ts](file:///E:/workspace_work/CampusShare/frontend/src/test/setup.ts) - 测试启动时导入jest-dom匹配器
- `package.json` - 添加 `test`/`test:watch`/`test:coverage` 脚本

**npm scripts**：
```json
{
  "test": "vitest run",        // 单次运行所有测试（CI用）
  "test:watch": "vitest",       // 监听模式（开发用）
  "test:coverage": "vitest run --coverage"  // 带覆盖率报告
}
```

### 2.2 首批测试用例

#### 1. cn 工具函数测试（[utils.test.ts](file:///E:/workspace_work/CampusShare/frontend/src/lib/utils.test.ts)）
测试class合并工具的7个场景：
- 基本合并、条件类、undefined/null处理
- **tailwind-merge冲突类合并**（如px-2 + px-4 → px-4）
- 数组语法、对象语法、空输入

#### 2. formatTime 时间格式化测试（[time.test.ts](file:///E:/workspace_work/CampusShare/frontend/src/utils/time.test.ts)）
使用 `vi.useFakeTimers()` + `vi.setSystemTime()` 控制时间，覆盖全部10个分支：
- 空字符串输入、刚刚（0秒/时钟偏差/未来时间）
- X秒前、X分钟前、X小时前
- 昨天 HH:mm、本周星期X HH:mm
- 今年M月D日、往年YYYY年M月D日

### 2.3 测试约定

- 测试文件命名：`*.test.ts`/`*.test.tsx`，与被测文件同目录
- 不使用全局API，显式从`vitest`导入`describe/it/expect/vi`
- 工具函数/hooks测试放在同目录 `__tests__` 或并列 `.test.ts`
- 组件测试关注用户可见行为，不测试实现细节
- 覆盖率报告排除：测试文件自身、setup文件、类型声明、main.tsx入口

---

## 三、测试结果

```
 ✓ src/utils/time.test.ts (10 tests) 22ms
 ✓ src/lib/utils.test.ts (7 tests) 6ms

 Test Files  2 passed (2)
      Tests  17 passed (17)
   Duration  777ms
```

全部17个测试通过，运行时间不到1秒。

---

## 四、验证结果

- ✅ Vitest 配置完成，test/test:watch/test:coverage 脚本可用
- ✅ 17个单元测试全部通过
- ✅ TypeScript 类型检查通过
- ✅ 生产构建成功（1962 modules，2.77s）
- ✅ 不影响现有功能（测试是新增文件，运行时代码零改动）

---

## 五、后续工作

测试基础设施已搭建完成，后续开发新功能时应同步编写测试。建议逐步补充：
1. **API层测试**：使用MSW（Mock Service Worker）mock HTTP请求，测试hooks/queries
2. **组件测试**：为核心UI组件（Button、Input、Modal等）编写交互测试
3. **页面测试**：为关键用户流程（登录、发帖、评论）编写集成测试
4. **CI集成**：在GitHub Actions中添加`npm test`步骤，PR自动运行测试
5. **覆盖率目标**：工具函数100%，hooks 80%+，核心组件 60%+
