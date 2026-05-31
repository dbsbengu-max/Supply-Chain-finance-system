const FINANCE_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  APPROVED: '已审批',
  TO_DISBURSE: '待放款',
  DISBURSED: '已放款',
  REPAYING: '还款中',
  OVERDUE: '逾期',
  SETTLED: '已结清',
  CANCELLED: '已取消',
  REJECTED: '已拒绝'
}

export function financeStatusLabel(code: string | undefined) {
  if (!code) return '—'
  return FINANCE_STATUS_LABELS[code] ?? code
}

export function financeStatusTagType(status: string | undefined): 'success' | 'danger' | 'info' | 'warning' {
  if (!status) return 'info'
  if (status === 'DISBURSED' || status === 'SETTLED' || status === 'APPROVED') return 'success'
  if (status === 'OVERDUE' || status === 'REJECTED' || status === 'CANCELLED') return 'danger'
  if (status === 'DRAFT' || status === 'SUBMITTED' || status === 'TO_DISBURSE' || status === 'REPAYING') {
    return 'warning'
  }
  return 'info'
}

export const FINANCE_REPAYABLE_STATUSES = new Set(['DISBURSED', 'REPAYING', 'OVERDUE'])
