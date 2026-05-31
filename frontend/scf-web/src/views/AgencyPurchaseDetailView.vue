<template>
  <div v-loading="loading">
    <div class="toolbar">
      <h2>代采申请详情</h2>
      <div class="actions">
        <el-button @click="goBack">返回列表</el-button>
        <el-button
          v-if="canCreate && app && isDraftStatus(app.application_status)"
          type="warning"
          @click="goEdit"
        >编辑</el-button>
        <el-button
          v-if="canSubmit && app && isDraftStatus(app.application_status)"
          type="primary"
          @click="onSubmit"
        >提交</el-button>
        <el-button
          v-if="canCancel && app && isCancellableStatus(app.application_status)"
          type="danger"
          @click="onCancel"
        >取消</el-button>
      </div>
    </div>

    <el-descriptions v-if="app" :column="2" border>
      <el-descriptions-item label="申请单号">{{ app.application_no }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        {{ agencyPurchaseStatusLabel(meta, app.application_status) }}
      </el-descriptions-item>
      <el-descriptions-item label="代采模式" :span="2">
        {{ agencyPurchaseModeLabel(meta, app.mode_key) }}
      </el-descriptions-item>
      <el-descriptions-item label="订单/备货">
        {{ agencyPurchaseOrderModeLabel(meta, app.order_mode) }}
      </el-descriptions-item>
      <el-descriptions-item label="资金来源">
        {{ agencyPurchaseFundSourceLabel(meta, app.fund_source) }}
      </el-descriptions-item>
      <el-descriptions-item label="提货方式">
        {{ agencyPurchasePickupTypeLabel(meta, app.pickup_type) }}
      </el-descriptions-item>
      <el-descriptions-item label="客户 ID">{{ app.customer_id }}</el-descriptions-item>
      <el-descriptions-item label="贸易公司 ID">{{ app.trade_company_id }}</el-descriptions-item>
      <el-descriptions-item label="关联订单 ID">{{ app.order_id || '-' }}</el-descriptions-item>
      <el-descriptions-item label="金额">{{ app.total_amount }} {{ app.currency }}</el-descriptions-item>
      <el-descriptions-item label="BPM 实例">{{ app.bpm_instance_id || '未启动' }}</el-descriptions-item>
      <el-descriptions-item label="Saga 状态">
        <el-tag :type="sagaStatusTagType(app.saga_status)" size="small">
          {{ agencyPurchaseSagaStatusLabel(app.saga_status) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item v-if="app.margin_account_id" label="保证金账户">
        {{ app.margin_account_id }}
      </el-descriptions-item>
      <el-descriptions-item v-if="app.margin_frozen_amount" label="已冻保证金">
        {{ app.margin_frozen_amount }} {{ app.currency }}
      </el-descriptions-item>
      <el-descriptions-item v-if="app.inventory_id" label="库存 ID">
        {{ app.inventory_id }}
      </el-descriptions-item>
      <el-descriptions-item v-if="app.finance_application_id" label="融资申请 ID">
        {{ app.finance_application_id }}
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ app.created_at }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ app.remark || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="app.saga_last_error" label="Saga 错误" :span="2">
        <span class="saga-error">{{ app.saga_last_error }}</span>
      </el-descriptions-item>
    </el-descriptions>

    <template v-if="app && showSagaTimeline">
      <el-divider content-position="left">跨域 Saga 时间线</el-divider>
      <el-steps :active="sagaActiveStep" finish-status="success" align-center class="saga-steps">
        <el-step
          v-for="step in orderedSagaSteps"
          :key="step.step_code"
          :title="agencyPurchaseSagaStepLabel(step.step_code)"
          :status="elStepStatus(step.step_status)"
        >
          <template #description>
            <div class="step-desc">
              <el-tag :type="sagaStepTagType(step.step_status)" size="small">
                {{ agencyPurchaseSagaStepStatusLabel(step.step_status) }}
              </el-tag>
              <span v-if="step.executed_at" class="step-time">{{ step.executed_at }}</span>
              <span v-if="step.detail_json" class="step-detail">{{ step.detail_json }}</span>
            </div>
          </template>
        </el-step>
      </el-steps>
      <el-alert
        v-if="app.saga_status === 'FAILED'"
        type="error"
        :closable="false"
        show-icon
        title="Saga 执行失败，已记录失败步骤；若已完成步骤涉及资金/库存冻结，系统将写入补偿任务队列。"
        style="margin-top: 12px"
      />
    </template>

    <template v-else-if="app && app.application_status === 'APPROVED'">
      <el-divider content-position="left">跨域 Saga 时间线</el-divider>
      <el-alert type="info" :closable="false" show-icon title="审批已通过，Saga 尚未启动或正在等待 Outbox 投递。"       />
    </template>

    <template v-if="app?.compensation_tasks?.length">
      <el-divider content-position="left">补偿任务</el-divider>
      <el-table :data="app.compensation_tasks" size="small" stripe>
        <el-table-column label="类型" min-width="140">
          <template #default="{ row }">
            {{ agencyPurchaseCompensationTypeLabel(row.compensation_type) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="compensationStatusTagType(row.compensation_status)" size="small">
              {{ agencyPurchaseCompensationStatusLabel(row.compensation_status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="created_at" label="创建时间" width="180" />
        <el-table-column prop="executed_at" label="执行时间" width="180" />
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAgencyPurchaseApplication,
  getAgencyPurchaseApplicationDetail,
  submitAgencyPurchaseApplication,
  type AgencyPurchaseApplication,
  type AgencyPurchaseMeta,
  type AgencyPurchaseSagaStep
} from '../api/agencyPurchase'
import {
  agencyPurchaseFundSourceLabel,
  agencyPurchaseModeLabel,
  agencyPurchaseOrderModeLabel,
  agencyPurchasePickupTypeLabel,
  agencyPurchaseSagaStatusLabel,
  agencyPurchaseSagaStepLabel,
  agencyPurchaseSagaStepStatusLabel,
  agencyPurchaseCompensationTypeLabel,
  agencyPurchaseCompensationStatusLabel,
  agencyPurchaseStatusLabel,
  isCancellableStatus,
  isDraftStatus,
  loadAgencyPurchaseMeta,
  sagaStatusTagType,
  sagaStepTagType,
  compensationStatusTagType
} from '../constants/agencyPurchaseDict'
import { usePermission } from '../composables/usePermission'

const SAGA_STEP_ORDER = ['ORDER_CONFIRM', 'MARGIN_FREEZE', 'INVENTORY_FREEZE', 'FINANCE_CREATE']

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()
const canCreate = computed(() => hasPermission('AGENCY_PURCHASE_CREATE'))
const canSubmit = computed(() => hasPermission('AGENCY_PURCHASE_SUBMIT'))
const canCancel = computed(() => hasPermission('AGENCY_PURCHASE_CANCEL'))

const loading = ref(false)
const meta = ref<AgencyPurchaseMeta | null>(null)
const app = ref<AgencyPurchaseApplication | null>(null)
const applicationId = computed(() => route.params.id as string)

const showSagaTimeline = computed(
  () => !!app.value?.saga_steps?.length || !!app.value?.saga_status
)

const orderedSagaSteps = computed(() => {
  const steps = app.value?.saga_steps ?? []
  const byCode = new Map(steps.map((s) => [s.step_code, s]))
  return SAGA_STEP_ORDER.filter((code) => byCode.has(code)).map(
    (code) => byCode.get(code) as AgencyPurchaseSagaStep
  )
})

const sagaActiveStep = computed(() => {
  const steps = orderedSagaSteps.value
  const failedIdx = steps.findIndex((s) => s.step_status === 'FAILED')
  if (failedIdx >= 0) return failedIdx
  const successCount = steps.filter((s) => s.step_status === 'SUCCESS' || s.step_status === 'SKIPPED').length
  return app.value?.saga_status === 'SUCCESS' ? steps.length : successCount
})

function elStepStatus(stepStatus: string): '' | 'wait' | 'process' | 'finish' | 'error' | 'success' {
  if (stepStatus === 'SUCCESS' || stepStatus === 'SKIPPED') return 'success'
  if (stepStatus === 'FAILED') return 'error'
  return 'process'
}

async function load() {
  loading.value = true
  try {
    meta.value = await loadAgencyPurchaseMeta()
    const res = await getAgencyPurchaseApplicationDetail(applicationId.value)
    if (!res.success) throw new Error(res.message)
    app.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
    goBack()
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/agency-purchase/applications')
}

function goEdit() {
  router.push(`/agency-purchase/applications/${applicationId.value}/edit`)
}

async function onSubmit() {
  try {
    const res = await submitAgencyPurchaseApplication(applicationId.value)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('已提交，进入 BPM 待办')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确认取消该代采申请？', '提示', { type: 'warning' })
    const res = await cancelAgencyPurchaseApplication(applicationId.value)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('已取消')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '取消失败')
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.actions {
  display: flex;
  gap: 8px;
}
.saga-steps {
  margin: 8px 0 16px;
}
.step-desc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
}
.step-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.step-detail {
  font-size: 12px;
  color: var(--el-color-danger);
  max-width: 180px;
  word-break: break-all;
}
.saga-error {
  color: var(--el-color-danger);
}
</style>
