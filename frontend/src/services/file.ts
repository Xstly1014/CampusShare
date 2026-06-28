import { API_BASE_URL } from './http'

export interface UploadResult {
  url: string
  fileName: string
  originalName: string
  fileType: string
  fileSize: number
}

export const fileApi = {
  upload: async (file: File): Promise<UploadResult> => {
    const formData = new FormData()
    formData.append('file', file)

    const token = sessionStorage.getItem('campusshare_token')
    const headers: Record<string, string> = {}
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const res = await fetch(`${API_BASE_URL}/files/upload`, {
      method: 'POST',
      headers,
      body: formData,
    })

    if (!res.ok) {
      throw new Error(`上传失败: ${res.status}`)
    }

    const data = await res.json()
    if (data.code !== 200) {
      throw new Error(data.message || '上传失败')
    }

    const result = data.data
    return {
      url: result.url,
      fileName: result.fileName,
      originalName: result.fileName,
      fileType: result.fileType,
      fileSize: result.fileSize,
    }
  },
}
