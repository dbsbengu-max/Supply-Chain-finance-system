<template>
  <div v-if="!canView">
    <el-result icon="warning" title="无权访问" sub-title="当前身份无银行流水查看权限（ACCOUNT_FLOW_VIEW）" />
  </div>
  <div v-else>
    <AccountBalanceSummary ref="balanceSummaryRef" />
    <div class="toolbar">
      <h2>银行流水</h2>
      <el-button v-if="canImport" type="primary" @click="openImport">导入流水</el-button>
    </div>

    <el-form :inline="true" class="filters" @submit.prevent="load">
      <el-form-item label="匹配状态">
        <el-select v-model="filters.match_status" clearable placeholder="全部" style="width: 140px">
          <el-option label="未匹配" value="UNMATCHED" />
          <el-option label="已匹配" value="MATCHED" />
        </el-select>
      </el-form-item>
      <el-form-item label="流水类型">
        <el-select v-model="filters.flow_type" clearable placeholder="全部" style="width: 120px">
          <el-option label="收入 IN" value="IN" />
          <el-option label="支出 OUT" value="OUT" />
        </el-select>
      </el-form-item>
      <el-form-item label="账户 ID">
        <el-input v-model="filters.account_id" clearable placeholder="ACC_REPAY_001" style="width: 160px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-if="flows.length || loading" :data="flows" v-loading="loading" stripe>
      <el-table-column prop="external_flow_no" label="外部流水号" min-width="160" />
      <el-table-column prop="account_id" label="账户 ID" width="140" />
      <el-table-column label="类型" width="80">
        <template #default="{ row }">{{ labelOf(FLOW_TYPE_LABELS, row.flow_type) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="150" align="right">
        <template #default="{ row }">{{ formatMoney(row.amount, row.currency) }}</template>
      </el-table-column>
      <el-table-column prop="counterparty_name" label="对手方" min-width="120" />
      <el-table-column label="匹配状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.match_status === 'MATCHED' ? 'success' : 'info'" size="small">
            {{ labelOf(MATCH_STATUS_LABELS, row.match_status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="关联融资" min-width="140">
        <template #default="{ row }">
          <span v-if="row.source_type === 'FINANCE'">{{ row.source_id }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="canMatch && row.flow_type === 'IN' && row.match_status === 'UNMATCHED'"
            link
            type="primary"
            @click="openMatch(row)"
          >
            匹配融资
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="暂无流水记录，可点击「导入流水」添加 IN 类型回款流水" />

    <el-dialog v-model="showImport" title="导入银行流水（IN）" width="640px" @closed="importFormRef?.resetFields()">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅支持向 REPAYMENT 账户导入 IN 类型流水；金额为正数，最多两位小数。"
        style="margin-bottom: 16px"
      />
      <el-form ref="importFormRef" :model="importForm" :rules="importRules" label-width="120px">
        <el-form-item label="回款账户 ID" prop="account_id">
          <el-input v-model="importForm.account_id" placeholder="ACC_REPAY_001" />
        </el-form-item>
        <el-form-item label="外部流水号" prop="external_flow_no">
          <el-input v-model="importForm.external_flow_no" placeholder="银行侧唯一流水号" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input v-model="importForm.amount" placeholder="120000.00" />
        </el-form-item>
        <el-form-item label="币种" prop="currency">
          <el-input v-model="importForm.currency" placeholder="CNY" maxlength="3" />
        </el-form-item>
        <el-form-item label="对手方名称">
          <el-input v-model="importForm.counterparty_name" />
        </el-form-item>
        <el-form-item label="对手方账号">
          <el-input v-model="importForm.counterparty_account" />
        </el-form-item>
        <el-form-item label="流水时间" prop="flow_time">
          <el-input v-model="importForm.flow_time" placeholder="ISO-8601，如 2026-06-01T10:00:00Z" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showImport = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="onImport">确认导入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showMatch" title="匹配融资单" width="520px">
      <p class="match-hint">
        流水：{{ matchingFlow?.external_flow_no }}，金额
        {{ formatMoney(matchingFlow?.amount, matchingFlow?.currency) }}
      </p>
      <el-empty
        v-if="!repayableLoading && repayableFinances.length === 0"
        description="暂无可还款融资单（需 DISBURSED / REPAYING / OVERDUE 状态）"
      />
      <el-form v-else label-width="100px">
        <el-form-item label="融资单" required>
          <el-select
            v-model="matchFinanceId"
            filterable
            placeholder="选择可还款融资单"
            style="width: 100%"
            :loading="repayableLoading"
          >
            <el-option
              v-for="item in repayableFinances"
              :key="item.id"
              :label="`${item.finance_no} (${item.finance_status})`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMatch = false">取消</el-button>
        <el-button
          type="primary"
          :loading="matching"
          :disabled="!repayableFinances.length"
          @click="onMatch"
        >
          确认匹配
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { importBankFlows, listBankFlows, matchBankFlow, type BankFlow } from '../api/account'
import { listFinanceApplications, type FinanceApplication } from '../api/finance'
import { usePermission } from '../composables/usePermission'
import AccountBalanceSummary from '../components/AccountBalanceSummary.vue'
import {
  FLOW_TYPE_LABELS,
  formatMoney,
  isValidAmount,
  labelOf,
  MATCH_STATUS_LABELS
} from '../utils/format'

const { hasPermission } = usePermission()
const canView = computed(() => hasPermission('ACCOUNT_FLOW_VIEW'))
const canImport = computed(() => hasPermission('ACCOUNT_FLOW_IMPORT'))
const canMatch = computed(() => hasPermission('ACCOUNT_FLOW_MATCH'))

const loading = ref(false)
const importing = ref(false)
const matching = ref(false)
const repayableLoading = ref(false)
const flows = ref<BankFlow[]>([])
const repayableFinances = ref<FinanceApplication[]>([])

const filters = reactive({
  match_status: '',
  flow_type: '',
  account_id: ''
})

const showImport = ref(false)
const showMatch = ref(false)
const matchingFlow = ref<BankFlow | null>(null)
const matchFinanceId = ref('')
const balanceSummaryRef = ref<InstanceType<typeof AccountBalanceSummary> | null>(null)
const importFormRef = ref<FormInstance>()

const importForm = reactive({
  account_id: 'ACC_REPAY_001',
  external_flow_no: '',
  amount: '',
  currency: 'CNY',
  counterparty_name: '',
  counterparty_account: '',
  flow_time: ''
})

const importRules: FormRules = {
  account_id: [{ required: true, message: '请输入回款账户 ID', trigger: 'blur' }],
  external_flow_no: [{ required: true, message: '请输入外部流水号', trigger: 'blur' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isValidAmount(String(value || ''))) {
          callback(new Error('金额须为正数，最多两位小数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  currency: [
    { required: true, message: '请输入币种', trigger: 'blur' },
    { pattern: /^[A-Z]{3}$/, message: '币种须为 3 位大写字母，如 CNY', trigger: 'blur' }
  ],
  flow_time: [
    { required: true, message: '请输入流水时间', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const t = Date.parse(String(value || ''))
        if (Number.isNaN(t)) {
          callback(new Error('流水时间格式无效，请使用 ISO-8601'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function load() {
  if (!canView.value) return
  loading.value = true
  try {
    const params: Record<string, string | number> = { page_no: 1, page_size: 50 }
    if (filters.match_status) params.match_status = filters.match_status
    if (filters.flow_type) params.flow_type = filters.flow_type
    if (filters.account_id) params.account_id = filters.account_id
    const res = await listBankFlows(params)
    if (!res.success) throw new Error(res.message)
    flows.value = res.data?.records || []
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRepayableFinances() {
  repayableLoading.value = true
  try {
    const res = await listFinanceApplications({ page_no: 1, page_size: 100 })
    if (!res.success) throw new Error(res.message)
    const ok = new Set(['DISBURSED', 'REPAYING', 'OVERDUE'])
    repayableFinances.value = (res.data?.records || []).filter((f: FinanceApplication) => ok.has(f.finance_status))
  } catch (e: any) {
    repayableFinances.value = []
    ElMessage.error(e.response?.data?.message || e.message || '加载融资单失败')
  } finally {
    repayableLoading.value = false
  }
}

function resetFilters() {
  filters.match_status = ''
  filters.flow_type = ''
  filters.account_id = ''
  load()
}

function openImport() {
  importForm.external_flow_no = `IMP-${Date.now()}`
  importForm.amount = ''
  importForm.flow_time = new Date().toISOString()
  showImport.value = true
}

async function onImport() {
  const valid = await importFormRef.value?.validate().catch(() => false)
  if (!valid) return
  importing.value = true
  try {
    const res = await importBankFlows({
      flows: [
        {
          account_id: importForm.account_id.trim(),
          external_flow_no: importForm.external_flow_no.trim(),
          amount: importForm.amount.trim(),
          currency: importForm.currency.trim().toUpperCase(),
          counterparty_name: importForm.counterparty_name || undefined,
          counterparty_account: importForm.counterparty_account || undefined,
          flow_time: importForm.flow_time
        }
      ]
    })
    if (!res.success) throw new Error(res.message)
    ElMessage.success('流水导入成功')
    showImport.value = false
    await load()
    balanceSummaryRef.value?.load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '导入失败')
  } finally {
    importing.value = false
  }
}

function openMatch(row: BankFlow) {
  matchingFlow.value = row
  matchFinanceId.value = ''
  showMatch.value = true
  loadRepayableFinances()
}

async function onMatch() {
  if (!matchingFlow.value) return
  if (!matchFinanceId.value) {
    ElMessage.warning('请选择融资单')
    return
  }
  matching.value = true
  try {
    const res = await matchBankFlow(matchingFlow.value.id, matchFinanceId.value)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('匹配成功')
    showMatch.value = false
    await load()
    balanceSummaryRef.value?.load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '匹配失败')
  } finally {
    matching.value = false
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
.filters {
  margin-bottom: 12px;
}
.muted {
  color: #999;
}
.match-hint {
  margin: 0 0 12px;
  color: #666;
}
</style>
