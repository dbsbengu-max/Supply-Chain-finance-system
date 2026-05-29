import http from './http'

export interface AuditLogItem {
  id: string
  action: string
  action_label: string
  object_type: string
  object_type_label: string
  object_id: string
  user_id: string
  user_name: string
  enterprise_id?: string
  project_id?: string
  ip_address?: string
  operation_at: string
  related_route?: string
}

export interface AuditLogDetail extends AuditLogItem {
  before_value?: string
  after_value?: string
}

export interface AuditSummary {
  total: number
  by_object_type: { object_type: string; object_type_label: string; count: number }[]
}

export interface AuditFilterMeta {
  actions: string[]
  object_types: string[]
}

export async function listAuditLogs(params?: {
  page_no?: number
  page_size?: number
  action?: string
  object_type?: string
  object_id?: string
  user_id?: string
  from_at?: string
  to_at?: string
  keyword?: string
}) {
  const res = await http.get('/audit/logs', { params })
  return res.data
}

export async function getAuditLog(id: string) {
  const res = await http.get(`/audit/logs/${id}`)
  return res.data
}

export async function getAuditSummary(days = 7) {
  const res = await http.get('/audit/summary', { params: { days } })
  return res.data
}

export async function getAuditFilterMeta() {
  const res = await http.get('/audit/meta/filters')
  return res.data
}
