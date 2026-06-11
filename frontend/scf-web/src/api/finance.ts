import http from './http'

export interface FinanceApplication {
  id: string
  finance_no: string
  product_type: string
  source_type: string
  source_id: string
  customer_id: string
  funding_party_id: string
  credit_id?: string
  apply_amount: string
  approved_amount?: string
  currency: string
  term_days: number
  annual_rate: string
  finance_status: string
  created_at: string
}

export async function listFinanceApplications(params?: {
  page_no?: number
  page_size?: number
  finance_status?: string
}) {
  const res = await http.get('/finance/applications', { params })
  return res.data
}

export async function getFinanceApplication(id: string) {
  const res = await http.get(`/finance/applications/${id}`)
  return res.data
}

export async function createFinanceApplication(body: {
  product_type: string
  source_type: string
  source_id: string
  customer_id: string
  funding_party_id: string
  credit_id?: string
  apply_amount: string
  currency: string
  term_days: number
  annual_rate: string
  guarantee_amount?: string
  pledge_rate?: string
}) {
  const res = await http.post('/finance/applications', body)
  return res.data
}

export async function submitFinanceApplication(id: string) {
  const res = await http.post(`/finance/applications/${id}/submit`)
  return res.data
}

export async function approveFinanceApplication(id: string) {
  const res = await http.post(`/finance/applications/${id}/approve`)
  return res.data
}

export interface FinancePreCheckItem {
  code: string
  result: 'PASSED' | 'FAILED' | 'WARNING'
  message: string
}

export interface FinancePreCheckResult {
  finance_id: string
  passed: boolean
  checks: FinancePreCheckItem[]
  document_validation?: {
    passed: boolean
    missing?: unknown[]
    pending_review?: unknown[]
    warnings?: unknown[]
  }
}

export async function preCheckFinanceApplication(
  id: string,
  body?: {
    disburse_amount?: string
    currency?: string
    value_date?: string
    payer_account_id?: string
    receiver_account_id?: string
    funding_channel?: string
    idempotency_key?: string
    secondary_auth_token?: string
  }
) {
  const res = await http.post(`/finance/applications/${id}/pre-check`, body ?? {})
  return res.data
}

export async function disburseFinanceApplication(
  id: string,
  body: {
    disburse_amount: string
    currency: string
    value_date: string
    payer_account_id: string
    receiver_account_id: string
    funding_channel: string
    remark?: string
  },
  headers?: { idempotencyKey?: string; secondaryAuthToken?: string }
) {
  const res = await http.post(`/finance/applications/${id}/disburse`, body, {
    headers: {
      ...(headers?.idempotencyKey ? { 'X-Idempotency-Key': headers.idempotencyKey } : {}),
      ...(headers?.secondaryAuthToken ? { 'X-Secondary-Auth-Token': headers.secondaryAuthToken } : {})
    }
  })
  return res.data
}
