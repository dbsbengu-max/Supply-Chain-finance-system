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

export interface OutboxEventDetail extends OutboxEventItem {
  idempotency_key?: string
  payload_json?: string
  related_route?: string
}

export interface CompensationTaskItem {
  id: string
  compensation_type: string
  business_type: string
  business_id: string
  compensation_status: string
  high_risk?: boolean
  retry_count: number
  next_retry_at?: string
  last_error?: string
  approved_by?: string
  claimed_by?: string
  executed_at?: string
  created_at?: string
  updated_at?: string
}

export interface CompensationImpact {
  order_id?: string
  order_status?: string
  finance_application_id?: string
  inventory_id?: string
  margin_account_id?: string
  document_id?: string
  external_sign_ref?: string
  provider_code?: string
  sign_task_status?: string
  suggested_action?: string
}

export interface ContractSignCallbackAction {
  action?: string
  external_sign_ref?: string
  callback_status?: string
  provider_code?: string
  signed_at?: string
  failure_reason?: string
  idempotency_key?: string
  reason_code?: string
  reason_message?: string
}

export interface CompensationAuditEntry {
  action: string
  user_id?: string
  operation_at?: string
  detail?: string
}

export interface CompensationTaskDetail extends CompensationTaskItem {
  source_event_id?: string
  action_json?: string
  related_route?: string
  claimed_at?: string
  submitted_by?: string
  submitted_at?: string
  handle_reason?: string
  closed_by?: string
  closed_at?: string
  impact?: CompensationImpact
  audit_timeline?: CompensationAuditEntry[]
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

export async function getOutboxEventDetail(id: string) {
  const res = await http.get(`/saga/ops/outbox/${id}`)
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

export async function getCompensationTaskDetail(id: string) {
  const res = await http.get(`/saga/ops/compensation-tasks/${id}`)
  return res.data
}

export async function retryOutboxEvent(id: string, reason: string) {
  const res = await http.post(`/saga/ops/outbox/${id}/retry`, { reason })
  return res.data
}

export async function retryCompensationTask(id: string, reason: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/retry`, { reason })
  return res.data
}

export async function approveCompensationTask(id: string, reason: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/approve-execute`, { reason })
  return res.data
}

export async function claimCompensationTask(id: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/claim`)
  return res.data
}

export async function submitCompensationApproval(id: string, reason: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/submit-approval`, { reason })
  return res.data
}

export async function ignoreCompensationTask(id: string, reason: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/ignore`, { reason })
  return res.data
}

export async function closeCompensationTask(id: string, reason: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/close`, { reason })
  return res.data
}

export interface ContractSignProviderStatus {
  external_sign_ref?: string
  provider_code?: string
  provider_status?: string
  signed_at?: string
  failure_reason?: string
  supports_status_query?: boolean
}

export interface ContractSignStatusQueryResult {
  external_sign_ref?: string
  provider?: ContractSignProviderStatus
  reconciled?: boolean
  reconcile_action?: string
  message?: string
  document?: {
    document_id?: string
    sign_status?: string
    contract_status?: string
  }
  local_task?: {
    task_status?: string
    external_sign_ref?: string
  }
}

export async function queryCompensationSignStatus(id: string, reason: string) {
  const res = await http.post(`/saga/ops/compensation-tasks/${id}/query-sign-status`, { reason })
  return res.data
}
