import type { AxiosError } from 'axios'

type ApiErrorBody = { message?: string; code?: string }

/** 从 API 响应或 Error 中提取用户可读错误信息 */
export function apiErrorMessage(e: unknown, fallback: string): string {
  if (e === 'cancel') return fallback
  const err = e as AxiosError<ApiErrorBody> & { message?: string }
  const fromBody = err.response?.data?.message
  if (fromBody) return fromBody
  if (err.message) return err.message
  return fallback
}

/** 业务 envelope：success=false 时抛错 */
export function assertApiSuccess<T>(
  res: { success?: boolean; message?: string; data?: T },
  fallback: string
): T | undefined {
  if (res.success) return res.data
  throw new Error(res.message || fallback)
}
