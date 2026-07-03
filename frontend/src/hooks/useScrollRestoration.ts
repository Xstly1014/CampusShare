import { useEffect, useLayoutEffect, useRef } from 'react'

const STORAGE_PREFIX = 'scroll:'

/**
 * 保存并恢复页面滚动位置，解决 React Router 路由切换导致列表页组件重挂载、
 * 异步数据加载期间页面高度不足使浏览器原生 scrollRestoration 失效的问题。
 *
 * 保存策略：在滚动事件中持续写入 sessionStorage（rAF 节流）。
 *   不依赖组件卸载时保存——因为 useEffect 的 cleanup 在浏览器绘制后异步执行，
 *   此时本组件 DOM 已被移除、页面高度骤减，浏览器会把 window.scrollY clamp
 *   到 0，导致保存到错误值。滚动时 DOM 尚未变更，scrollY 是用户真实位置。
 *
 * 恢复策略：ready 变为 true 后（列表数据加载完成、DOM 已渲染），用
 *   useLayoutEffect 在浏览器绘制前同步恢复，避免"先闪到顶部再跳"的抖动。
 *   每个 key 仅在首次 ready 时恢复一次，避免后续筛选/排序变化触发误恢复。
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

  // 滚动时持续保存位置（rAF 节流，每帧最多写一次）
  // 注意：挂载时不主动保存，避免覆盖待恢复的旧值
  useEffect(() => {
    let ticking = false
    const onScroll = () => {
      if (ticking) return
      ticking = true
      requestAnimationFrame(() => {
        try {
          sessionStorage.setItem(`${STORAGE_PREFIX}${key}`, String(window.scrollY))
        } catch {
          // sessionStorage 不可用或配额不足时静默忽略
        }
        ticking = false
      })
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', onScroll)
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
