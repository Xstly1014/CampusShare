import { useState, useEffect } from 'react'
import { ChevronUp } from 'lucide-react'

interface BackToTopButtonProps {
  /** 距右侧距离（px），默认 16，与发帖按钮 right-4 对齐 */
  right?: number
  /** 距底部距离（px），默认 144，刚好位于 bottom-20 (80px) 的发帖按钮上方 */
  bottom?: number
  /** 滚动超过该阈值才显示按钮，默认 300px */
  threshold?: number
}

/**
 * 返回顶部浮动按钮。滚动超过阈值后淡入显示，点击平滑滚动回顶部。
 * 通过 right/bottom props 控制位置，以适配不同页面发帖按钮的布局。
 */
export default function BackToTopButton({
  right = 16,
  bottom = 144,
  threshold = 300,
}: BackToTopButtonProps) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      setVisible(window.scrollY > threshold)
    }
    handleScroll()
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [threshold])

  if (!visible) return null

  return (
    <button
      onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
      style={{ right: `${right}px`, bottom: `${bottom}px` }}
      className="fixed w-10 h-10 rounded-full bg-white text-gray-700 shadow-lg border border-gray-100 hover:bg-gray-50 hover:scale-105 active:scale-95 transition-all flex items-center justify-center z-30"
      aria-label="返回顶部"
      title="返回顶部"
    >
      <ChevronUp className="w-5 h-5" />
    </button>
  )
}
