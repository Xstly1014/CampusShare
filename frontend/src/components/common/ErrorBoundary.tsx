/* eslint-disable react-refresh/only-export-components */
import { Component, type ErrorInfo, type ReactNode } from 'react'
import { captureException } from '../../lib/monitoring'

interface ErrorBoundaryProps {
  children: ReactNode
  fallback?: ReactNode
  onError?: (error: Error, errorInfo: ErrorInfo) => void
  level?: 'root' | 'page' | 'component'
}

interface ErrorBoundaryState {
  hasError: boolean
  error: Error | null
}

function DefaultFallback({
  error,
  level = 'component',
  onReset,
}: {
  error: Error | null
  level?: 'root' | 'page' | 'component'
  onReset: () => void
}) {
  const isDev = import.meta.env.DEV
  const titles = {
    root: '应用出错了',
    page: '页面加载失败',
    component: '组件加载失败',
  }
  const descriptions = {
    root: '应用遇到了意外错误，请刷新页面重试。',
    page: '此页面遇到了问题，其他页面仍可正常使用。',
    component: '此部分内容加载失败，可以尝试刷新。',
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[400px] p-8 text-center">
      <div className="w-16 h-16 mb-4 text-red-400">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={1.5}
          stroke="currentColor"
          className="w-full h-full"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126ZM12 15.75h.007v.008H12v-.008Z"
          />
        </svg>
      </div>
      <h2 className="text-xl font-semibold text-gray-800 mb-2">{titles[level]}</h2>
      <p className="text-gray-500 mb-4 max-w-md">{descriptions[level]}</p>
      <button
        onClick={onReset}
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
      >
        {level === 'root' ? '刷新页面' : '重试'}
      </button>
      {isDev && error && (
        <details className="mt-4 text-left w-full max-w-2xl">
          <summary className="cursor-pointer text-sm text-gray-500 hover:text-gray-700">
            错误详情（开发环境）
          </summary>
          <pre className="mt-2 p-4 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700 overflow-auto max-h-64">
            {error.message}
            {'\n\n'}
            {error.stack}
          </pre>
        </details>
      )}
    </div>
  )
}

export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    if (this.props.onError) {
      this.props.onError(error, errorInfo)
    }
    captureException(error, {
      level: this.props.level ?? 'component',
      componentStack: errorInfo.componentStack,
    })
    console.error('[ErrorBoundary]', error, errorInfo)
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null })
    if (this.props.level === 'root') {
      window.location.reload()
    }
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback
      }
      return (
        <DefaultFallback
          error={this.state.error}
          level={this.props.level}
          onReset={this.handleReset}
        />
      )
    }
    return this.props.children
  }
}
