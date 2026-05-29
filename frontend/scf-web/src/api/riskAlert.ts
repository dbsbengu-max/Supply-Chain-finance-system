import http from './http'

export interface RiskAlertItem {
  id: string
  alert_code: string
  severity: string
  title: string
  message: string
  related_id: string
  related_type: string
  related_label?: string
  amount?: string
  currency?: string
  handle_status: string
  assignee_user_id?: string
  assignee_name?: string
  remark?: string
  detected_at: string
  handled_at?: string
  updated_at?: string
  related_route?: string
}

export async function listRiskAlerts(params?: {
  page_no?: number
  page_size?: number
  alert_code?: string
  severity?: string
  handle_status?: string
  assignee_user_id?: string
}) {
  const res = await http.get('/risk/alerts', { params })
  return res.data
}

export async function getRiskAlert(id: string) {
  const res = await http.get(`/risk/alerts/${id}`)
  return res.data
}

export async function handleRiskAlert(
  id: string,
  body: {
    handle_status: string
    assignee_user_id?: string
    assignee_name?: string
    remark?: string
  }
) {
  const res = await http.patch(`/risk/alerts/${id}`, body)
  return res.data
}

export async function claimRiskAlert(id: string) {
  const res = await http.post(`/risk/alerts/${id}/claim`)
  return res.data
}
