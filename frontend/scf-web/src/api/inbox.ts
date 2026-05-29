import http from './http'

export interface InboxEvent {
  event_key: string
  source: string
  category: string
  severity: string
  title: string
  message: string
  business_type: string
  business_id: string
  business_label?: string
  action_route?: string
  occurred_at: string
  read: boolean
  metadata?: Record<string, string>
}

export interface InboxSummary {
  total: number
  unread_count: number
  by_source: Record<string, number>
}

export interface InboxFeed {
  summary: InboxSummary
  events: InboxEvent[]
}

export async function getInboxFeed(params?: {
  source?: string
  unread_only?: boolean
  limit?: number
}) {
  const res = await http.get('/inbox/feed', { params })
  return res.data
}

export async function markInboxEventRead(eventKey: string) {
  const res = await http.patch('/inbox/events/read', null, { params: { event_key: eventKey } })
  return res.data
}

export const INBOX_SOURCE_LABELS: Record<string, string> = {
  BPM: 'BPM 审批',
  RISK: '风险预警',
  CLEARING: '清分审批',
  DISBURSE: '放款确认',
  WAREHOUSE: '仓储异常'
}

export const INBOX_CATEGORY_LABELS: Record<string, string> = {
  TODO: '待办',
  ALERT: '告警',
  APPROVAL: '审批',
  CONFIRM: '确认',
  EXCEPTION: '异常'
}
