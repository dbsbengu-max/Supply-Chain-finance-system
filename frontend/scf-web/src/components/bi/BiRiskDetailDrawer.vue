<template>
  <el-drawer
    :model-value="modelValue"
    title="风险告警明细"
    size="640px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="drawer-toolbar">
      <el-select v-model="severity" clearable placeholder="全部级别" style="width: 140px">
        <el-option label="高 (HIGH)" value="HIGH" />
        <el-option label="中 (MEDIUM)" value="MEDIUM" />
        <el-option label="低 (LOW)" value="LOW" />
      </el-select>
      <el-select v-model="code" clearable placeholder="全部类型" style="width: 180px">
        <el-option v-for="c in codeOptions" :key="c" :label="codeLabel(c)" :value="c" />
      </el-select>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert v-if="error" type="error" :title="error" show-icon class="drawer-error" />

    <el-table v-loading="loading" :data="filteredAlerts" size="small" max-height="calc(100vh - 200px)">
      <el-table-column prop="severity" label="级别" width="88">
        <template #default="{ row }">
          <el-tag :type="severityType(row.severity)" size="small">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" width="120" />
      <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
      <el-table-column prop="related_id" label="关联 ID" width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="88" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goRelated(row)">下钻</el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <span class="footer-hint">共 {{ filteredAlerts.length }} 条（总计 {{ alertCount }}）</span>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getBiRiskAlerts, type BiRiskAlertItem } from '../../api/bi'

const props = defineProps<{
  modelValue: boolean
  initialCode?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const router = useRouter()
const loading = ref(false)
const error = ref('')
const alerts = ref<BiRiskAlertItem[]>([])
const alertCount = ref(0)
const severity = ref<string>('')
const code = ref<string>('')

const codeOptions = computed(() => [...new Set(alerts.value.map((a) => a.code))])

const filteredAlerts = computed(() =>
  alerts.value.filter((a) => {
    if (severity.value && a.severity !== severity.value) return false
    if (code.value && a.code !== code.value) return false
    return true
  })
)

function severityType(s: string) {
  if (s === 'HIGH') return 'danger'
  if (s === 'MEDIUM') return 'warning'
  return 'info'
}

function codeLabel(c: string) {
  const map: Record<string, string> = {
    FINANCE_OVERDUE: '融资逾期',
    BANK_FLOW_UNMATCHED: '未匹配流水',
    PRICE_ABNORMAL: '价格异常',
    INVENTORY_STOCKTAKE: '盘点异常'
  }
  return map[c] ?? c
}

function goRelated(row: BiRiskAlertItem) {
  const path = resolveRiskPath(row)
  emit('update:modelValue', false)
  router.push(path)
}

function resolveRiskPath(row: BiRiskAlertItem): string {
  switch (row.related_type) {
    case 'FINANCE':
      return '/finance/applications'
    case 'BANK_FLOW':
      return '/accounts/bank-flows'
    case 'PRICE':
      return '/pricing'
    case 'INVENTORY':
      return row.related_id ? `/warehouse/inventories/${row.related_id}` : '/warehouse/inventories'
    default:
      return '/bi/dashboard'
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getBiRiskAlerts()
    if (!res.success) throw new Error(res.message || '加载失败')
    alerts.value = res.data?.alerts ?? []
    alertCount.value = res.data?.alert_count ?? alerts.value.length
  } catch (e: any) {
    error.value = e?.response?.data?.message || e.message || '风险明细加载失败'
    alerts.value = []
    alertCount.value = 0
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      code.value = props.initialCode ?? ''
      load()
    }
  }
)
</script>

<style scoped>
.drawer-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.drawer-error { margin-bottom: 12px; }
.footer-hint { font-size: 13px; color: #909399; }
</style>
