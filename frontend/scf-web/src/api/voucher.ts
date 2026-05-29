import http from './http'

export interface Voucher {
  id: string
  voucher_no: string
  issuer_id: string
  acceptor_id: string
  holder_id: string
  parent_voucher_id?: string
  amount: string
  available_amount: string
  currency: string
  issue_date: string
  due_date: string
  voucher_status: string
  evidence_status: string
  version_no: number
}

export interface VoucherFlow {
  id: string
  voucher_id: string
  flow_type: string
  from_holder_id?: string
  to_holder_id?: string
  amount: string
  before_available_amount: string
  after_available_amount: string
  related_voucher_id?: string
  operated_by: string
  operated_at: string
}

export interface VoucherFinanceSummary {
  finance_occupied_amount: string
  released_amount: string
  pending_redeem_amount: string
}

export interface VoucherDetail {
  voucher: Voucher
  flows: VoucherFlow[]
  finance_summary?: VoucherFinanceSummary
}

export async function listVouchers(params?: { page_no?: number; page_size?: number; status?: string }) {
  const { data } = await http.get('/dv/vouchers', { params })
  return data
}

export async function getVoucher(id: string) {
  const { data } = await http.get(`/dv/vouchers/${id}`)
  return data
}

export async function createVoucher(body: {
  issuer_id: string
  acceptor_id: string
  holder_id: string
  amount: string
  currency: string
  issue_date?: string
  due_date: string
}) {
  const { data } = await http.post('/dv/vouchers', body)
  return data
}

export async function issueVoucher(id: string) {
  const { data } = await http.post(`/dv/vouchers/${id}/issue`)
  return data
}

export async function transferVoucher(id: string, body: { to_holder_id: string; remark?: string }) {
  const { data } = await http.post(`/dv/vouchers/${id}/transfer`, body)
  return data
}

export async function splitVoucher(id: string, body: { amount: string; to_holder_id?: string; remark?: string }) {
  const { data } = await http.post(`/dv/vouchers/${id}/split`, body)
  return data
}

export async function redeemVoucher(id: string, body: { remark?: string }) {
  const { data } = await http.post(`/dv/vouchers/${id}/redeem-apply`, body)
  return data
}

export async function cancelVoucher(id: string) {
  const { data } = await http.post(`/dv/vouchers/${id}/cancel`)
  return data
}
