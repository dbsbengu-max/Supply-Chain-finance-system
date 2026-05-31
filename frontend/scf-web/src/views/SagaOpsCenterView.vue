<template>
  <div class="saga-ops-center">
    <div class="page-header">
      <div>
        <h2>Saga 运营监控台</h2>
        <p class="subtitle">Outbox 投递与补偿任务 backlog、自动重试与人工介入</p>
      </div>
      <div class="header-actions">
        <el-button @click="router.push('/pilot/closure')">试点闭环向导</el-button>
        <el-button :loading="loading" @click="reload">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col v-for="card in summaryCards" :key="card.label" :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">{{ card.label }}</div>
          <div class="summary-value" :class="card.tone">{{ card.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <el-tab-pane label="补偿任务" name="compensation">
        <el-card shadow="never" class="filter-bar">
          <el-form inline @submit.prevent="loadCompensation">
            <el-form-item label="状态">
              <el-select v-model="compFilters.compensation_status" clearable placeholder="全部" style="width: 140px">
                <el-option
                  v-for="s in meta?.compensation_statuses ?? []"
                  :key="s"
                  :label="compensationStatusLabel(s)"
                  :value="s"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="compFilters.compensation_type" clearable placeholder="全部" style="width: 160px">
                <el-option
                  v-for="t in meta?.compensation_types ?? []"
                  :key="t"
                  :label="compensationTypeLabel(t)"
                  :value="t"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="业务 ID">
              <el-input v-model="compFilters.business_id" clearable placeholder="代采单号等" style="width: 180px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadCompensation">查询</el-button>
              <el-button @click="resetCompFilters">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-table v-loading="loading" :data="compensationTasks" stripe>
          <el-table-column label="类型" width="140">
            <template #default="{ row }">{{ compensationTypeLabel(row.compensation_type) }}</template>
          </el-table-column>
          <el-table-column prop="business_id" label="业务 ID" width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="compensationStatusTagType(row.compensation_status)" size="small">
                {{ compensationStatusLabel(row.compensation_status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="retry_count" label="重试" width="72" />
          <el-table-column label="下次重试" width="168">
            <template #default="{ row }">{{ formatTime(row.next_retry_at) }}</template>
          </el-table-column>
          <el-table-column prop="last_error" label="最近错误" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCompensationDetail(row.id)">详情</el-button>
              <el-button
                v-if="canManage && canRetryCompensation(row.compensation_status)"
                link
                type="warning"
                @click="onRetryCompensation(row)"
              >
                人工重试
              </el-button>
              <el-button
                v-if="canManage && row.compensation_status === 'MANUAL_REQUIRED'"
                link
                type="danger"
                @click="onApproveCompensation(row)"
              >
                批准执行
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="Outbox 事件" name="outbox">
        <el-card shadow="never" class="filter-bar">
          <el-form inline @submit.prevent="loadOutbox">
            <el-form-item label="状态">
              <el-select v-model="outboxFilters.event_status" clearable placeholder="全部" style="width: 140px">
                <el-option
                  v-for="s in meta?.outbox_statuses ?? []"
                  :key="s"
                  :label="outboxStatusLabel(s)"
                  :value="s"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="业务 ID">
              <el-input v-model="outboxFilters.business_id" clearable placeholder="业务主键" style="width: 180px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadOutbox">查询</el-button>
              <el-button @click="resetOutboxFilters">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-table v-loading="loading" :data="outboxEvents" stripe>
          <el-table-column prop="event_type" label="事件类型" width="200" show-overflow-tooltip />
          <el-table-column prop="business_id" label="业务 ID" width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="outboxStatusTagType(row.event_status)" size="small">
                {{ outboxStatusLabel(row.event_status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="retry_count" label="重试" width="72" />
          <el-table-column label="下次重试" width="168">
            <template #default="{ row }">{{ formatTime(row.next_retry_at) }}</template>
          </el-table-column>
          <el-table-column prop="last_error" label="最近错误" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openOutboxDetail(row.id)">详情</el-button>
              <el-button
                v-if="canManage && canRetryOutbox(row.event_status)"
                link
                type="warning"
                @click="onRetryOutbox(row)"
              >
                人工重试
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <div v-if="total > 0" class="pager">
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadActiveTab"
      />
    </div>

    <el-drawer v-model="drawerVisible" :title="drawerTitle" size="480px" destroy-on-close>
      <div v-loading="drawerLoading" class="detail-drawer">
        <template v-if="drawerKind === 'compensation' && compDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="任务 ID">{{ compDetail.id }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ compensationTypeLabel(compDetail.compensation_type) }}</el-descriptions-item>
            <el-descriptions-item label="业务">{{ compDetail.business_type }} / {{ compDetail.business_id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ compensationStatusLabel(compDetail.compensation_status) }}</el-descriptions-item>
            <el-descriptions-item label="源事件">{{ compDetail.source_event_id || '—' }}</el-descriptions-item>
            <el-descriptions-item label="重试次数">{{ compDetail.retry_count }}</el-descriptions-item>
            <el-descriptions-item label="下次重试">{{ formatTime(compDetail.next_retry_at) }}</el-descriptions-item>
            <el-descriptions-item label="批准人">{{ compDetail.approved_by || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="compDetail.related_route" class="detail-block">
            <el-button type="primary" link @click="goRoute(compDetail.related_route!)">跳转业务单 →</el-button>
          </div>
          <div v-if="compDetail.last_error" class="detail-block">
            <div class="detail-label">错误堆栈 / 消息</div>
            <pre class="detail-pre error">{{ compDetail.last_error }}</pre>
          </div>
          <div v-if="compDetail.action_json" class="detail-block">
            <div class="detail-label">业务参数 (action_json)</div>
            <pre class="detail-pre">{{ formatJson(compDetail.action_json) }}</pre>
          </div>
        </template>
        <template v-else-if="drawerKind === 'outbox' && outboxDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="事件 ID">{{ outboxDetail.id }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ outboxDetail.event_type }}</el-descriptions-item>
            <el-descriptions-item label="业务">{{ outboxDetail.business_type }} / {{ outboxDetail.business_id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ outboxStatusLabel(outboxDetail.event_status) }}</el-descriptions-item>
            <el-descriptions-item label="幂等键">{{ outboxDetail.idempotency_key || '—' }}</el-descriptions-item>
            <el-descriptions-item label="重试次数">{{ outboxDetail.retry_count }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="outboxDetail.related_route" class="detail-block">
            <el-button type="primary" link @click="goRoute(outboxDetail.related_route!)">跳转业务单 →</el-button>
          </div>
          <div v-if="outboxDetail.last_error" class="detail-block">
            <div class="detail-label">错误堆栈 / 消息</div>
            <pre class="detail-pre error">{{ outboxDetail.last_error }}</pre>
          </div>
          <div v-if="outboxDetail.payload_json" class="detail-block">
            <div class="detail-label">业务参数 (payload_json)</div>
            <pre class="detail-pre">{{ formatJson(outboxDetail.payload_json) }}</pre>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-alert v-if="error" type="error" :title="error" show-icon class="error-banner" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveCompensationTask,
  getCompensationTaskDetail,
  getOutboxEventDetail,
  getSagaOpsFilterMeta,
  getSagaOpsSummary,
  listCompensationTasks,
  listOutboxEvents,
  retryCompensationTask,
  retryOutboxEvent,
  type CompensationTaskDetail,
  type CompensationTaskItem,
  type OutboxEventDetail,
  type OutboxEventItem,
  type SagaOpsFilterMeta,
  type SagaOpsSummary
} from '../api/sagaOps'
import { usePermission } from '../composables/usePermission'
import {
  agencyPurchaseCompensationStatusLabel,
  agencyPurchaseCompensationTypeLabel,
  compensationStatusTagType
} from '../constants/agencyPurchaseDict'

const router = useRouter()
const route = useRoute()
const { hasPermission } = usePermission()
const canManage = computed(() => hasPermission('SAGA_OPS_MANAGE'))

const loading = ref(false)
const error = ref('')
const summary = ref<SagaOpsSummary | null>(null)
const meta = ref<SagaOpsFilterMeta | null>(null)
const activeTab = ref<'compensation' | 'outbox'>('compensation')
const compensationTasks = ref<CompensationTaskItem[]>([])
const outboxEvents = ref<OutboxEventItem[]>([])
const pageNo = ref(1)
const pageSize = ref(20)
const total = ref(0)

const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drawerKind = ref<'compensation' | 'outbox'>('compensation')
const compDetail = ref<CompensationTaskDetail | null>(null)
const outboxDetail = ref<OutboxEventDetail | null>(null)

const drawerTitle = computed(() =>
  drawerKind.value === 'compensation' ? '补偿任务详情' : 'Outbox 事件详情'
)

const summaryCards = computed(() => [
  { label: 'Outbox 待处理', value: summary.value?.outbox_pending ?? 0, tone: 'warn' },
  { label: 'Outbox 失败', value: summary.value?.outbox_failed ?? 0, tone: 'danger' },
  { label: 'Outbox 待人工', value: summary.value?.outbox_manual_required ?? 0, tone: 'danger' },
  { label: '补偿待执行', value: summary.value?.compensation_pending ?? 0, tone: 'warn' },
  { label: '补偿失败', value: summary.value?.compensation_failed ?? 0, tone: 'danger' },
  { label: '补偿待人工', value: summary.value?.compensation_manual_required ?? 0, tone: 'danger' }
])

const compFilters = reactive({
  compensation_status: '',
  compensation_type: '',
  business_id: ''
})

const outboxFilters = reactive({
  event_status: '',
  business_id: ''
})

function compensationTypeLabel(code: string) {
  return agencyPurchaseCompensationTypeLabel(code)
}

function compensationStatusLabel(code: string) {
  return agencyPurchaseCompensationStatusLabel(code)
}

const OUTBOX_STATUS_LABELS: Record<string, string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  SUCCESS: '成功',
  FAILED: '失败',
  MANUAL_REQUIRED: '待人工'
}

function outboxStatusLabel(code: string) {
  return OUTBOX_STATUS_LABELS[code] ?? code
}

function outboxStatusTagType(status: string): 'success' | 'danger' | 'info' | 'warning' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'MANUAL_REQUIRED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

function formatTime(value?: string) {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

function formatJson(raw: string) {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function canRetryCompensation(status: string) {
  return status === 'FAILED' || status === 'MANUAL_REQUIRED'
}

function canRetryOutbox(status: string) {
  return status === 'FAILED' || status === 'MANUAL_REQUIRED'
}

function goRoute(path: string) {
  router.push(path)
}

async function promptManualReason(actionLabel: string): Promise<string | null> {
  try {
    const { value } = await ElMessageBox.prompt(`请填写${actionLabel}原因（至少 5 字，将写入审计日志）`, actionLabel, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (v) => {
        if (!v || v.trim().length < 5) return '原因至少 5 个字符'
        return true
      }
    })
    return value.trim()
  } catch {
    return null
  }
}

async function openCompensationDetail(id: string) {
  drawerKind.value = 'compensation'
  drawerVisible.value = true
  drawerLoading.value = true
  compDetail.value = null
  try {
    const res = await getCompensationTaskDetail(id)
    if (!res.success) throw new Error(res.message || '加载详情失败')
    compDetail.value = res.data as CompensationTaskDetail
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载详情失败')
    drawerVisible.value = false
  } finally {
    drawerLoading.value = false
  }
}

async function openOutboxDetail(id: string) {
  drawerKind.value = 'outbox'
  drawerVisible.value = true
  drawerLoading.value = true
  outboxDetail.value = null
  try {
    const res = await getOutboxEventDetail(id)
    if (!res.success) throw new Error(res.message || '加载详情失败')
    outboxDetail.value = res.data as OutboxEventDetail
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载详情失败')
    drawerVisible.value = false
  } finally {
    drawerLoading.value = false
  }
}

async function loadSummary() {
  const res = await getSagaOpsSummary()
  if (!res.success) throw new Error(res.message || '加载汇总失败')
  summary.value = res.data as SagaOpsSummary
}

async function loadMeta() {
  const res = await getSagaOpsFilterMeta()
  if (!res.success) throw new Error(res.message || '加载筛选项失败')
  meta.value = res.data as SagaOpsFilterMeta
}

async function loadCompensation() {
  loading.value = true
  error.value = ''
  try {
    const res = await listCompensationTasks({
      page_no: pageNo.value,
      page_size: pageSize.value,
      compensation_status: compFilters.compensation_status || undefined,
      compensation_type: compFilters.compensation_type || undefined,
      business_id: compFilters.business_id || undefined
    })
    if (!res.success) throw new Error(res.message || '加载补偿任务失败')
    compensationTasks.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadOutbox() {
  loading.value = true
  error.value = ''
  try {
    const res = await listOutboxEvents({
      page_no: pageNo.value,
      page_size: pageSize.value,
      event_status: outboxFilters.event_status || undefined,
      business_id: outboxFilters.business_id || undefined
    })
    if (!res.success) throw new Error(res.message || '加载 Outbox 失败')
    outboxEvents.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function loadActiveTab() {
  if (activeTab.value === 'compensation') {
    await loadCompensation()
  } else {
    await loadOutbox()
  }
}

async function reload() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadSummary(), loadMeta()])
    pageNo.value = 1
    await loadActiveTab()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '刷新失败'
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  pageNo.value = 1
  loadActiveTab()
}

function resetCompFilters() {
  compFilters.compensation_status = ''
  compFilters.compensation_type = ''
  compFilters.business_id = ''
  pageNo.value = 1
  loadCompensation()
}

function resetOutboxFilters() {
  outboxFilters.event_status = ''
  outboxFilters.business_id = ''
  pageNo.value = 1
  loadOutbox()
}

async function onRetryCompensation(row: CompensationTaskItem) {
  const reason = await promptManualReason('人工重试补偿')
  if (!reason) return
  const res = await retryCompensationTask(row.id, reason)
  if (!res.success) {
    ElMessage.error(res.message || '重试失败')
    return
  }
  ElMessage.success('已触发补偿重试')
  await reload()
}

async function onApproveCompensation(row: CompensationTaskItem) {
  const reason = await promptManualReason('批准执行补偿')
  if (!reason) return
  const res = await approveCompensationTask(row.id, reason)
  if (!res.success) {
    ElMessage.error(res.message || '批准执行失败')
    return
  }
  ElMessage.success('补偿任务已批准并执行')
  await reload()
}

async function onRetryOutbox(row: OutboxEventItem) {
  const reason = await promptManualReason('人工重试 Outbox')
  if (!reason) return
  const res = await retryOutboxEvent(row.id, reason)
  if (!res.success) {
    ElMessage.error(res.message || '重试失败')
    return
  }
  ElMessage.success('已触发 Outbox 重试')
  await reload()
}

onMounted(async () => {
  applyRouteQuery()
  await reload()
  const taskId = route.query.task_id as string | undefined
  if (taskId) {
    await openCompensationDetail(taskId)
  }
})

function applyRouteQuery() {
  const tab = route.query.tab as string | undefined
  if (tab === 'outbox') activeTab.value = 'outbox'
  const businessId = route.query.business_id as string | undefined
  if (businessId) {
    compFilters.business_id = businessId
    outboxFilters.business_id = businessId
  }
}
</script>

<style scoped>
.saga-ops-center {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.subtitle {
  margin: 4px 0 0;
  color: #666;
  font-size: 13px;
}
.summary-row {
  margin-bottom: 4px;
}
.summary-card {
  text-align: center;
}
.summary-label {
  color: #666;
  font-size: 13px;
}
.summary-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
}
.summary-value.warn {
  color: #e6a23c;
}
.summary-value.danger {
  color: #f56c6c;
}
.filter-bar {
  margin-bottom: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.error-banner {
  margin-top: 8px;
}
.detail-drawer {
  min-height: 120px;
}
.detail-block {
  margin-top: 16px;
}
.detail-label {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}
.detail-pre {
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  overflow: auto;
  max-height: 280px;
  white-space: pre-wrap;
  word-break: break-all;
}
.detail-pre.error {
  background: #fef0f0;
  color: #f56c6c;
}
</style>
