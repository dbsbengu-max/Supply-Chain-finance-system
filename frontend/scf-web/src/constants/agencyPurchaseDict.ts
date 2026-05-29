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
