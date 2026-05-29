import http from './http'

export interface BiOverview {
  as_of: string
  operator_id: string
  project_id: string
  currency: string
  order_count: number
  order_amount_total: string
  finance_count: number
  outstanding_finance_count: number
  outstanding_finance_amount: string
  disbursed_amount_total: string
  clearing_executed_count: number
  repaid_amount_total: string
  inventory_lot_count: number
  inventory_valuation_total: string
  voucher_finance_count: number
  risk_alert_count: number
}

export interface BiTrendPoint {
  period: string
  order_count: number
  amount_total: string
  currency: string
}

export interface BiStatusBucket {
  status: string
  count: number
  amount_total: string
  currency: string
}

export interface BiRiskAlertItem {
  code: string
  severity: string
  title: string
  message: string
  related_id: string
  related_type: string
}

export async function getBiOverview() {
  const res = await http.get('/bi/overview')
  return res.data
}

export async function getBiTradeTrend(months?: number) {
  const res = await http.get('/bi/trade-trend', { params: months ? { months } : undefined })
  return res.data
}

export async function getBiFinanceSummary() {
  const res = await http.get('/bi/finance-summary')
  return res.data
}

export async function getBiWarehouseSummary() {
  const res = await http.get('/bi/warehouse-summary')
  return res.data
}

export async function getBiClearingSummary() {
  const res = await http.get('/bi/clearing-summary')
  return res.data
}

export async function getBiRiskAlerts() {
  const res = await http.get('/bi/risk-alerts')
  return res.data
}

export async function exportBiDashboard() {
  const res = await http.post('/bi/dashboard/export')
  return res.data
}
