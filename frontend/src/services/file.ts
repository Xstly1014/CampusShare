import { api, UploadOptions, UploadProgress } from './http'

export interface UploadResult {
  url: string
  fileName: string
  originalName: string
  fileType: string
  fileSize: number
}

export const fileApi = {
  upload: async (file: File, options?: UploadOptions): Promise<UploadResult> => {
    const res = await api.upload<{
      url: string
      fileName: string
      originalName: string
      fileType: string
      fileSize: number
    }>('/files/upload', file, 'file', options)

    const result = res.data
    return {
      url: result.url,
      fileName: result.fileName,
      originalName: result.originalName,
      fileType: result.fileType,
      fileSize: result.fileSize,
    }
  },
}

export type { UploadProgress, UploadOptions }
