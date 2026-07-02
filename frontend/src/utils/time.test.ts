import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { formatTime } from './time'

describe('formatTime', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns empty string for falsy input', () => {
    expect(formatTime('')).toBe('')
  })

  it('returns "刚刚" for just now (0 seconds ago)', () => {
    const now = new Date('2026-07-02T12:00:00')
    vi.setSystemTime(now)
    expect(formatTime('2026-07-02 12:00:00')).toBe('刚刚')
  })

  it('returns "刚刚" for future time (clock skew)', () => {
    const now = new Date('2026-07-02T12:00:00')
    vi.setSystemTime(now)
    expect(formatTime('2026-07-02 12:00:30')).toBe('刚刚')
  })

  it('returns "X秒前" for < 60 seconds', () => {
    const now = new Date('2026-07-02T12:00:30')
    vi.setSystemTime(now)
    expect(formatTime('2026-07-02 12:00:00')).toBe('30秒前')
  })

  it('returns "X分钟前" for < 60 minutes', () => {
    const now = new Date('2026-07-02T12:05:00')
    vi.setSystemTime(now)
    expect(formatTime('2026-07-02 12:00:00')).toBe('5分钟前')
  })

  it('returns "X小时前" for same day but >= 1 hour', () => {
    const now = new Date('2026-07-02T15:00:00')
    vi.setSystemTime(now)
    expect(formatTime('2026-07-02 12:00:00')).toBe('3小时前')
  })

  it('returns "昨天 HH:mm" for yesterday', () => {
    const now = new Date('2026-07-02T10:00:00')
    vi.setSystemTime(now)
    expect(formatTime('2026-07-01 15:30:00')).toBe('昨天 15:30')
  })

  it('returns weekday + HH:mm for this week (before yesterday)', () => {
    const now = new Date('2026-07-02T10:00:00')
    vi.setSystemTime(now)
    expect(formatTime('2026-06-29 09:00:00')).toBe('星期一 09:00')
  })

  it('returns "M月D日" for this year before this week', () => {
    const now = new Date('2026-07-02T10:00:00')
    vi.setSystemTime(now)
    expect(formatTime('2026-05-01 10:00:00')).toBe('5月1日')
  })

  it('returns "YYYY年M月D日" for previous years', () => {
    const now = new Date('2026-07-02T10:00:00')
    vi.setSystemTime(now)
    expect(formatTime('2025-12-25 08:00:00')).toBe('2025年12月25日')
  })
})
