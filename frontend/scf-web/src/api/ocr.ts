import http from './http'

export interface OcrField {
  field_name: string
  suggested_value: string
  confidence: number
  source_text?: string
  page_no?: number
  bbox?: string
  confirm_status: string
  confirmed_value?: string
  requires_manual_confirm: boolean
}

export interface OcrJob {
  id: string
  file_id: string
  business_type: string
  business_id?: string
  recognition_type: string
  status: string
  model_version: string
  fields: OcrField[]
  pending_manual_count: number
}

export async function createOcrJob(body: {
  file_id: string
  business_type: string
  business_id?: string
  recognition_type: string
}) {
  const res = await http.post('/ai/ocr/jobs', body)
  return res.data.data as OcrJob
}

export async function getOcrJob(id: string) {
  const res = await http.get(`/ai/ocr/jobs/${id}`)
  return res.data.data as OcrJob
}

export async function confirmOcrJob(id: string, confirmed_fields: Record<string, string>) {
  const res = await http.post(`/ai/ocr/jobs/${id}/confirm`, { confirmed_fields })
  return res.data.data
}
