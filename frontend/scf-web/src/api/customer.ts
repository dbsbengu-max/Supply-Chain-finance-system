import http from './http'

export interface Enterprise {
  id: string
  enterprise_code: string
  enterprise_name: string
  enterprise_type: string
  country_region: string
  kyc_status: string
  status: string
}

export async function listEnterprises(params?: {
  page_no?: number
  page_size?: number
  enterprise_type?: string
  kyc_status?: string
}) {
  const res = await http.get('/customers/enterprises', { params })
  return res.data
}

export async function createEnterprise(body: {
  enterprise_name: string
  enterprise_type: string
  country_region: string
  registration_no?: string
  unified_credit_code?: string
  legal_person?: string
}) {
  const res = await http.post('/customers/enterprises', body)
  return res.data
}

export async function submitKyc(id: string) {
  const res = await http.post(`/customers/enterprises/${id}/submit-kyc`)
  return res.data
}

export async function approveKyc(id: string) {
  const res = await http.post(`/customers/enterprises/${id}/approve-kyc`)
  return res.data
}

export async function rejectKyc(id: string, reason?: string) {
  const res = await http.post(`/customers/enterprises/${id}/reject-kyc`, { reason })
  return res.data
}
