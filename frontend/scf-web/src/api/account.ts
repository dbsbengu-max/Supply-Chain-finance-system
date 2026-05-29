import http from './http'

export interface BankFlow {
  id: string
  account_id: string
  external_flow_no: string
  flow_type: string
  amount: string
  currency: string
  counterparty_name?: string
  counterparty_account?: string
  flow_time?: string
  match_status: string
  source_type?: string
  source_id?: string
}

export interface ClearingRuleOption {
  id: string
  rule_name: string
  product_type: string
}

export interface AccountBalanceSummary {
  id: string
  account_type: string
  account_no: string
  account_name: string
  currency: string
  balance: string
  frozen_balance: string
  available_balance: string
  enterprise_id?: string
  funding_party_id?: string
}

export interface ClearingEntry {
  finance_id?: string
  finance_no?: string
  finance_status?: string
  outstanding_principal?: string
  currency?: string
  unmatched_flows: BankFlow[]
  clearing_rules: ClearingRuleOption[]
}

export interface ClearingAllocation {
  penalty_amount: string
  fee_amount: string
  interest_amount: string
  principal_amount: string
  remaining_amount: string
}

export interface ClearingCalculateResult {
  finance_id: string
  bank_flow_id: string
  clearing_rule_id: string
  repayment_amount: string
  currency: string
  allocation: ClearingAllocation
  warnings?: string[]
}

export interface ClearingExecuteResult extends ClearingCalculateResult {
  repayment_id: string
  clearing_result_id: string
  finance_status: string
  created_at?: string
  idempotent_replay?: boolean
}

export const SECONDARY_AUTH_MOCK = 'MOCK-APPROVED'

export function newIdempotencyKey() {
  return crypto.randomUUID()
}

export async function getAccountSummary() {
  const res = await http.get('/accounts/summary')
  return res.data
}

export async function listBankFlows(params?: {
  page_no?: number
  page_size?: number
  match_status?: string
  flow_type?: string
  account_id?: string
}) {
  const res = await http.get('/accounts/bank-flows', { params })
  return res.data
}

export async function importBankFlows(body: {
  flows: Array<{
    account_id: string
    external_flow_no: string
    amount: string
    currency: string
    counterparty_name?: string
    counterparty_account?: string
    flow_time: string
  }>
}) {
  const res = await http.post('/accounts/bank-flows/import', body)
  return res.data
}

export async function matchBankFlow(flowId: string, financeId: string) {
  const res = await http.post(`/accounts/bank-flows/${flowId}/match`, { finance_id: financeId })
  return res.data
}

export async function getClearingEntry(financeId?: string) {
  const res = await http.get('/accounts/clearing/entry', {
    params: financeId ? { finance_id: financeId } : undefined
  })
  return res.data
}

export async function calculateClearing(body: {
  finance_id: string
  bank_flow_id: string
  clearing_rule_id: string
}) {
  const res = await http.post('/accounts/clearing/calculate', body)
  return res.data
}

export async function executeClearing(
  body: {
    finance_id: string
    bank_flow_id: string
    clearing_rule_id: string
  },
  idempotencyKey: string,
  secondaryAuthToken: string = SECONDARY_AUTH_MOCK
) {
  const res = await http.post('/accounts/clearing/execute', body, {
    headers: {
      'X-Idempotency-Key': idempotencyKey,
      'X-Secondary-Auth-Token': secondaryAuthToken
    }
  })
  return res.data
}

export interface ClearingRule {
  id: string
  operator_id: string
  project_id: string
  funding_party_id?: string
  product_type: string
  rule_name: string
  priority_json: string
  fee_formula_json?: string
  currency_rule: string
  effective_from: string
  effective_to?: string
  review_status: string
  version_no: number
}

export const DEFAULT_PRIORITY_JSON = JSON.stringify(
  { priority: ['penalty', 'fee', 'interest', 'principal', 'platform_fee', 'residual'] },
  null,
  2
)

export async function listClearingRules(params?: {
  page_no?: number
  page_size?: number
  product_type?: string
  review_status?: string
}) {
  const res = await http.get('/accounts/clearing-rules', { params })
  return res.data
}

export async function getClearingRule(id: string) {
  const res = await http.get(`/accounts/clearing-rules/${id}`)
  return res.data
}

export async function createClearingRule(body: {
  funding_party_id?: string
  product_type: string
  rule_name: string
  priority_json: string
  fee_formula_json?: string
  currency_rule: string
  effective_from: string
  effective_to?: string
}) {
  const res = await http.post('/accounts/clearing-rules', body)
  return res.data
}

export async function updateClearingRule(
  id: string,
  body: {
    funding_party_id?: string
    product_type: string
    rule_name: string
    priority_json: string
    fee_formula_json?: string
    currency_rule: string
    effective_from: string
    effective_to?: string
  }
) {
  const res = await http.put(`/accounts/clearing-rules/${id}`, body)
  return res.data
}

export async function submitClearingRule(id: string) {
  const res = await http.post(`/accounts/clearing-rules/${id}/submit`)
  return res.data
}

export async function approveClearingRule(id: string) {
  const res = await http.post(`/accounts/clearing-rules/${id}/approve`)
  return res.data
}
