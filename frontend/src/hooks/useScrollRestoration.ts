import { useEffect, useLayoutEffect, useRef } from 'react'

const STORAGE_PREFIX = 'scroll:'

/**
 * 清除指定 key 的滚动位置记录。
 * 用于用户主动返回上级页面时清除保存的滚动位置，使下次进入时从顶部开始。
 */
export function clearScrollPosition(key: string) {
  try {
    sessionStorage.removeItem(`${STORAGE_PREFIX}${key}`)
  } catch {
    // sessionStorage 不可用时静默忽略
  }
}

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
 *
 * 无限滚动支持：如果保存的滚动位置超出当前文档高度（返回后只加载了第一页），
 *   先滚动到最大位置触发 IntersectionObserver 加载更多数据，同时监听 body
 *   尺寸变化，文档高度足够后自动恢复到精确位置。
 */
export function useScrollRestoration(key: string, ready: boolean) {
  const restoredRef = useRef(false)
  // 恢复进行中标志：防止触发 IntersectionObserver 产生的 scroll 事件覆盖原始值
  const restoringRef = useRef(false)

  // 全局禁用浏览器原生滚动恢复，完全由本 hook 接管
  // 不在 cleanup 中恢复原值：组件卸载后若恢复为 'auto'，返回导航时浏览器
  // 会在数据加载前尝试原生恢复，此时页面高度不足 scrollY 被 clamp 到 0，
  // 触发的 scroll 事件会覆盖 sessionStorage 中已保存的真实位置
  useEffect(() => {
    if (!('scrollRestoration' in window.history)) return
    window.history.scrollRestoration = 'manual'
  }, [])

  // key 变化时重置恢复标记，确保不同页面的滚动位置互相独立
  useEffect(() => {
    restoredRef.current = false
    restoringRef.current = false
  }, [key])

  // 滚动时持续保存位置（rAF 节流，每帧最多写一次）
  // 仅在 ready=true（数据加载完成）且不在恢复过程中时才保存：
  // 加载期间页面高度不足，浏览器 clamp 产生的 scrollY=0 不应覆盖已保存的真实位置；
  // 恢复期间滚动到 maxScroll 触发 IntersectionObserver 的 scroll 事件也不应覆盖
  useEffect(() => {
    if (!ready) return
    let ticking = false
    const onScroll = () => {
      if (ticking) return
      ticking = true
      requestAnimationFrame(() => {
        if (!restoringRef.current) {
          try {
            sessionStorage.setItem(`${STORAGE_PREFIX}${key}`, String(window.scrollY))
          } catch {
            // sessionStorage 不可用或配额不足时静默忽略
          }
        }
        ticking = false
      })
    }
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      window.removeEventListener('scroll', onScroll)
    }
  }, [key, ready])

  // 首次 ready 时恢复滚动位置（在浏览器绘制前，避免视觉抖动）
  useLayoutEffect(() => {
    if (!ready || restoredRef.current) return
    restoredRef.current = true

    let savedY: number
    try {
      const saved = sessionStorage.getItem(`${STORAGE_PREFIX}${key}`)
      if (!saved) return
      savedY = parseInt(saved, 10)
      if (Number.isNaN(savedY) || savedY <= 0) return
    } catch {
      return
    }

    const tryRestore = (): boolean => {
      const maxScroll = document.documentElement.scrollHeight - window.innerHeight
      if (savedY <= maxScroll) {
        // 文档高度足够，立即恢复并解除恢复锁
        restoringRef.current = false
        window.scrollTo(0, savedY)
        // rAF 兜底：某些情况下异步内容在首帧后才会撑开高度
        requestAnimationFrame(() => window.scrollTo(0, savedY))
        return true
      }
      // 文档高度不足（可能是无限滚动页面返回后只加载了第一页）
      // 滚动到最大位置以触发 IntersectionObserver 加载更多数据
      if (maxScroll > 0) {
        window.scrollTo(0, maxScroll)
      }
      return false
    }

    // 标记恢复进行中，阻止 scroll 事件覆盖 sessionStorage 中的原始值
    restoringRef.current = true

    // 立即尝试恢复
    if (tryRestore()) return

    // 文档高度不足：监听 body 尺寸变化，更多数据加载后自动重试
    const observer = new ResizeObserver(() => {
      if (tryRestore()) {
        observer.disconnect()
      }
    })
    observer.observe(document.body)

    // 5秒超时清理，避免内存泄漏；超时后解除恢复锁
    const timeoutId = setTimeout(() => {
      restoringRef.current = false
      observer.disconnect()
    }, 5000)

    return () => {
      restoringRef.current = false
      observer.disconnect()
      clearTimeout(timeoutId)
    }
  }, [key, ready])
}
