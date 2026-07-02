import { describe, it, expect } from 'vitest'
import { cn } from '../lib/utils'

describe('cn utility (clsx + tailwind-merge)', () => {
  it('merges class names', () => {
    expect(cn('foo', 'bar')).toBe('foo bar')
  })

  it('handles conditional classes', () => {
    expect(cn('base', true && 'active', false && 'hidden')).toBe('base active')
  })

  it('handles undefined and null', () => {
    expect(cn('base', undefined, null, 'extra')).toBe('base extra')
  })

  it('merges conflicting Tailwind classes (tailwind-merge)', () => {
    expect(cn('px-2 py-1', 'px-4')).toBe('py-1 px-4')
  })

  it('handles array of classes', () => {
    expect(cn(['a', 'b'], ['c'])).toBe('a b c')
  })

  it('handles object syntax', () => {
    expect(cn({ foo: true, bar: false, baz: true })).toBe('foo baz')
  })

  it('returns empty string for no inputs', () => {
    expect(cn()).toBe('')
  })
})
