import http from './http'

export interface ContractSignTask {
  id: string
  document_id: string
  provider_code: string
  external_sign_ref?: string
  task_status: string
  callback_status?: string
  failure_reason?: string
  retry_count: number
  last_retry_at?: string
  signed_at?: string
  created_at?: string
  updated_at?: string
}

export interface ContractSignResult {
  document_id: string
  sign_status: string
  contract_status: string
  sign_provider?: string
  external_sign_ref?: string
  task?: ContractSignTask
}

export interface DocumentCenterItem {
  id: string
  business_type: string
  business_id: string
  document_type: string
  document_no?: string
  file_id: string
  document_status: string
  review_status: string
  contract_status: string
  sign_status?: string
  ocr_status: string
  ocr_confidence?: number
  ocr_job_id?: string
  updated_at?: string
  created_at?: string
}

export interface DocumentReviewLog {
  id: string
  action: string
  before_status?: string
  after_status?: string
  operator_id?: string
  operator_role?: string
  reason?: string
  created_at?: string
}

export interface DocumentCenterDetail extends DocumentCenterItem {
  operator_id: string
  project_id: string
  review_result?: string
  review_reason?: string
  sign_status?: string
  validation_status?: string
  reviewed_by?: string
  reviewed_at?: string
  review_logs?: DocumentReviewLog[]
}

export interface DocumentRequirement {
  id: string
  project_id?: string
  business_type: string
  business_stage: string
  product_type?: string
  document_type: string
  required_flag: boolean
  ocr_required: boolean
  manual_review_required: boolean
  min_confidence?: number
  enabled: boolean
  sort_no: number
}

export interface DocumentValidateResult {
  passed: boolean
  business_type: string
  business_id: string
  missing: { document_type: string; required: boolean; message: string }[]
  pending_review: { document_id: string; document_type: string; review_status: string }[]
  warnings: { document_id: string; document_type: string; message: string }[]
}

export interface PageResult<T> {
  page_no: number
  page_size: number
  total: number
  records: T[]
}

export function listDocuments(params: Record<string, string | number | undefined>) {
  return http.get<{ data: PageResult<DocumentCenterItem> }>('/documents/center', { params })
}

export function getDocument(id: string) {
  return http.get<{ data: DocumentCenterDetail }>(`/documents/center/${id}`)
}

export function registerDocument(body: {
  business_type: string
  business_id: string
  document_type: string
  file_id: string
  document_no?: string
  trigger_ocr?: boolean
}) {
  return http.post<{ data: DocumentCenterDetail }>('/documents/center', body)
}

export function triggerDocumentOcr(id: string) {
  return http.post<{ data: DocumentCenterDetail }>(`/documents/center/${id}/ocr`)
}

export function submitDocumentReview(id: string) {
  return http.post<{ data: DocumentCenterDetail }>(`/documents/center/${id}/submit-review`)
}

export function approveDocument(id: string, reason?: string) {
  return http.post<{ data: DocumentCenterDetail }>(`/documents/center/${id}/approve`, { reason })
}

export function rejectDocument(id: string, reason: string) {
  return http.post<{ data: DocumentCenterDetail }>(`/documents/center/${id}/reject`, { reason })
}

export function archiveDocument(id: string, reason?: string) {
  return http.post<{ data: DocumentCenterDetail }>(`/documents/center/${id}/archive`, { reason })
}

export function listDocumentRequirements(params?: Record<string, string | undefined>) {
  return http.get<{ data: DocumentRequirement[] }>('/documents/requirements', { params })
}

export function createDocumentRequirement(body: Partial<DocumentRequirement> & {
  business_type: string
  business_stage: string
  document_type: string
}) {
  return http.post<{ data: DocumentRequirement }>('/documents/requirements', body)
}

export function updateDocumentRequirement(id: string, body: Partial<DocumentRequirement> & {
  business_type: string
  business_stage: string
  document_type: string
}) {
  return http.put<{ data: DocumentRequirement }>(`/documents/requirements/${id}`, body)
}

export function validateDocuments(body: {
  business_type: string
  business_id: string
  business_stage: string
  product_type?: string
}) {
  return http.post<{ data: DocumentValidateResult }>('/documents/validate', body)
}

export function initiateContractSign(
  documentId: string,
  body?: { provider_code?: string; simulate_failure?: boolean }
) {
  return http.post<{ data: ContractSignResult }>(`/documents/center/${documentId}/sign`, body ?? {})
}

export function retryContractSign(documentId: string) {
  return http.post<{ data: ContractSignResult }>(`/documents/center/${documentId}/sign/retry`)
}

export function listContractSignTasks(documentId: string) {
  return http.get<{ data: ContractSignTask[] }>(`/documents/center/${documentId}/sign/tasks`)
}
