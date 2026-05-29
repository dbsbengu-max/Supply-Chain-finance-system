import http from './http'

export interface DictItem {
  code: string
  label: string
}

export interface ValidMode {
  mode_key: string
  order_mode: string
  fund_source: string
  pickup_type: string
  label: string
}

export interface CrossDomainAction {
  code: string
  label: string
  hint: string
}

export interface AgencyPurchaseMeta {
  order_modes: DictItem[]
  fund_sources: DictItem[]
  pickup_types: DictItem[]
  application_statuses: DictItem[]
  valid_modes: ValidMode[]
  cross_domain_actions: CrossDomainAction[]
}

export interface AgencyPurchaseApplication {
  id: string
  application_no: string
  order_mode: string
  fund_source: string
  pickup_type: string
  mode_key: string
  customer_id: string
  trade_company_id: string
  order_id?: string
  currency: string
  total_amount: string
  application_status: string
  remark?: string
  bpm_instance_id?: string
  created_by: string
  created_at: string
  updated_at?: string
}

export async function getAgencyPurchaseMeta() {
  const res = await http.get('/agency-purchase/meta')
  return res.data
}

export async function listAgencyPurchaseApplications(params?: {
  page_no?: number
  page_size?: number
  application_status?: string
  order_mode?: string
  fund_source?: string
  pickup_type?: string
  customer_id?: string
  created_from?: string
  created_to?: string
}) {
  const res = await http.get('/agency-purchase/applications', { params })
  return res.data
}

export async function getAgencyPurchaseApplication(id: string) {
  const res = await http.get(`/agency-purchase/applications/${id}`)
  return res.data
}

export async function createAgencyPurchaseApplication(body: {
  order_mode: string
  fund_source: string
  pickup_type: string
  customer_id: string
  trade_company_id: string
  order_id?: string
  currency: string
  total_amount: string
  remark?: string
}) {
  const res = await http.post('/agency-purchase/applications', body)
  return res.data
}

export async function updateAgencyPurchaseApplication(
  id: string,
  body: {
    order_mode: string
    fund_source: string
    pickup_type: string
    customer_id: string
    trade_company_id: string
    order_id?: string
    currency: string
    total_amount: string
    remark?: string
  }
) {
  const res = await http.put(`/agency-purchase/applications/${id}`, body)
  return res.data
}

export async function submitAgencyPurchaseApplication(id: string) {
  const res = await http.post(`/agency-purchase/applications/${id}/submit`)
  return res.data
}

export async function cancelAgencyPurchaseApplication(id: string) {
  const res = await http.post(`/agency-purchase/applications/${id}/cancel`)
  return res.data
}
