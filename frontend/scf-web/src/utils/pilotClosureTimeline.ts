import type { AgencyPurchaseApplication } from '../api/agencyPurchase'
import type { FinanceApplication } from '../api/finance'
import { financeStatusLabel } from '../constants/financeDict'
import { agencyPurchaseSagaStatusLabel } from '../constants/agencyPurchaseDict'

export type ClosureTimelineType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

export interface ClosureTimelineNode {
  key: string
  title: string
  type: ClosureTimelineType
  description: string
  timestamp?: string
  route?: string
  routeLabel?: string
}

function bpmNode(app: AgencyPurchaseApplication): ClosureTimelineNode {
  const st = app.application_status
  let type: ClosureTimelineType = 'info'
  let desc = '草稿或未提交'
  if (st === 'SUBMITTED' || st === 'REVIEWING') {
    type = 'warning'
    desc = app.bpm_instance_id ? `BPM 实例 ${app.bpm_instance_id}` : '审批进行中'
  } else if (st === 'APPROVED') {
    type = 'success'
    desc = '审批已通过'
  } else if (st === 'REJECTED' || st === 'CANCELLED') {
    type = 'danger'
    desc = st === 'REJECTED' ? '审批拒绝' : '申请已取消'
  } else if (st !== 'DRAFT') {
    type = 'success'
    desc = st
  }
  return {
    key: 'bpm',
    title: 'BPM 审批',
    type,
    description: desc,
    timestamp: app.updated_at
  }
}

function sagaNode(app: AgencyPurchaseApplication): ClosureTimelineNode {
  const st = app.saga_status
  if (!st) {
    return {
      key: 'saga',
      title: '跨域 Saga',
      type: app.application_status === 'APPROVED' ? 'warning' : 'info',
      description:
        app.application_status === 'APPROVED'
          ? '审批已通过，等待 Outbox 启动 Saga'
          : '审批通过后启动',
      route: app.id ? `/saga/ops?tab=compensation&business_id=${app.id}` : undefined,
      routeLabel: 'Saga 监控'
    }
  }
  const type: ClosureTimelineType =
    st === 'SUCCESS' ? 'success' : st === 'FAILED' ? 'danger' : 'warning'
  return {
    key: 'saga',
    title: '跨域 Saga',
    type,
    description: app.saga_last_error
      ? `${agencyPurchaseSagaStatusLabel(st)} — ${app.saga_last_error}`
      : agencyPurchaseSagaStatusLabel(st),
    route: `/saga/ops?tab=compensation&business_id=${app.id}`,
    routeLabel: 'Saga 监控'
  }
}

function financeNode(app: AgencyPurchaseApplication, finance?: FinanceApplication | null): ClosureTimelineNode {
  const fid = app.finance_application_id || finance?.id
  if (!fid) {
    return {
      key: 'finance',
      title: '融资申请',
      type: 'info',
      description: 'Saga 完成后自动创建或手工发起'
    }
  }
  const st = finance?.finance_status
  const type: ClosureTimelineType = !st
    ? 'warning'
    : st === 'DISBURSED' || st === 'REPAYING' || st === 'SETTLED'
      ? 'success'
      : st === 'REJECTED' || st === 'CANCELLED'
        ? 'danger'
        : 'warning'
  return {
    key: 'finance',
    title: '融资 / 放款',
    type,
    description: st
      ? `${finance?.finance_no ?? fid} · ${financeStatusLabel(st)}`
      : `融资单 ${fid}`,
    route: `/finance/applications?highlight=${fid}`,
    routeLabel: '融资管理'
  }
}

function downstreamNode(finance?: FinanceApplication | null): ClosureTimelineNode[] {
  const fid = finance?.id
  const st = finance?.finance_status
  const canClear = st && ['DISBURSED', 'REPAYING', 'OVERDUE', 'SETTLED'].includes(st)
  return [
    {
      key: 'voucher',
      title: '数字凭证',
      type: canClear ? 'success' : 'info',
      description: canClear ? '放款后可签发/持有凭证' : '依赖融资放款',
      route: '/vouchers',
      routeLabel: '凭证列表'
    },
    {
      key: 'clearing',
      title: '还款 / 清分',
      type: canClear && st !== 'SETTLED' ? 'warning' : st === 'SETTLED' ? 'success' : 'info',
      description:
        st === 'SETTLED'
          ? '融资已结清'
          : canClear
            ? '可进入清分中心试算与执行'
            : '需已放款且匹配银行流水',
      route: fid ? `/accounts/clearing?finance_id=${fid}` : '/accounts/clearing',
      routeLabel: '清分中心'
    },
    {
      key: 'bi',
      title: 'BI / 审计',
      type: 'primary',
      description: '核对放款、在贷与 Saga 人工操作审计',
      route: '/bi/dashboard?from=pilot',
      routeLabel: '经营看板'
    }
  ]
}

export function buildAgencyClosureTimeline(
  app: AgencyPurchaseApplication,
  finance?: FinanceApplication | null
): ClosureTimelineNode[] {
  const createType: ClosureTimelineType = app.application_status === 'DRAFT' ? 'info' : 'success'
  return [
    {
      key: 'create',
      title: '代采申请',
      type: createType,
      description: `${app.application_no} · ${app.total_amount} ${app.currency}`,
      timestamp: app.created_at,
      route: `/agency-purchase/applications/${app.id}`,
      routeLabel: '当前详情'
    },
    bpmNode(app),
    sagaNode(app),
    financeNode(app, finance),
    ...downstreamNode(finance)
  ]
}
