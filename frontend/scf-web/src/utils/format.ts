/** Format decimal amount string for display (2 fractional digits). */
export function formatMoneyAmount(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = typeof value === 'string' ? Number(value) : value
  if (Number.isNaN(num)) return String(value)
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function formatMoney(
  value: string | number | null | undefined,
  currency?: string | null
): string {
  const amount = formatMoneyAmount(value)
  if (amount === '—') return amount
  return currency ? `${amount} ${currency}` : amount
}

export const MATCH_STATUS_LABELS: Record<string, string> = {
  UNMATCHED: '未匹配',
  MATCHED: '已匹配'
}

export const FLOW_TYPE_LABELS: Record<string, string> = {
  IN: '收入',
  OUT: '支出'
}

export function labelOf(map: Record<string, string>, code: string | null | undefined): string {
  if (!code) return '—'
  return map[code] ?? code
}

/** Validate amount string: positive number with up to 2 decimal places. */
export function isValidAmount(value: string): boolean {
  return /^\d+(\.\d{1,2})?$/.test(value.trim()) && Number(value) > 0
}
