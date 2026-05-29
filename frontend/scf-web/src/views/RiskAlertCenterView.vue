<template>
  <div class="risk-center">
    <div class="page-header">
      <div>
        <h2>风险预警中心</h2>
        <p class="subtitle">承接经营看板风险下钻，跟踪处理状态与责任人</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-card shadow="never" class="filter-bar">
      <el-form inline @submit.prevent="load">
        <el-form-item label="告警类型">
          <el-select v-model="filters.alert_code" clearable placeholder="全部" style="width: 180px">
            <el-option v-for="opt in alertCodeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filters.severity" clearable placeholder="全部" style="width: 120px">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select v-model="filters.handle_status" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="opt in handleStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="error" type="error" :title="error" show-icon class="error-banner" />

    <el-table v-loading="loading" :data="alerts" stripe>
      <el-table-column prop="severity" label="级别" width="88">
        <template #default="{ row }">
          <el-tag :type="severityType(row.severity)" size="small">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="120" />
      <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="related_label" label="关联对象" width="140" show-overflow-tooltip />
      <el-table-column label="处理状态" width="110">
        <template #default="{ row }">
          <el-tag :type="handleStatusType(row.handle_status)" size="small">
            {{ handleStatusLabel(row.handle_status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="责任人" width="120">
        <template #default="{ row }">
          <span>{{ row.assignee_name || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="检测时间" width="168">
        <template #default="{ row }">{{ formatTime(row.detected_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.related_route" link type="primary" @click="goRelated(row)">关联单据</el-button>
          <el-button v-if="canHandle && row.handle_status === 'OPEN'" link type="warning" @click="onClaim(row)">
            认领
          </el-button>
          <el-button v-if="canHandle" link type="primary" @click="openHandle(row)">处理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="total > 0" class="pager">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>
    <el-empty v-if="!loading && !alerts.length" description="暂无风险告警" />

    <el-dialog v-model="handleVisible" title="更新处理状态" width="480px" @closed="resetHandleForm">
      <el-form ref="handleFormRef" :model="handleForm" :rules="handleRules" label-width="100px">
        <el-form-item label="处理状态" prop="handle_status">
          <el-select v-model="handleForm.handle_status" style="width: 100%">
            <el-option v-for="opt in handleStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="handleForm.assignee_name" placeholder="可选，默认当前用户" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="handleForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  claimRiskAlert,
  handleRiskAlert,
  listRiskAlerts,
  type RiskAlertItem
} from '../api/riskAlert'
import { useAuthStore } from '../stores/auth'
import { usePermission } from '../composables/usePermission'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { hasPermission } = usePermission()

const canHandle = computed(() => hasPermission('RISK_ALERT_HANDLE'))
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const alerts = ref<RiskAlertItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)

const filters = reactive({
  alert_code: '',
  severity: '',
  handle_status: ''
})

const alertCodeOptions = [
  { value: 'FINANCE_OVERDUE', label: '融资逾期' },
  { value: 'BANK_FLOW_UNMATCHED', label: '未匹配流水' },
  { value: 'PRICE_ABNORMAL', label: '价格异常' },
  { value: 'INVENTORY_STOCKTAKE', label: '盘点异常' }
]

const handleStatusOptions = [
  { value: 'OPEN', label: '待处理' },
  { value: 'ACK', label: '已认领' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'DISMISSED', label: '已忽略' }
]

const handleVisible = ref(false)
const handleTarget = ref<RiskAlertItem | null>(null)
const handleFormRef = ref<FormInstance>()
const handleForm = reactive({
  handle_status: 'PROCESSING',
  assignee_name: '',
  remark: ''
})
const handleRules: FormRules = {
  handle_status: [{ required: true, message: '请选择处理状态', trigger: 'change' }]
}

function severityType(severity: string) {
  if (severity === 'HIGH') return 'danger'
  if (severity === 'MEDIUM') return 'warning'
  return 'info'
}

function handleStatusLabel(status: string) {
  return handleStatusOptions.find((o) => o.value === status)?.label ?? status
}

function handleStatusType(status: string) {
  if (status === 'RESOLVED') return 'success'
  if (status === 'DISMISSED') return 'info'
  if (status === 'PROCESSING' || status === 'ACK') return 'warning'
  return 'danger'
}

function formatTime(iso: string) {
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return iso
  }
}

function applyRouteQuery() {
  const code = route.query.alert_code
  if (typeof code === 'string' && code) {
    filters.alert_code = code
  }
}

function resetFilters() {
  filters.alert_code = ''
  filters.severity = ''
  filters.handle_status = ''
  pageNo.value = 1
  load()
}

function goRelated(row: RiskAlertItem) {
  if (!row.related_route) return
  router.push(row.related_route)
}

function openHandle(row: RiskAlertItem) {
  handleTarget.value = row
  handleForm.handle_status = row.handle_status === 'OPEN' ? 'ACK' : row.handle_status
  handleForm.assignee_name = row.assignee_name || auth.userName || ''
  handleForm.remark = row.remark || ''
  handleVisible.value = true
}

function resetHandleForm() {
  handleTarget.value = null
  handleForm.handle_status = 'PROCESSING'
  handleForm.assignee_name = ''
  handleForm.remark = ''
}

async function onClaim(row: RiskAlertItem) {
  if (!canHandle.value) return
  submitting.value = true
  try {
    const res = await claimRiskAlert(row.id)
    if (!res.success) throw new Error(res.message || '认领失败')
    ElMessage.success('已认领')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '认领失败')
  } finally {
    submitting.value = false
  }
}

async function submitHandle() {
  if (!handleTarget.value) return
  await handleFormRef.value?.validate()
  submitting.value = true
  try {
    const res = await handleRiskAlert(handleTarget.value.id, {
      handle_status: handleForm.handle_status,
      assignee_name: handleForm.assignee_name || undefined,
      remark: handleForm.remark || undefined
    })
    if (!res.success) throw new Error(res.message || '保存失败')
    ElMessage.success('处理状态已更新')
    handleVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, string | number> = {
      page_no: pageNo.value,
      page_size: pageSize.value
    }
    if (filters.alert_code) params.alert_code = filters.alert_code
    if (filters.severity) params.severity = filters.severity
    if (filters.handle_status) params.handle_status = filters.handle_status

    const res = await listRiskAlerts(params)
    if (!res.success) throw new Error(res.message || '加载失败')
    alerts.value = res.data?.records ?? []
    total.value = res.data?.total ?? alerts.value.length
  } catch (e: any) {
    error.value = e?.response?.data?.message || e.message || '风险告警加载失败'
    alerts.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query.alert_code,
  () => {
    applyRouteQuery()
    load()
  }
)

onMounted(() => {
  applyRouteQuery()
  load()
})
</script>

<style scoped>
.risk-center { padding-bottom: 24px; }
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 16px;
}
.page-header h2 { margin: 0 0 4px; }
.subtitle { margin: 0; font-size: 13px; color: #909399; }
.filter-bar { margin-bottom: 16px; }
.filter-bar :deep(.el-form-item) { margin-bottom: 0; }
.error-banner { margin-bottom: 16px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
