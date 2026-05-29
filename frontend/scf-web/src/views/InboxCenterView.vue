<template>
  <div class="inbox-center">
    <div class="page-header">
      <div>
        <h2>消息与待办中心</h2>
        <p class="subtitle">统一汇聚 BPM、风险预警、清分审批、放款确认、仓储异常</p>
      </div>
      <div class="header-actions">
        <el-switch v-model="unreadOnly" active-text="仅未读" @change="load" />
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col :xs="12" :sm="8" :md="4" v-for="card in summaryCards" :key="card.key">
        <el-card shadow="hover" class="summary-card" :class="{ active: filters.source === card.key }" @click="toggleSource(card.key)">
          <div class="summary-label">{{ card.label }}</div>
          <div class="summary-value">{{ card.count }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="summary-card unread-card">
          <div class="summary-label">未读</div>
          <div class="summary-value">{{ summary?.unread_count ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-alert v-if="error" type="error" :title="error" show-icon class="error-banner" />

    <el-table v-loading="loading" :data="events" stripe>
      <el-table-column label="来源" width="110">
        <template #default="{ row }">
          <el-tag size="small">{{ sourceLabel(row.source) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="88">
        <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
      </el-table-column>
      <el-table-column label="级别" width="88">
        <template #default="{ row }">
          <el-tag :type="severityType(row.severity)" size="small">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="160" show-overflow-tooltip />
      <el-table-column prop="message" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column prop="business_label" label="业务对象" width="140" show-overflow-tooltip />
      <el-table-column label="时间" width="168">
        <template #default="{ row }">{{ formatTime(row.occurred_at) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.read ? 'info' : 'danger'" size="small">{{ row.read ? '已读' : '未读' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.action_route" link type="primary" @click="goAction(row)">去处理</el-button>
          <el-button v-if="!row.read" link @click="markRead(row)">标记已读</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !events.length" description="暂无待办或通知" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getInboxFeed,
  markInboxEventRead,
  INBOX_CATEGORY_LABELS,
  INBOX_SOURCE_LABELS,
  type InboxEvent,
  type InboxSummary
} from '../api/inbox'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const unreadOnly = ref(false)
const events = ref<InboxEvent[]>([])
const summary = ref<InboxSummary | null>(null)

const filters = reactive({
  source: '' as string
})

const summaryCards = computed(() => {
  const bySource = summary.value?.by_source ?? {}
  return [
    { key: 'BPM', label: 'BPM', count: bySource.BPM ?? 0 },
    { key: 'RISK', label: '风险', count: bySource.RISK ?? 0 },
    { key: 'CLEARING', label: '清分', count: bySource.CLEARING ?? 0 },
    { key: 'DISBURSE', label: '放款', count: bySource.DISBURSE ?? 0 },
    { key: 'WAREHOUSE', label: '仓储', count: bySource.WAREHOUSE ?? 0 }
  ]
})

function sourceLabel(source: string) {
  return INBOX_SOURCE_LABELS[source] ?? source
}

function categoryLabel(category: string) {
  return INBOX_CATEGORY_LABELS[category] ?? category
}

function severityType(severity: string) {
  if (severity === 'HIGH') return 'danger'
  if (severity === 'MEDIUM') return 'warning'
  return 'info'
}

function formatTime(iso: string) {
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return iso
  }
}

function toggleSource(source: string) {
  filters.source = filters.source === source ? '' : source
  load()
}

function applyRouteQuery() {
  const source = route.query.source
  if (typeof source === 'string') {
    filters.source = source
  }
  const unread = route.query.unread_only
  if (unread === '1' || unread === 'true') {
    unreadOnly.value = true
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params: Record<string, string | boolean | number> = { limit: 100 }
    if (filters.source) params.source = filters.source
    if (unreadOnly.value) params.unread_only = true
    const res = await getInboxFeed(params)
    if (!res.success) throw new Error(res.message || '加载失败')
    summary.value = res.data?.summary ?? null
    events.value = res.data?.events ?? []
  } catch (e: any) {
    error.value = e?.response?.data?.message || e.message || '待办中心加载失败'
    events.value = []
    summary.value = null
  } finally {
    loading.value = false
  }
}

async function markRead(row: InboxEvent) {
  try {
    const res = await markInboxEventRead(row.event_key)
    if (!res.success) throw new Error(res.message || '标记失败')
    row.read = true
    if (summary.value) {
      summary.value = {
        ...summary.value,
        unread_count: Math.max(0, summary.value.unread_count - 1)
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '标记已读失败')
  }
}

async function goAction(row: InboxEvent) {
  if (!row.read) {
    await markRead(row)
  }
  if (row.action_route) {
    router.push(row.action_route)
  }
}

onMounted(() => {
  applyRouteQuery()
  load()
})
</script>

<style scoped>
.inbox-center { padding-bottom: 24px; }
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.page-header h2 { margin: 0 0 4px; }
.subtitle { margin: 0; font-size: 13px; color: #909399; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.summary-row { margin-bottom: 16px; }
.summary-card { cursor: pointer; min-height: 72px; }
.summary-card.active { border-color: var(--el-color-primary); }
.summary-card.unread-card { cursor: default; }
.summary-label { font-size: 13px; color: #909399; }
.summary-value { font-size: 22px; font-weight: 600; margin-top: 8px; }
.error-banner { margin-bottom: 16px; }
</style>
