/**
 * Format time string from backend (yyyy-MM-dd HH:mm:ss) to relative display.
 *
 * Rules:
 * - < 1 second or negative: 刚刚
 * - < 60 seconds: X秒前
 * - < 60 minutes: X分钟前
 * - Same day: X小时前
 * - Yesterday: 昨天 HH:mm
 * - This week (before yesterday): 星期X HH:mm
 * - This year (before this week): M月D日
 * - Previous years: YYYY年M月D日
 */
export function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr.replace(' ', 'T'))
  const now = new Date()

  // Handle clock skew (server slightly ahead of client)
  let diff = now.getTime() - date.getTime()
  if (diff < 0) diff = 0

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))

  const isSameDay = date.toDateString() === now.toDateString()
  if (isSameDay) {
    if (seconds < 1) return '刚刚'
    if (seconds < 60) return `${seconds}秒前`
    if (minutes < 60) return `${minutes}分钟前`
    return `${hours}小时前`
  }

  // Yesterday
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  const isYesterday = date.toDateString() === yesterday.toDateString()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  if (isYesterday) return `昨天 ${hh}:${mm}`

  // This week (before yesterday): compute day-of-week difference
  // 0=Sun, 1=Mon, ... 6=Sat
  const nowDay = now.getDay()
  const dateDay = date.getDay()
  // Days since start of this week (Monday-based)
  const nowWeekStart = new Date(now)
  const nowOffset = nowDay === 0 ? 6 : nowDay - 1 // days since Monday
  nowWeekStart.setDate(now.getDate() - nowOffset)
  nowWeekStart.setHours(0, 0, 0, 0)

  if (date >= nowWeekStart) {
    const weekDays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
    return `${weekDays[dateDay]} ${hh}:${mm}`
  }

  // This year
  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}月${date.getDate()}日`
  }

  // Previous years
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}
