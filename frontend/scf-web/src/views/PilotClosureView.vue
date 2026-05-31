<template>
  <div class="pilot-closure">
    <div class="page-header">
      <div>
        <h2>试点闭环向导</h2>
        <p class="subtitle">端到端主链路：客户/KYC → 代采 → BPM/Saga → 融资 → 放款 → 凭证 → 还款/清分 → 释放/兑付 → BI/审计</p>
      </div>
      <el-button type="primary" @click="router.push('/saga/ops')">Saga 监控台</el-button>
    </div>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="试点说明"
      description="按顺序完成各阶段操作；Saga/补偿异常可在 Saga 监控台查看详情、填写人工原因后重试。上线前请对照 EA-029 UAT 清单逐项验收。"
      class="hint"
    />

    <el-card v-if="agencyContext" v-loading="agencyLoading" shadow="never" class="agency-context">
      <template #header>
        <span>当前试点单：{{ agencyContext.application_no }}</span>
        <el-button link type="primary" @click="router.push(`/agency-purchase/applications/${agencyContext.id}`)">
          代采详情 →
        </el-button>
      </template>
      <PilotClosureTimeline :nodes="agencyTimeline" />
    </el-card>

    <el-steps :active="activeStep" finish-status="success" align-center class="steps">
      <el-step v-for="step in steps" :key="step.key" :title="step.title" />
    </el-steps>

    <el-row :gutter="16" class="step-cards">
      <el-col v-for="(step, idx) in steps" :key="step.key" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card shadow="hover" class="step-card" :class="{ done: idx < activeStep }">
          <div class="step-num">{{ idx + 1 }}</div>
          <h3>{{ step.title }}</h3>
          <p>{{ step.desc }}</p>
          <ul v-if="step.checks?.length" class="checks">
            <li v-for="c in step.checks" :key="c">{{ c }}</li>
          </ul>
          <el-button
            v-if="canAccess(step)"
            type="primary"
            link
            @click="router.push(step.path)"
          >
            进入 →
          </el-button>
          <el-tag v-else size="small" type="info">无权限</el-tag>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="trace-card">
      <template #header>链路追踪要点</template>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="代采 Saga">Outbox 事件 + 补偿任务可在 Saga 监控台按业务 ID 过滤</el-descriptions-item>
        <el-descriptions-item label="融资/放款">融资单状态与银行流水匹配见融资管理、银行流水</el-descriptions-item>
        <el-descriptions-item label="凭证生命周期">签发 → 持有 → 还款释放 → 兑付，见数字凭证详情</el-descriptions-item>
        <el-descriptions-item label="清分">清分试算/执行与规则配置联动</el-descriptions-item>
        <el-descriptions-item label="审计/BI">审计日志检索 Saga 人工操作；经营看板核对放款/在贷指标</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAgencyPurchaseApplicationDetail, type AgencyPurchaseApplication } from '../api/agencyPurchase'
import { getFinanceApplication, type FinanceApplication } from '../api/finance'
import PilotClosureTimeline from '../components/PilotClosureTimeline.vue'
import { usePermission } from '../composables/usePermission'
import { buildAgencyClosureTimeline } from '../utils/pilotClosureTimeline'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const agencyLoading = ref(false)
const agencyContext = ref<AgencyPurchaseApplication | null>(null)
const agencyFinance = ref<FinanceApplication | null>(null)
const agencyTimeline = computed(() =>
  agencyContext.value ? buildAgencyClosureTimeline(agencyContext.value, agencyFinance.value) : []
)

