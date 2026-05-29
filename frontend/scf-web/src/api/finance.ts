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
