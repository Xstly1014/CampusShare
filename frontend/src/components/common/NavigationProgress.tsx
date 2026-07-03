import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import NProgress from 'nprogress'

import 'nprogress/nprogress.css'

NProgress.configure({
  showSpinner: false,
  trickleSpeed: 120,
  minimum: 0.15,
  speed: 300,
})

export default function NavigationProgress() {
  const location = useLocation()
  const prevPathRef = useRef(location.pathname)
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    if (prevPathRef.current !== location.pathname) {
      NProgress.start()
      prevPathRef.current = location.pathname

      if (timerRef.current) {
        window.clearTimeout(timerRef.current)
      }
      timerRef.current = window.setTimeout(() => {
        NProgress.done()
        timerRef.current = null
      }, 250)
    }

    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current)
      }
      NProgress.done()
    }
  }, [location.pathname])

  return null
}
