<template>
  <div v-if="!canView">
    <el-result icon="warning" title="无权访问" sub-title="当前身份无清分查看权限（CLEARING_VIEW）" />
  </div>
  <div v-else class="clearing-page">
    <AccountBalanceSummary ref="balanceSummaryRef" />
    <div class="toolbar">
      <h2>清分中心</h2>
    </div>

    <el-alert
      v-if="!financeLoading && repayableFinances.length === 0"
      type="warning"
      show-icon
      :closable="false"
      title="暂无可清分融资单"
      description="需存在 DISBURSED / REPAYING / OVERDUE 状态的融资单；请先在融资管理或通过演示数据脚本初始化 FIN_CLEAR_OK。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" class="section">
      <template #header>1. 选择融资单与已匹配流水</template>
      <el-form :inline="true" @submit.prevent="loadEntry">
        <el-form-item label="融资单 ID" required>
          <el-select
            v-model="financeId"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入融资单 ID"
            style="width: 280px"
            :loading="financeLoading"
            @change="onFinanceChange"
          >
            <el-option
              v-for="item in repayableFinances"
              :key="item.id"
              :label="`${item.finance_no} (${item.id})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="entryLoading" :disabled="!financeId" @click="loadEntry">
            加载清分入口
          </el-button>
        </el-form-item>
      </el-form>

      <el-descriptions v-if="entry" :column="3" border size="small" class="entry-summary">
        <el-descriptions-item label="融资编号">{{ entry.finance_no || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ entry.finance_status || '—' }}</el-descriptions-item>
        <el-descriptions-item label="待还本金">
          {{ formatMoney(entry.outstanding_principal, entry.currency) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form v-if="entry" label-width="120px" style="margin-top: 16px">
        <el-form-item label="已匹配流水" required>
          <el-select
            v-model="bankFlowId"
            placeholder="请选择 MATCHED 流水"
            style="width: 100%"
            :loading="matchedLoading"
            :disabled="!matchedFlows.length"
          >
            <el-option
              v-for="flow in matchedFlows"
              :key="flow.id"
              :label="`${flow.external_flow_no} | ${formatMoney(flow.amount, flow.currency)}`"
              :value="flow.id"
            />
          </el-select>
          <p v-if="financeId && !matchedLoading && matchedFlows.length === 0" class="hint warn">
            暂无已匹配流水，请先在「银行流水」页导入并匹配到当前融资单。
          </p>
        </el-form-item>
        <el-form-item label="清分规则" required>
          <el-select
            v-model="clearingRuleId"
            placeholder="选择规则"
            style="width: 100%"
            :disabled="!(entry?.clearing_rules?.length)"
          >
            <el-option
              v-for="rule in entry?.clearing_rules || []"
              :key="rule.id"
              :label="`${rule.rule_name} (${rule.product_type})`"
              :value="rule.id"
            />
          </el-select>
          <p v-if="entry && !(entry.clearing_rules?.length)" class="hint warn">
            未找到可用清分规则，请先在「清分规则」页创建并审批通过。
          </p>
        </el-form-item>
      </el-form>
      <el-empty v-else-if="!entryLoading" description="选择融资单后点击「加载清分入口」" />
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="section-header">
          <span>2. 清分试算</span>
          <el-button
            v-if="canCalculate"
            type="primary"
            :loading="calculating"
            :disabled="!entry || !bankFlowId || !clearingRuleId"
            @click="onCalculate"
          >
            试算
          </el-button>
        </div>
      </template>

      <el-table v-if="calculateResult" :data="allocationRows" border size="small">
        <el-table-column prop="label" label="分配项" width="160" />
        <el-table-column label="金额" align="right">
          <template #default="{ row }">{{ row.amountDisplay }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="完成选择后点击试算，分配结果由后端计算" />

      <el-alert
        v-for="(w, idx) in calculateResult?.warnings || []"
        :key="idx"
        type="warning"
        :title="w"
        show-icon
        :closable="false"
        style="margin-top: 12px"
      />
    </el-card>

    <el-card shadow="never" class="section">
      <template #header>
        <div class="section-header">
          <span>3. 执行清分</span>
          <el-button
            v-if="canExecute"
            type="danger"
            :disabled="!calculateResult"
            :loading="executing"
            @click="openExecuteConfirm"
          >
            执行清分
          </el-button>
        </div>
      </template>

      <el-descriptions v-if="executeResult" :column="2" border size="small">
        <el-descriptions-item label="还款记录">{{ executeResult.repayment_id }}</el-descriptions-item>
        <el-descriptions-item label="清分结果">{{ executeResult.clearing_result_id }}</el-descriptions-item>
        <el-descriptions-item label="融资状态">{{ executeResult.finance_status }}</el-descriptions-item>
        <el-descriptions-item label="还款金额">
          {{ formatMoney(executeResult.repayment_amount, executeResult.currency) }}
        </el-descriptions-item>
        <el-descriptions-item label="幂等重放">{{ executeResult.idempotent_replay ? '是' : '否' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="试算通过后执行清分，需二次确认 Token（测试环境 MOCK-APPROVED）" />
    </el-card>

    <el-dialog v-model="showExecuteConfirm" title="二次确认" width="480px">
      <p>确认执行清分？将写入还款与清分结果，并划转回款账户资金。</p>
      <p v-if="calculateResult" class="confirm-amount">
        试算还款金额：{{ formatMoney(calculateResult.repayment_amount, calculateResult.currency) }}
      </p>
      <p class="hint">测试环境使用二次确认 Token：<code>MOCK-APPROVED</code></p>
      <template #footer>
        <el-button @click="showExecuteConfirm = false">取消</el-button>
        <el-button type="danger" :loading="executing" @click="onExecute">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  calculateClearing,
  executeClearing,
  getClearingEntry,
  listBankFlows,
  newIdempotencyKey,
  SECONDARY_AUTH_MOCK,
  type BankFlow,
  type ClearingCalculateResult,
  type ClearingEntry,
  type ClearingExecuteResult
} from '../api/account'
import { listFinanceApplications, type FinanceApplication } from '../api/finance'
import { usePermission } from '../composables/usePermission'
import AccountBalanceSummary from '../components/AccountBalanceSummary.vue'
import { formatMoney } from '../utils/format'

const { hasPermission } = usePermission()
const canView = computed(() => hasPermission('CLEARING_VIEW'))
const canCalculate = computed(() => hasPermission('CLEARING_CALCULATE'))
const canExecute = computed(() => hasPermission('CLEARING_EXECUTE'))

const financeId = ref('')
const bankFlowId = ref('')
const clearingRuleId = ref('')
const entry = ref<ClearingEntry | null>(null)
const matchedFlows = ref<BankFlow[]>([])
const repayableFinances = ref<FinanceApplication[]>([])

const financeLoading = ref(false)
const entryLoading = ref(false)
const matchedLoading = ref(false)
const calculating = ref(false)
const executing = ref(false)

const calculateResult = ref<ClearingCalculateResult | null>(null)
const executeResult = ref<ClearingExecuteResult | null>(null)
const showExecuteConfirm = ref(false)
const pendingIdempotencyKey = ref('')
const balanceSummaryRef = ref<InstanceType<typeof AccountBalanceSummary> | null>(null)

const allocationRows = computed(() => {
  const a = calculateResult.value?.allocation
  const currency = calculateResult.value?.currency
  if (!a) return []
  return [
    { label: '罚息', amountDisplay: formatMoney(a.penalty_amount, currency) },
    { label: '费用', amountDisplay: formatMoney(a.fee_amount, currency) },
    { label: '利息', amountDisplay: formatMoney(a.interest_amount, currency) },
    { label: '本金', amountDisplay: formatMoney(a.principal_amount, currency) },
    { label: '剩余', amountDisplay: formatMoney(a.remaining_amount, currency) }
  ]
})

async function loadRepayableFinances() {
  financeLoading.value = true
  try {
    const res = await listFinanceApplications({ page_no: 1, page_size: 100 })
    if (!res.success) throw new Error(res.message)
    const ok = new Set(['DISBURSED', 'REPAYING', 'OVERDUE'])
    repayableFinances.value = (res.data?.records || []).filter((f: FinanceApplication) => ok.has(f.finance_status))
  } catch (e: any) {
    repayableFinances.value = []
    ElMessage.error(e.response?.data?.message || e.message || '加载融资单失败')
  } finally {
    financeLoading.value = false
  }
}

async function loadMatchedFlows() {
  if (!financeId.value) {
    matchedFlows.value = []
    return
  }
  matchedLoading.value = true
  try {
    const res = await listBankFlows({ page_no: 1, page_size: 100, match_status: 'MATCHED', flow_type: 'IN' })
    if (!res.success) throw new Error(res.message)
    matchedFlows.value = (res.data?.records || []).filter(
      (f: BankFlow) => f.source_type === 'FINANCE' && f.source_id === financeId.value
    )
    bankFlowId.value = matchedFlows.value.length === 1 ? matchedFlows.value[0].id : bankFlowId.value
    if (bankFlowId.value && !matchedFlows.value.some((f) => f.id === bankFlowId.value)) {
      bankFlowId.value = ''
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '加载已匹配流水失败')
  } finally {
    matchedLoading.value = false
  }
}

async function loadEntry() {
  if (!financeId.value) {
    ElMessage.warning('请先选择融资单')
    return
  }
  entryLoading.value = true
  calculateResult.value = null
  executeResult.value = null
  try {
    const res = await getClearingEntry(financeId.value)
    if (!res.success) throw new Error(res.message)
    entry.value = res.data
    clearingRuleId.value =
      entry.value?.clearing_rules?.length === 1 ? entry.value.clearing_rules[0].id : ''
    await loadMatchedFlows()
  } catch (e: any) {
    entry.value = null
    ElMessage.error(e.response?.data?.message || e.message || '加载清分入口失败')
  } finally {
    entryLoading.value = false
  }
}

function onFinanceChange() {
  bankFlowId.value = ''
  clearingRuleId.value = ''
  entry.value = null
  calculateResult.value = null
  executeResult.value = null
}

function validateSelection() {
  if (!financeId.value || !bankFlowId.value || !clearingRuleId.value) {
    ElMessage.warning('请完整选择融资单、已匹配流水与清分规则')
    return false
  }
  return true
}

async function onCalculate() {
  if (!canCalculate.value) return
  if (!validateSelection()) return
  calculating.value = true
  calculateResult.value = null
  executeResult.value = null
  try {
    const res = await calculateClearing({
      finance_id: financeId.value,
      bank_flow_id: bankFlowId.value,
      clearing_rule_id: clearingRuleId.value
    })
    if (!res.success) throw new Error(res.message)
    calculateResult.value = res.data
    ElMessage.success('试算完成')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '试算失败')
  } finally {
    calculating.value = false
  }
}

function openExecuteConfirm() {
  if (!validateSelection()) return
  pendingIdempotencyKey.value = newIdempotencyKey()
  showExecuteConfirm.value = true
}

async function onExecute() {
  if (!canExecute.value) return
  if (!validateSelection()) return
  executing.value = true
  try {
    const res = await executeClearing(
      {
        finance_id: financeId.value,
        bank_flow_id: bankFlowId.value,
        clearing_rule_id: clearingRuleId.value
      },
      pendingIdempotencyKey.value,
      SECONDARY_AUTH_MOCK
    )
    if (!res.success) throw new Error(res.message)
    const executed = res.data
    executeResult.value = executed
    showExecuteConfirm.value = false
    ElMessage.success(res.data?.idempotent_replay ? '幂等重放：清分已执行过' : '清分执行成功')
    await loadEntry()
    balanceSummaryRef.value?.load()
    executeResult.value = executed
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '执行失败')
  } finally {
    executing.value = false
  }
}

const route = useRoute()

onMounted(async () => {
  await loadRepayableFinances()
  const qFinanceId = route.query.finance_id as string | undefined
  if (qFinanceId) {
    financeId.value = qFinanceId
    await loadEntry()
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.section {
  margin-bottom: 16px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.entry-summary {
  margin-top: 12px;
}
.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #909399;
}
.hint.warn {
  color: #e6a23c;
}
.confirm-amount {
  font-weight: 600;
  margin: 12px 0;
}
code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
