import { useLayoutEffect } from 'react'
import { useLocation } from 'react-router-dom'

/**
 * 路由切换时滚动到顶部。
 *
 * useScrollRestoration hook 全局设置了 scrollRestoration='manual'，禁用了
 * 浏览器原生的滚动恢复。对于不使用该 hook 的页面（如 HomePage），浏览器
 * 不会自动滚动到顶部，前一个页面的滚动位置会被带到新页面，导致"点到首页
 * 在底部"的问题。
 *
 * 本组件在每次 pathname 变化时同步滚动到顶部（useLayoutEffect 在浏览器
 * 绘制前执行，避免视觉抖动）。使用 useScrollRestoration 的页面会在数据
 * 加载完成后由 hook 的 useLayoutEffect 覆盖此行为，恢复到保存的位置。
 *
 * 仅监听 pathname 变化，不监听 hash 变化：PostDetailPage 的 #comment-xxx
 * 锚点跳转由页面内部 effect 处理，不应被重置。
 */
export default function ScrollToTop() {
  const { pathname } = useLocation()

  useLayoutEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])

  return null
}
