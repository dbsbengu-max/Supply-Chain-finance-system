<template>
  <div>
    <div class="toolbar">
      <h2>融资管理</h2>
      <div class="toolbar-actions">
        <el-button @click="router.push('/pilot/closure')">试点闭环</el-button>
        <el-button type="primary" @click="openCreate">新建融资申请</el-button>
      </div>
    </div>

    <el-alert
      v-if="filterSourceId || filterHighlight"
      type="info"
      :closable="false"
      show-icon
      class="filter-banner"
      :title="filterBannerTitle"
    />

    <el-table
      :data="displayApplications"
      v-loading="loading"
      stripe
      :row-class-name="rowClassName"
    >
      <template #empty>
        <ListEmptyState
          description="暂无融资申请"
          action-label="打开试点闭环向导"
          @action="router.push('/pilot/closure')"
        />
      </template>
      <el-table-column prop="finance_no" label="融资编号" width="180" />
      <el-table-column prop="product_type" label="产品类型" width="140" />
      <el-table-column label="来源" min-width="160">
        <template #default="{ row }">
          <span>{{ row.source_type }}</span>
          <el-button
            v-if="row.source_type === 'AGENCY_PURCHASE'"
            link
            type="primary"
            @click="goAgencySource(row.source_id)"
          >
            {{ row.source_id }}
          </el-button>
          <span v-else>{{ row.source_id }}</span>
        </template>
      </el-table-column>
      <el-table-column label="申请金额" width="160">
        <template #default="{ row }">{{ formatMoney(row.apply_amount, row.currency) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="financeStatusTagType(row.finance_status)" size="small">
            {{ financeStatusLabel(row.finance_status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.finance_status === 'DRAFT'" link type="primary" @click="onSubmit(row.id)">提交</el-button>
          <el-button v-if="row.finance_status === 'SUBMITTED'" link type="success" @click="onApprove(row.id)">审批</el-button>
          <el-button
            v-if="row.finance_status === 'TO_DISBURSE' && canPreCheck"
            link
            type="primary"
            @click="openDisburse(row)"
          >
            放款前校验
          </el-button>
          <el-button
            v-if="canGoClearing(row.finance_status)"
            link
            type="warning"
            @click="goClearing(row.id)"
          >
            清分
          </el-button>
          <el-button link @click="router.push('/bi/dashboard?from=pilot')">BI</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建融资申请" width="640px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="产品类型" required>
          <el-select v-model="form.product_type" style="width: 100%">
            <el-option label="订单融资" value="ORDER_FINANCE" />
            <el-option label="凭证融资" value="VOUCHER_FINANCE" />
            <el-option label="仓单质押" value="RECEIPT_PLEDGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源类型" required>
          <el-select v-model="form.source_type" style="width: 100%">
            <el-option label="代采" value="AGENCY_PURCHASE" />
            <el-option label="订单" value="ORDER" />
            <el-option label="凭证" value="VOUCHER" />
            <el-option label="仓单" value="RECEIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源 ID" required>
          <el-input v-model="form.source_id" placeholder="代采单 ID / ORD001" />
        </el-form-item>
        <el-form-item label="客户 ID" required>
          <el-input v-model="form.customer_id" />
        </el-form-item>
        <el-form-item label="资金方 ID" required>
          <el-input v-model="form.funding_party_id" placeholder="ENT_FACTOR_001" />
        </el-form-item>
        <el-form-item label="授信 ID">
          <el-input v-model="form.credit_id" placeholder="CR001" />
        </el-form-item>
        <el-form-item label="申请金额" required>
          <el-input v-model="form.apply_amount" />
        </el-form-item>
        <el-form-item label="币种" required>
          <el-input v-model="form.currency" />
        </el-form-item>
        <el-form-item label="期限(天)" required>
          <el-input-number v-model="form.term_days" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="年化利率" required>
          <el-input v-model="form.annual_rate" placeholder="0.085000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="onCreate">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showDisburse" title="放款前校验与执行" size="520px">
      <el-form :model="disburseForm" label-width="120px">
        <el-form-item label="融资编号">
          <span>{{ disburseTarget?.finance_no }}</span>
        </el-form-item>
        <el-form-item label="放款金额" required>
          <el-input v-model="disburseForm.disburse_amount" />
        </el-form-item>
        <el-form-item label="起息日" required>
          <el-input v-model="disburseForm.value_date" placeholder="2026-06-30" />
        </el-form-item>
        <el-form-item label="出款账户" required>
          <el-input v-model="disburseForm.payer_account_id" />
        </el-form-item>
        <el-form-item label="收款账户" required>
          <el-input v-model="disburseForm.receiver_account_id" />
        </el-form-item>
        <el-form-item label="二次确认">
          <el-input v-model="disburseForm.secondary_auth_token" placeholder="MOCK-APPROVED" />
        </el-form-item>
      </el-form>

      <div class="precheck-actions">
        <el-button :loading="preCheckLoading" @click="runPreCheck">执行前置校验</el-button>
        <el-button
          v-if="canDisburse"
          type="primary"
          :loading="disburseLoading"
          :disabled="!preCheckResult?.passed"
          @click="runDisburse"
        >
          确认放款
        </el-button>
      </div>

      <el-alert
        v-if="preCheckResult"
        :type="preCheckResult.passed ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="precheck-summary"
        :title="preCheckResult.passed ? '前置校验通过，可执行放款' : '前置校验未通过'"
      />

      <el-table v-if="preCheckResult" :data="preCheckResult.checks" size="small" stripe class="precheck-table">
        <el-table-column prop="code" label="检查项" width="160" />
        <el-table-column prop="result" label="结果" width="100">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.result === 'PASSED' ? 'success' : row.result === 'WARNING' ? 'warning' : 'danger'"
            >
              {{ row.result }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="说明" min-width="200" />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  approveFinanceApplication,
  createFinanceApplication,
  disburseFinanceApplication,
  listFinanceApplications,
  preCheckFinanceApplication,
  submitFinanceApplication,
  type FinanceApplication,
  type FinancePreCheckResult
} from '../api/finance'
import { financeStatusLabel, financeStatusTagType, FINANCE_REPAYABLE_STATUSES } from '../constants/financeDict'
import { usePermission } from '../composables/usePermission'
import { formatMoney } from '../utils/format'
import { apiErrorMessage } from '../utils/apiError'
import ListEmptyState from '../components/ListEmptyState.vue'

const { hasPermission } = usePermission()
const canPreCheck = computed(() => hasPermission('FINANCE_PRECHECK') || hasPermission('FINANCE_DISBURSE'))
const canDisburse = computed(() => hasPermission('FINANCE_DISBURSE'))

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const applications = ref<FinanceApplication[]>([])
const showCreate = ref(false)
const showDisburse = ref(false)
const preCheckLoading = ref(false)
const disburseLoading = ref(false)
const disburseTarget = ref<FinanceApplication | null>(null)
const preCheckResult = ref<FinancePreCheckResult | null>(null)
const disburseForm = reactive({
  disburse_amount: '',
  currency: 'CNY',
  value_date: '2026-06-30',
  payer_account_id: 'ACC_FUNDING_001',
  receiver_account_id: 'ACC_MEMBER_001',
  funding_channel: 'INTERNAL_ACCOUNT',
  secondary_auth_token: 'MOCK-APPROVED',
  idempotency_key: ''
})
const form = reactive({
  product_type: 'ORDER_FINANCE',
  source_type: 'ORDER',
  source_id: 'ORD001',
  customer_id: 'ENT_MEMBER_001',
  funding_party_id: 'ENT_FACTOR_001',
  credit_id: 'CR001',
  apply_amount: '100000.00',
  currency: 'CNY',
  term_days: 90,
  annual_rate: '0.085000'
})

const filterSourceId = computed(() => (route.query.source_id as string) || '')
const filterHighlight = computed(() => (route.query.highlight as string) || '')

const filterBannerTitle = computed(() => {
  if (filterHighlight.value) return `高亮融资单：${filterHighlight.value}`
  if (filterSourceId.value) return `筛选来源 ID：${filterSourceId.value}`
  return ''
})

const displayApplications = computed(() => {
  let rows = applications.value
  if (filterSourceId.value) {
    rows = rows.filter((r) => r.source_id === filterSourceId.value)
  }
  return rows
})

function rowClassName({ row }: { row: FinanceApplication }) {
  return row.id === filterHighlight.value ? 'row-highlight' : ''
}

function canGoClearing(status: string) {
  return FINANCE_REPAYABLE_STATUSES.has(status)
}

function goAgencySource(sourceId: string) {
  router.push(`/agency-purchase/applications/${sourceId}`)
}

function goClearing(financeId: string) {
  router.push(`/accounts/clearing?finance_id=${financeId}`)
}

async function load() {
  loading.value = true
  try {
    const res = await listFinanceApplications({ page_no: 1, page_size: 50 })
    if (res.success) applications.value = res.data?.records || []
    else ElMessage.error(res.message || '加载失败')
  } catch (e: unknown) {
    ElMessage.error(apiErrorMessage(e, '加载失败'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  showCreate.value = true
}

async function onCreate() {
  try {
    const res = await createFinanceApplication(form)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('融资申请已创建')
    showCreate.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  }
}

async function onSubmit(id: string) {
  const res = await submitFinanceApplication(id)
  if (res.success) {
    ElMessage.success('已提交')
    await load()
  } else {
    ElMessage.error(res.message || '提交失败')
  }
}

async function onApprove(id: string) {
  const res = await approveFinanceApplication(id)
  if (res.success) {
    ElMessage.success('审批通过')
    await load()
  } else {
    ElMessage.error(res.message || '审批失败')
  }
}

function openDisburse(row: FinanceApplication) {
  disburseTarget.value = row
  preCheckResult.value = null
  disburseForm.disburse_amount = row.approved_amount || row.apply_amount
  disburseForm.idempotency_key = `UI-${row.id}-${Date.now()}`
  showDisburse.value = true
}

function preCheckPayload() {
  return {
    disburse_amount: disburseForm.disburse_amount,
    currency: disburseForm.currency,
    value_date: disburseForm.value_date,
    payer_account_id: disburseForm.payer_account_id,
    receiver_account_id: disburseForm.receiver_account_id,
    funding_channel: disburseForm.funding_channel,
    idempotency_key: disburseForm.idempotency_key,
    secondary_auth_token: disburseForm.secondary_auth_token
  }
}

async function runPreCheck() {
  if (!disburseTarget.value) return
  preCheckLoading.value = true
  try {
    const res = await preCheckFinanceApplication(disburseTarget.value.id, preCheckPayload())
    if (!res.success) throw new Error(res.message)
    preCheckResult.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '前置校验失败')
  } finally {
    preCheckLoading.value = false
  }
}

async function runDisburse() {
  if (!disburseTarget.value || !preCheckResult.value?.passed) return
  disburseLoading.value = true
  try {
    const res = await disburseFinanceApplication(
      disburseTarget.value.id,
      {
        disburse_amount: disburseForm.disburse_amount,
        currency: disburseForm.currency,
        value_date: disburseForm.value_date,
        payer_account_id: disburseForm.payer_account_id,
        receiver_account_id: disburseForm.receiver_account_id,
        funding_channel: disburseForm.funding_channel
      },
      {
        idempotencyKey: disburseForm.idempotency_key,
        secondaryAuthToken: disburseForm.secondary_auth_token
      }
    )
    if (!res.success) throw new Error(res.message)
    ElMessage.success('放款已提交')
    showDisburse.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '放款失败')
  } finally {
    disburseLoading.value = false
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
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.filter-banner {
  margin-bottom: 12px;
}
:deep(.row-highlight) {
  background-color: var(--el-color-primary-light-9) !important;
}
.precheck-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.precheck-summary {
  margin-bottom: 12px;
}
.precheck-table {
  margin-top: 8px;
}
</style>
