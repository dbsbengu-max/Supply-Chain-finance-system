import type { AgencyPurchaseMeta, DictItem } from '../api/agencyPurchase'
import { getAgencyPurchaseMeta } from '../api/agencyPurchase'

let cachedMeta: AgencyPurchaseMeta | null = null

export async function loadAgencyPurchaseMeta(force = false): Promise<AgencyPurchaseMeta> {
  if (cachedMeta && !force) {
    return cachedMeta
  }
  const res = await getAgencyPurchaseMeta()
  if (!res.success || !res.data) {
    throw new Error(res.message || '加载代采字典失败')
  }
  cachedMeta = res.data as AgencyPurchaseMeta
  return cachedMeta
}

function labelOf(items: DictItem[] | undefined, code: string | undefined): string {
  if (!code) return '-'
  return items?.find((i) => i.code === code)?.label ?? code
}

export function agencyPurchaseStatusLabel(meta: AgencyPurchaseMeta | null, code: string) {
  return labelOf(meta?.application_statuses, code)
}

export function agencyPurchaseOrderModeLabel(meta: AgencyPurchaseMeta | null, code: string) {
  return labelOf(meta?.order_modes, code)
}

export function agencyPurchaseFundSourceLabel(meta: AgencyPurchaseMeta | null, code: string) {
  return labelOf(meta?.fund_sources, code)
}

export function agencyPurchasePickupTypeLabel(meta: AgencyPurchaseMeta | null, code: string) {
  return labelOf(meta?.pickup_types, code)
}

export function agencyPurchaseModeLabel(meta: AgencyPurchaseMeta | null, modeKey: string) {
  if (!modeKey) return '-'
  return meta?.valid_modes.find((m) => m.mode_key === modeKey)?.label ?? modeKey
}

export function filterValidModes(
  meta: AgencyPurchaseMeta | null,
  orderMode?: string,
  fundSource?: string
) {
  if (!meta) return []
  return meta.valid_modes.filter((m) => {
    if (orderMode && m.order_mode !== orderMode) return false
    if (fundSource && m.fund_source !== fundSource) return false
    return true
  })
}

export function isValidModeCombination(
  meta: AgencyPurchaseMeta | null,
  orderMode: string,
  fundSource: string,
  pickupType: string
) {
  return !!meta?.valid_modes.some(
    (m) =>
      m.order_mode === orderMode &&
      m.fund_source === fundSource &&
      m.pickup_type === pickupType
  )
}

export function isDraftStatus(status: string) {
  return status === 'DRAFT'
}

export function isCancellableStatus(status: string) {
  return status === 'DRAFT' || status === 'SUBMITTED' || status === 'REVIEWING'
}

const SAGA_STEP_LABELS: Record<string, string> = {
  ORDER_CONFIRM: '订单确认',
  MARGIN_FREEZE: '保证金冻结',
  INVENTORY_FREEZE: '库存冻结',
  FINANCE_CREATE: '融资申请创建'
}

const SAGA_STATUS_LABELS: Record<string, string> = {
  RUNNING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败'
}

const SAGA_STEP_STATUS_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '跳过'
}

const COMPENSATION_TYPE_LABELS: Record<string, string> = {
  ORDER_ROLLBACK: '订单回滚',
  MARGIN_UNFREEZE: '保证金解冻',
  INVENTORY_UNFREEZE: '库存解冻',
  CONTRACT_SIGN_CALLBACK_REVIEW: '签章回调复核'
}

const COMPENSATION_STATUS_LABELS: Record<string, string> = {
  PENDING: '待执行',
  PROCESSING: '执行中',
  RETRYING: '重试中',
  SUCCESS: '成功',
  FAILED: '失败',
  MANUAL_REQUIRED: '待人工',
  CLAIMED: '已认领',
  APPROVED: '已审批',
  IGNORED: '已忽略',
  CLOSED: '已关闭'
}

export function agencyPurchaseSagaStatusLabel(code: string | undefined) {
  if (!code) return '未启动'
  return SAGA_STATUS_LABELS[code] ?? code
}

export function agencyPurchaseSagaStepLabel(stepCode: string) {
  return SAGA_STEP_LABELS[stepCode] ?? stepCode
}

export function agencyPurchaseSagaStepStatusLabel(status: string) {
  return SAGA_STEP_STATUS_LABELS[status] ?? status
}

export function sagaStepTagType(status: string): 'success' | 'danger' | 'info' | 'warning' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'SKIPPED') return 'info'
  return 'warning'
}

export function sagaStatusTagType(status: string | undefined): 'success' | 'danger' | 'info' | 'warning' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}

export function agencyPurchaseCompensationTypeLabel(code: string) {
  return COMPENSATION_TYPE_LABELS[code] ?? code
}

export function agencyPurchaseCompensationStatusLabel(code: string) {
  return COMPENSATION_STATUS_LABELS[code] ?? code
}

export function compensationStatusTagType(status: string): 'success' | 'danger' | 'info' | 'warning' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'MANUAL_REQUIRED') return 'danger'
  if (status === 'IGNORED' || status === 'CLOSED') return 'info'
  if (status === 'CLAIMED' || status === 'APPROVED') return 'warning'
  if (status === 'PENDING' || status === 'PROCESSING' || status === 'RETRYING') return 'warning'
  return 'info'
}
