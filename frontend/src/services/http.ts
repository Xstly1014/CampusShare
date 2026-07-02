import axios, { AxiosError, AxiosProgressEvent, InternalAxiosRequestConfig } from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

const TOKEN_KEY = 'campusshare_token'
const REFRESH_TOKEN_KEY = 'campusshare_refresh_token'
const USER_KEY = 'campusshare_user'

let isRefreshing = false
let failedQueue: Array<{
  resolve: (token: string) => void
  reject: (error: any) => void
}> = []

function processQueue(error: any, token: string | null = null) {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token!)
    }
  })
  failedQueue = []
}

function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

function getRefreshToken(): string | null {
  return sessionStorage.getItem(REFRESH_TOKEN_KEY)
}

function clearAuth() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(REFRESH_TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
}

function redirectToLogin() {
  clearAuth()
  if (window.location.pathname !== '/') {
    window.location.href = '/'
  }
}

const instance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error) => Promise.reject(error),
)

instance.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse
    if (data && typeof data === 'object' && 'code' in data) {
      if (data.code !== 200) {
        return Promise.reject(new Error(data.message || '请求失败'))
      }
    }
    return response.data
  },
  async (error: AxiosError<ApiResponse>) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    if (!error.response) {
      return Promise.reject(new Error('网络连接失败，请检查网络'))
    }

    const status = error.response.status
    const message = error.response.data?.message || error.message

    if (status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        })
          .then(token => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            return instance(originalRequest)
          })
          .catch(err => Promise.reject(err))
      }

      originalRequest._retry = true
      isRefreshing = true

      const refreshToken = getRefreshToken()
      if (refreshToken) {
        try {
          const res = await axios.post<ApiResponse<{ token: string; refreshToken: string }>>(
            `${API_BASE_URL}/auth/refresh-token`,
            { refreshToken },
          )
          if (res.data?.code === 200 && res.data.data?.token) {
            const newToken = res.data.data.token
            const newRefreshToken = res.data.data.refreshToken || refreshToken
            sessionStorage.setItem(TOKEN_KEY, newToken)
            sessionStorage.setItem(REFRESH_TOKEN_KEY, newRefreshToken)
            originalRequest.headers.Authorization = `Bearer ${newToken}`
            processQueue(null, newToken)
            return instance(originalRequest)
          }
        } catch {
          processQueue(error, null)
        } finally {
          isRefreshing = false
        }
      }

      redirectToLogin()
      return Promise.reject(new Error('登录已过期，请重新登录'))
    }

    if (status === 403) {
      return Promise.reject(new Error('没有权限执行此操作'))
    }

    if (status >= 500) {
      return Promise.reject(new Error('服务器错误，请稍后重试'))
    }

    return Promise.reject(new Error(message || '请求失败'))
  },
)

export interface UploadProgress {
  loaded: number
  total: number
  percent: number
}

export interface UploadOptions {
  onUploadProgress?: (progress: UploadProgress) => void
}

export const api = {
  get: <T = any>(url: string) =>
    instance.get<any, ApiResponse<T>>(url),

  post: <T = any>(url: string, body?: any, options?: UploadOptions) =>
    instance.post<any, ApiResponse<T>>(url, body, {
      onUploadProgress: options?.onUploadProgress
        ? (e: AxiosProgressEvent) => {
            if (e.total) {
              options.onUploadProgress!({
                loaded: e.loaded,
                total: e.total,
                percent: Math.round((e.loaded * 100) / e.total),
              })
            }
          }
        : undefined,
    }),

  put: <T = any>(url: string, body?: any) =>
    instance.put<any, ApiResponse<T>>(url, body),

  delete: <T = any>(url: string) =>
    instance.delete<any, ApiResponse<T>>(url),

  upload: <T = any>(url: string, file: File, fieldName: string = 'file', options?: UploadOptions) => {
    const formData = new FormData()
    formData.append(fieldName, file)
    return instance.post<any, ApiResponse<T>>(url, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: options?.onUploadProgress
        ? (e: AxiosProgressEvent) => {
            if (e.total) {
              options.onUploadProgress!({
                loaded: e.loaded,
                total: e.total,
                percent: Math.round((e.loaded * 100) / e.total),
              })
            }
          }
        : undefined,
    })
  },
}

export { API_BASE_URL }
