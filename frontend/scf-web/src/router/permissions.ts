/** Route name → required permission code (any one if array). */
export const ROUTE_PERMISSIONS: Record<string, string | string[]> = {
  'bank-flow-list': 'ACCOUNT_FLOW_VIEW',
  'clearing-workbench': 'CLEARING_VIEW',
  'clearing-rule-list': 'CLEARING_RULE_LIST',
  'finance-applications': 'FINANCE_VIEW',
  'warehouse-list': 'WAREHOUSE_VIEW',
  'warehouse-detail': 'WAREHOUSE_VIEW',
  'inventory-list': 'WAREHOUSE_VIEW',
  'inventory-detail': 'WAREHOUSE_VIEW',
  'agency-purchase-list': 'AGENCY_PURCHASE_VIEW',
  'agency-purchase-new': 'AGENCY_PURCHASE_CREATE',
  'agency-purchase-edit': 'AGENCY_PURCHASE_CREATE',
  'agency-purchase-detail': 'AGENCY_PURCHASE_VIEW',
  'ai-ocr': 'AI_OCR_VIEW',
  'excel-import': 'EXCEL_IMPORT',
  'bi-dashboard': 'BI_VIEW',
  'risk-alerts': 'RISK_ALERT_VIEW',
  'inbox-center': 'INBOX_VIEW',
  'audit-center': 'AUDIT_VIEW',
  'saga-ops': 'SAGA_OPS_VIEW',
  'document-center': 'DOCUMENT_VIEW',
  'contract-sign-config': 'CONTRACT_SIGN_CONFIG_VIEW',
  'voucher-list': 'VOUCHER_VIEW',
  'voucher-detail': 'VOUCHER_VIEW'
}

export function routeRequiresPermission(name: string | symbol | null | undefined): string | string[] | null {
  if (!name || typeof name !== 'string') return null
  return ROUTE_PERMISSIONS[name] ?? null
}

export function hasRoutePermission(
  permissions: string[],
  required: string | string[] | null
): boolean {
  if (!required) return true
  const codes = Array.isArray(required) ? required : [required]
  return codes.some((code) => permissions.includes(code))
}
