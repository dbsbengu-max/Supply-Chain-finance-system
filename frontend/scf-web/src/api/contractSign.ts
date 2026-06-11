import http from './http'

export interface ContractSignProviderInfo {
  provider_code: string
  display_name: string
  description: string
  supports_status_query: boolean
}

export interface ContractSignProviderConnection {
  provider_code: string
  enabled: boolean
  configured: boolean
  outbound_auth_mode: string
  platform_trace_header: string
  base_url: string
  app_id: string
  app_secret_masked: string
}

export interface ContractSignConfig {
  default_provider: string
  max_retry_count: number
  callback_verification_mode: string
  callback_signature_window_seconds: number
  callback_token_masked: string
  callback_path: string
  callback_headers: string[]
  planned_callback_headers: string[]
  compensation_pool_enabled: boolean
  provider_connections: ContractSignProviderConnection[]
}

export function getContractSignConfig() {
  return http.get<{ data: ContractSignConfig }>('/integrations/contracts/sign/config')
}

export function listContractSignProviders() {
  return http.get<{ data: ContractSignProviderInfo[] }>('/integrations/contracts/sign/providers')
}

export interface ContractSignTaskSummary {
  id?: string
  document_id?: string
  external_sign_ref?: string
  provider_code?: string
  task_status?: string
  callback_status?: string
}

export interface ContractSignDocumentSummary {
  document_id?: string
  sign_status?: string
  contract_status?: string
}

export interface ContractSignLookupResult {
  external_sign_ref?: string
  task?: ContractSignTaskSummary
  document?: ContractSignDocumentSummary
}

export interface ContractSignStatusQueryResult {
  external_sign_ref?: string
  provider?: {
    provider_code?: string
    provider_status?: string
    signed_at?: string
    failure_reason?: string
  }
  reconciled?: boolean
  reconcile_action?: string
  message?: string
  local_task?: ContractSignTaskSummary
  document?: ContractSignDocumentSummary
}

export function lookupContractSignByRef(externalSignRef: string) {
  return http.get<{ data: ContractSignLookupResult }>(
    `/integrations/contracts/sign/by-ref/${encodeURIComponent(externalSignRef)}`
  )
}

export function queryContractSignStatus(
  externalSignRef: string,
  payload?: { reconcile?: boolean; reason?: string }
) {
  return http.post<{ data: ContractSignStatusQueryResult }>(
    `/integrations/contracts/sign/by-ref/${encodeURIComponent(externalSignRef)}/query-status`,
    payload ?? {}
  )
}
