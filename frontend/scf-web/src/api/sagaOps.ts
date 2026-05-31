import http from './http'

export interface SagaOpsSummary {
  outbox_pending: number
  outbox_failed: number
  outbox_manual_required: number
  compensation_pending: number
  compensation_failed: number
  compensation_manual_required: number
  outbox_by_status: { status: string; count: number }[]
  compensation_by_status: { status: string; count: number }[]
}

export interface OutboxEventItem {
  id: string
  event_type: string
  business_type: string
  business_id: string
  event_status: string
  retry_count: number
  next_retry_at?: string
  last_error?: string
  created_at?: string
  updated_at?: string
}

export interface CompensationTaskItem {
  id: string
  compensation_type: string
  business_type: string
  business_id: string
  compensation_status: string
  retry_count: number
  next_retry_at?: string
  last_error?: string
  approved_by?: string
  executed_at?: string
  created_at?: string
  updated_at?: string
}

export interface SagaOpsFilterMeta {
  outbox_statuses: string[]
  compensation_statuses: string[]
  compensation_types: string[]
}

export async function getSagaOpsSummary() {
  const res = await http.get('/saga/ops/summary')
  return res.data
}

export async function getSagaOpsFilterMeta() {
  const res = await http.get('/saga/ops/meta/filters')
  return res.data
}

export async function listOutboxEvents(params?: {
  page_no?: number
  page_size?: number
  event_status?: string
  event_type?: string
  business_type?: string
  business_id?: string
}) {
  const res = await http.get('/saga/ops/outbox', { params })
  return res.data
}

export async function listCompensationTasks(params?: {
  page_no?: number
  page_size?: number
  compensation_status?: string
  business_type?: string
  compensation_type?: string
  business_id?: string
}) {
  const res = await http.get('/saga/ops/compensation-tasks', { params })
  return res.data
}

export async function retryOutboxEvent(id: string) {
  const res = await http.post(`/saga/ops/outbox/${id}/retry`)
  return res.data
}

export async function retryCompensationTask(id: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/retry`)
  return res.data
}

export async function approveCompensationTask(id: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/approve-execute`)
  return res.data
}
