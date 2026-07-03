import * as Sentry from '@sentry/react'

const SENTRY_DSN = import.meta.env.VITE_SENTRY_DSN
const ENV = import.meta.env.MODE

export function initMonitoring(): void {
  if (!SENTRY_DSN || ENV !== 'production') {
    return
  }

  Sentry.init({
    dsn: SENTRY_DSN,
    environment: ENV,
    integrations: [Sentry.browserTracingIntegration(), Sentry.replayIntegration()],
    tracesSampleRate: 0.1,
    tracePropagationTargets: ['localhost', /^https?:\/\/192\.168\.150\.103/],
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,
  })
}

export function captureException(error: Error, context?: Record<string, unknown>): void {
  if (SENTRY_DSN && ENV === 'production') {
    Sentry.captureException(error, { extra: context })
  } else {
    console.error('[Error]', error, context)
  }
}

export function captureMessage(
  message: string,
  level: 'fatal' | 'error' | 'warning' | 'log' | 'info' | 'debug' = 'info',
): void {
  if (SENTRY_DSN && ENV === 'production') {
    Sentry.captureMessage(message, level)
  } else {
    console.log(`[${level}]`, message)
  }
}