async function loadAgencyContext() {
  const id = route.query.agency_id as string | undefined
  if (!id) return
  agencyLoading.value = true
  try {
    const res = await getAgencyPurchaseApplicationDetail(id)
    if (!res.success || !res.data) throw new Error(res.message || '加载代采单失败')
    agencyContext.value = res.data
    const fid = res.data.finance_application_id
    if (fid) {
      const fr = await getFinanceApplication(fid)
      if (fr.success && fr.data) agencyFinance.value = fr.data
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载试点单上下文失败')
  } finally {
    agencyLoading.value = false
  }
}

onMounted(loadAgencyContext)

interface PilotStep {
  key: string
  title: string
  desc: string
  path: string
  permission?: string | string[]
  checks?: string[]
}

const steps: PilotStep[] = [
  {
    key: 'kyc',
    title: '客户/KYC',
    desc: '维护企业主体与 KYC 资料',
    path: '/customers',
    checks: ['企业建档', 'KYC 状态有效']
  },
  {
    key: 'agency',
    title: '代采申请',
    desc: '创建贸易代采单并提交审批',
    path: '/agency-purchase/applications',
    checks: ['保证金冻结', '库存冻结']
  },
  {
    key: 'bpm',
    title: 'BPM / Saga',
    desc: '审批通过后 Outbox 驱动下游',
    path: '/saga/ops',
    permission: 'SAGA_OPS_VIEW',
    checks: ['Outbox SUCCESS', '无 FAILED 积压']
  },
  {
    key: 'finance',
    title: '融资申请',
    desc: '基于代采/贸易发起融资',
    path: '/finance/applications',
    permission: 'FINANCE_VIEW',
    checks: ['融资单创建', '审批通过']
  },
  {
    key: 'disburse',
    title: '放款',
    desc: '放款指令与账户记账',
    path: '/finance/applications',
    permission: 'FINANCE_VIEW',
    checks: ['放款状态 DISBURSED', '虚拟账户余额']
  },
  {
    key: 'voucher',
    title: '数字凭证',
    desc: '凭证签发与持有',
    path: '/vouchers',
    permission: 'VOUCHER_VIEW',
    checks: ['凭证 ACTIVE', '面额与融资一致']
  },
  {
    key: 'repay',
    title: '还款/清分',
    desc: '还款入账与清分执行',
    path: '/accounts/clearing',
    permission: 'CLEARING_VIEW',
    checks: ['清分试算', '清分 EXECUTED']
  },
  {
    key: 'release',
    title: '释放/兑付',
    desc: '凭证释放或兑付完成',
    path: '/vouchers',
    permission: 'VOUCHER_VIEW',
    checks: ['RELEASED / REDEEMED', '库存/保证金解冻']
  },
  {
    key: 'bi',
    title: 'BI 看板',
    desc: '核对经营指标与试点数据',
    path: '/bi/dashboard',
    permission: 'BI_VIEW',
    checks: ['放款额', '在贷余额', '逾期率']
  },
  {
    key: 'audit',
    title: '审计',
    desc: '检索人工 Saga 操作与业务审计',
    path: '/audit/logs',
    permission: 'AUDIT_VIEW',
    checks: ['SAGA_* 操作有 manual_reason', '业务操作可追溯']
  }
]

function canAccess(step: PilotStep) {
  if (!step.permission) return true
  if (Array.isArray(step.permission)) {
    return step.permission.some((p) => hasPermission(p))
  }
  return hasPermission(step.permission)
}

/** 向导高亮步进：有试点单上下文时按时间线进度，否则按权限 */
const activeStep = computed(() => {
  if (agencyTimeline.value.length) {
    const idx = agencyTimeline.value.findIndex((n) => n.type === 'warning' || n.type === 'info')
    if (idx >= 0) return idx
    return Math.min(agencyTimeline.value.length - 1, steps.length - 1)
  }
  let last = 0
  steps.forEach((step, idx) => {
    if (canAccess(step)) last = idx + 1
  })
  return Math.min(last, steps.length - 1)
})
</script>

<style scoped>
.pilot-closure {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.subtitle {
  margin: 4px 0 0;
  color: #666;
  font-size: 13px;
  max-width: 720px;
}
.hint {
  margin-bottom: 0;
}
.steps {
  margin: 8px 0;
}
.step-cards {
  margin-top: 8px;
}
.step-card {
  min-height: 200px;
  margin-bottom: 16px;
}
.step-card.done {
  border-color: #67c23a;
}
.step-num {
  width: 28px;
  height: 28px;
  line-height: 28px;
  text-align: center;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-weight: 600;
  margin-bottom: 8px;
}
.step-card h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
.step-card p {
  margin: 0 0 8px;
  color: #666;
  font-size: 13px;
}
.checks {
  margin: 0 0 12px;
  padding-left: 18px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.trace-card {
  margin-top: 8px;
}
.agency-context :deep(.el-card__header) {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
