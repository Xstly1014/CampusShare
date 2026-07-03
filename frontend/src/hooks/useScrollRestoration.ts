import { useEffect, useLayoutEffect, useRef } from 'react'

const STORAGE_PREFIX = 'scroll:'

/**
 * 保存并恢复页面滚动位置，解决 React Router 路由切换导致列表页组件重挂载、
 * 异步数据加载期间页面高度不足使浏览器原生 scrollRestoration 失效的问题。
 *
 * - 组件卸载时把当前 window.scrollY 写入 sessionStorage（按 key 隔离）
 * - ready 变为 true 后（通常是列表数据加载完成）从 sessionStorage 读取并恢复
 * - 用 useLayoutEffect 在 DOM commit 后、浏览器绘制前同步恢复，避免"先闪到顶部再跳"的抖动
 * - 每个 key 仅在首次 ready 时恢复一次，避免后续筛选/排序变化触发误恢复
 * - key 变化时（如 schoolId 切换）自动重置恢复标记
 * - 全局禁用浏览器原生 scrollRestoration，避免与自定义恢复逻辑相互干扰
 */
export function useScrollRestoration(key: string, ready: boolean) {
  const restoredRef = useRef(false)

  // 全局禁用浏览器原生滚动恢复，完全由本 hook 接管
  useEffect(() => {
    if (!('scrollRestoration' in window.history)) return
    const original = window.history.scrollRestoration
    window.history.scrollRestoration = 'manual'
    return () => {
      window.history.scrollRestoration = original
    }
  }, [])

  // key 变化时重置恢复标记，确保不同页面的滚动位置互相独立
  useEffect(() => {
    restoredRef.current = false
  }, [key])

  // 卸载时保存当前位置
  useEffect(() => {
    return () => {
      try {
        sessionStorage.setItem(`${STORAGE_PREFIX}${key}`, String(window.scrollY))
      } catch {
        // sessionStorage 不可用或配额不足时静默忽略
      }
    }
  }, [key])

  // 首次 ready 时同步恢复（在浏览器绘制前，避免视觉抖动）
  useLayoutEffect(() => {
    if (!ready || restoredRef.current) return
    restoredRef.current = true

    try {
      const saved = sessionStorage.getItem(`${STORAGE_PREFIX}${key}`)
      if (!saved) return
      const y = parseInt(saved, 10)
      if (Number.isNaN(y) || y <= 0) return
      // 此时 DOM 已 commit（posts 已渲染），立即同步恢复滚动位置
      window.scrollTo(0, y)
      // 再用一次 rAF 兜底：某些情况下图片/异步内容在首帧后才会撑开高度
      requestAnimationFrame(() => window.scrollTo(0, y))
    } catch {
      // ignore
    }
  }, [key, ready])
}
