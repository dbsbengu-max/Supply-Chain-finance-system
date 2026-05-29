<template>
  <div class="audit-center">
    <div class="page-header">
      <div>
        <h2>审计日志中心</h2>
        <p class="subtitle">留痕放款、清分、风险、仓储、权限等关键操作，支撑 UAT 与合规验收</p>
      </div>
      <el-button :loading="loading" @click="reload">刷新</el-button>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col :xs="12" :sm="8" :md="4">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">近 7 日操作</div>
          <div class="summary-value">{{ summary?.total ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col
        v-for="item in summary?.by_object_type?.slice(0, 5)"
        :key="item.object_type"
        :xs="12"
        :sm="8"
        :md="4"
      >
        <el-card
          shadow="hover"
          class="summary-card"
          :class="{ active: filters.object_type === item.object_type }"
          @click="toggleObjectType(item.object_type)"
        >
          <div class="summary-label">{{ item.object_type_label }}</div>
          <div class="summary-value">{{ item.count }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="filter-bar">
      <el-form inline @submit.prevent="search">
        <el-form-item label="操作">
          <el-select v-model="filters.action" clearable filterable placeholder="全部" style="width: 180px">
            <el-option v-for="a in meta?.actions ?? []" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="filters.object_type" clearable filterable placeholder="全部" style="width: 160px">
            <el-option v-for="t in meta?.object_types ?? []" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象 ID">
          <el-input v-model="filters.object_id" clearable placeholder="业务主键" style="width: 160px" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable placeholder="动作/类型/ID" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="error" type="error" :title="error" show-icon class="error-banner" />

    <el-table v-loading="loading" :data="logs" stripe>
      <el-table-column label="时间" width="168">
        <template #default="{ row }">{{ formatTime(row.operation_at) }}</template>
      </el-table-column>
      <el-table-column prop="action_label" label="操作" width="140" show-overflow-tooltip />
      <el-table-column prop="object_type_label" label="对象类型" width="120" />
      <el-table-column prop="object_id" label="对象 ID" width="160" show-overflow-tooltip />
      <el-table-column prop="user_name" label="操作人" width="120" />
      <el-table-column prop="ip_address" label="IP" width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.related_route" link @click="goRelated(row)">业务单据</el-button>
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
    <el-empty v-if="!loading && !logs.length" description="暂无审计记录" />

    <el-drawer v-model="detailVisible" title="审计详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="操作">{{ detail.action_label }} ({{ detail.action }})</el-descriptions-item>
          <el-descriptions-item label="对象">
            {{ detail.object_type_label }} / {{ detail.object_id }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.user_name }}</el-descriptions-item>
          <el-descriptions-item label="企业 ID">{{ detail.enterprise_id || '—' }}</el-descriptions-item>
          <el-descriptions-item label="项目 ID">{{ detail.project_id || '—' }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ detail.ip_address || '—' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatTime(detail.operation_at) }}</el-descriptions-item>
        </el-descriptions>
        <div class="json-block">
          <div class="json-title">变更前</div>
          <pre>{{ prettyJson(detail.before_value) }}</pre>
        </div>
        <div class="json-block">
          <div class="json-title">变更后</div>
          <pre>{{ prettyJson(detail.after_value) }}</pre>
        </div>
        <el-button v-if="detail.related_route" type="primary" link @click="goRelated(detail)">跳转业务单据</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getAuditFilterMeta,
  getAuditLog,
  getAuditSummary,
  listAuditLogs,
  type AuditFilterMeta,
  type AuditLogDetail,
  type AuditLogItem,
  type AuditSummary
} from '../api/audit'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const logs = ref<AuditLogItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const summary = ref<AuditSummary | null>(null)
const meta = ref<AuditFilterMeta | null>(null)
const detailVisible = ref(false)
const detail = ref<AuditLogDetail | null>(null)

const filters = reactive({
  action: '',
  object_type: '',
  object_id: '',
  keyword: ''
})

function formatTime(iso: string) {
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return iso
  }
}

function prettyJson(raw?: string) {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function toggleObjectType(objectType: string) {
  filters.object_type = filters.object_type === objectType ? '' : objectType
  search()
}

async function loadSummary() {
  try {
    const res = await getAuditSummary(7)
    summary.value = res.data
  } catch {
    summary.value = null
  }
}

async function loadMeta() {
  try {
    const res = await getAuditFilterMeta()
    meta.value = res.data
  } catch {
    meta.value = null
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listAuditLogs({
      page_no: pageNo.value,
      page_size: pageSize.value,
      action: filters.action || undefined,
      object_type: filters.object_type || undefined,
      object_id: filters.object_id || undefined,
      keyword: filters.keyword || undefined
    })
    logs.value = res.data.records ?? []
    total.value = res.data.total ?? 0
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '加载审计日志失败'
    logs.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  pageNo.value = 1
  load()
}

function resetFilters() {
  filters.action = ''
  filters.object_type = ''
  filters.object_id = ''
  filters.keyword = ''
  search()
}

async function openDetail(row: AuditLogItem) {
  try {
    const res = await getAuditLog(row.id)
    detail.value = res.data
    detailVisible.value = true
  } catch {
    ElMessage.error('加载审计详情失败')
  }
}

function goRelated(row: { related_route?: string }) {
  if (!row.related_route) return
  router.push(row.related_route)
}

async function reload() {
  await Promise.all([loadSummary(), loadMeta(), load()])
}

onMounted(async () => {
  if (typeof route.query.object_type === 'string') {
    filters.object_type = route.query.object_type
  }
  if (typeof route.query.object_id === 'string') {
    filters.object_id = route.query.object_id
  }
  await reload()
})
</script>

<style scoped>
.audit-center {
  display: flex;
  flex-direction: column;
  gap: 16px;
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
}
.summary-row {
  margin-bottom: 0;
}
.summary-card {
  cursor: pointer;
  margin-bottom: 8px;
}
.summary-card.active {
  border-color: var(--el-color-primary);
}
.summary-label {
  color: #666;
  font-size: 13px;
}
.summary-value {
  font-size: 24px;
  font-weight: 600;
  margin-top: 4px;
}
.filter-bar {
  margin-bottom: 0;
}
.error-banner {
  margin-bottom: 0;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.json-block {
  margin-top: 16px;
}
.json-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.json-block pre {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  overflow: auto;
  max-height: 240px;
  font-size: 12px;
}
</style>
