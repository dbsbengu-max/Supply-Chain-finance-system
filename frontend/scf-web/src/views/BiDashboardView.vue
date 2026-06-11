<template>
  <div class="bi-dashboard">
    <div class="page-header">
      <div>
        <h2>经营看板</h2>
        <p v-if="overview" class="as-of">数据截至 {{ formatAsOf(overview.as_of) }}</p>
      </div>
      <div class="header-actions">
        <el-button v-if="canExport" :loading="exporting" type="primary" @click="onExport">
          导出看板
        </el-button>
        <el-button :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="fromPilot"
      type="success"
      show-icon
      :closable="false"
      class="pilot-banner"
      title="试点闭环视图"
      description="当前从试点闭环 / 代采详情进入。请核对 KPI 与融资状态分布是否与业务单一致。"
    />
    <div v-if="fromPilot" class="pilot-banner-actions">
      <el-button link type="primary" @click="router.push('/pilot/closure')">返回试点向导</el-button>
    </div>

    <el-card class="filter-bar" shadow="never">
      <el-form inline @submit.prevent>
        <el-form-item label="趋势窗口">
          <el-select v-model="filters.months" style="width: 120px" @change="reloadTrend">
            <el-option :value="3" label="近 3 月" />
            <el-option :value="6" label="近 6 月" />
            <el-option :value="12" label="近 12 月" />
          </el-select>
        </el-form-item>
        <el-form-item label="展示币种">
          <el-tag type="info">{{ currency }}</el-tag>
        </el-form-item>
        <el-form-item v-if="canDrilldown" label="告警预览">
          <el-select v-model="filters.severity" clearable placeholder="全部级别" style="width: 130px">
            <el-option value="HIGH" label="高" />
            <el-option value="MEDIUM" label="中" />
            <el-option value="LOW" label="低" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="error" type="error" :title="error" show-icon closable class="error-banner" @close="error = ''" />

    <el-row v-loading="loading" :gutter="16" class="kpi-row">
      <el-col v-for="card in kpiCards" :key="card.key" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card shadow="hover" class="kpi-card" :class="{ clickable: card.key === 'risk' && canDrilldown }" @click="onKpiClick(card.key)">
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value">{{ card.value }}</div>
          <div v-if="card.hint" class="kpi-hint">{{ card.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :xs="24" :lg="14">
        <el-card>
          <template #header>贸易趋势（近 {{ filters.months }} 月）</template>
          <el-empty v-if="!trendPoints.length && !loading" description="暂无趋势数据" />
          <BiTrendChart v-else :points="trendPoints" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card>
          <template #header>融资状态分布</template>
          <el-empty v-if="!financeBuckets.length && !loading" description="暂无融资数据" />
          <BiFinanceStatusChart v-else :buckets="financeBuckets" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :xs="24" :lg="10">
        <el-card>
          <template #header>清分构成</template>
          <el-empty v-if="!clearingSummary && !loading" description="暂无清分数据" />
          <BiClearingChart
            v-else-if="clearingSummary"
            :principal="clearingSummary.repaid_principal_total"
            :interest="clearingSummary.repaid_interest_total"
            :fee="clearingSummary.repaid_fee_total"
            :currency="clearingSummary.currency"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card>
          <template #header>
            <div class="card-header-row">
              <span>风险告警</span>
              <el-button v-if="canDrilldown && (canViewInbox || canViewRiskCenter)" link type="primary" @click="goRiskCenter()">
                {{ canViewInbox ? '待办中心' : '预警中心' }}
              </el-button>
              <el-button v-else-if="canDrilldown" link type="primary" @click="riskDrawerOpen = true">
                查看明细
              </el-button>
            </div>
          </template>
          <template v-if="canDrilldown">
            <el-empty v-if="!filteredRiskPreview.length && !loading" description="暂无告警" />
            <el-table v-else :data="filteredRiskPreview" size="small" max-height="280">
              <el-table-column prop="severity" label="级别" width="80">
                <template #default="{ row }">
                  <el-tag :type="severityType(row.severity)" size="small">{{ row.severity }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="标题" width="120" />
              <el-table-column prop="message" label="说明" show-overflow-tooltip />
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="canViewInbox || canViewRiskCenter"
                    link
                    type="primary"
                    @click="goRiskCenter(row.code)"
                  >
                    {{ canViewInbox ? '待办' : '预警' }}
                  </el-button>
                  <el-button v-else link type="primary" @click="openRiskDrawer(row.code)">下钻</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="当前身份无 BI 下钻权限，仅展示 KPI 汇总">
            <template v-if="overview" #description>
              <p>当前身份无 BI 下钻权限</p>
              <p class="muted">风险告警数：{{ overview.risk_alert_count }}</p>
            </template>
          </el-empty>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="section-row">
      <el-col :xs="24" :md="8">
        <el-card>
          <template #header>融资摘要</template>
          <el-descriptions v-if="financeSummary" :column="1" size="small" border>
            <el-descriptions-item label="融资单数">{{ financeSummary.total_count }}</el-descriptions-item>
            <el-descriptions-item label="放款成功">
              {{ financeSummary.disbursement_success_count }} 笔 /
              {{ formatMoney(financeSummary.disbursement_success_amount, financeSummary.currency) }}
            </el-descriptions-item>
            <el-descriptions-item label="凭证融资">
              {{ financeSummary.voucher_finance_count }} 笔 /
              {{ formatMoney(financeSummary.voucher_finance_amount, financeSummary.currency) }}
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="加载中…" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card>
          <template #header>仓储摘要</template>
          <el-descriptions v-if="warehouseSummary" :column="1" size="small" border>
            <el-descriptions-item label="库存批次数">{{ warehouseSummary.inventory_lot_count }}</el-descriptions-item>
            <el-descriptions-item label="货值合计">
              {{ formatMoney(warehouseSummary.valuation_total, warehouseSummary.currency) }}
            </el-descriptions-item>
            <el-descriptions-item label="质押数量">{{ warehouseSummary.pledged_quantity_total }}</el-descriptions-item>
            <el-descriptions-item label="盘点异常">{{ warehouseSummary.stocktake_exception_count }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="加载中…" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card>
          <template #header>清分摘要</template>
          <el-descriptions v-if="clearingSummary" :column="1" size="small" border>
            <el-descriptions-item label="已执行清分">{{ clearingSummary.executed_clearing_count }}</el-descriptions-item>
            <el-descriptions-item label="已还本金">
              {{ formatMoney(clearingSummary.repaid_principal_total, clearingSummary.currency) }}
            </el-descriptions-item>
            <el-descriptions-item label="未匹配入账">
              {{ clearingSummary.unmatched_inflow_count }} 笔 /
              {{ formatMoney(clearingSummary.unmatched_inflow_amount, clearingSummary.currency) }}
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="加载中…" />
        </el-card>
      </el-col>
    </el-row>

    <BiRiskDetailDrawer
      v-if="canDrilldown && !canViewRiskCenter"
      v-model="riskDrawerOpen"
      :initial-code="drawerCodeFilter"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import BiClearingChart from '../components/bi/BiClearingChart.vue'
import BiFinanceStatusChart from '../components/bi/BiFinanceStatusChart.vue'
import BiRiskDetailDrawer from '../components/bi/BiRiskDetailDrawer.vue'
import BiTrendChart from '../components/bi/BiTrendChart.vue'
import {
  exportBiDashboard,
  getBiClearingSummary,
  getBiFinanceSummary,
  getBiOverview,
  getBiRiskAlerts,
  getBiTradeTrend,
  getBiWarehouseSummary,
  type BiOverview,
  type BiRiskAlertItem,
  type BiStatusBucket,
  type BiTrendPoint
} from '../api/bi'
import { usePermission } from '../composables/usePermission'
import { formatMoney } from '../utils/format'

const { hasPermission } = usePermission()
const route = useRoute()
const router = useRouter()
const fromPilot = computed(() => route.query.from === 'pilot')
const canExport = computed(() => hasPermission('BI_EXPORT'))
const canDrilldown = computed(() => hasPermission('BI_DRILLDOWN'))
const canViewRiskCenter = computed(() => hasPermission('RISK_ALERT_VIEW'))
const canViewInbox = computed(() => hasPermission('INBOX_VIEW'))

const loading = ref(false)
const exporting = ref(false)
const error = ref('')
const overview = ref<BiOverview | null>(null)
const trendPoints = ref<BiTrendPoint[]>([])
const financeSummary = ref<any>(null)
const warehouseSummary = ref<any>(null)
const clearingSummary = ref<any>(null)
const riskAlerts = ref<BiRiskAlertItem[]>([])
const riskDrawerOpen = ref(false)
const drawerCodeFilter = ref<string>('')

const filters = reactive({
  months: 6,
  severity: '' as string
})

const currency = computed(() => overview.value?.currency ?? 'CNY')
const financeBuckets = computed<BiStatusBucket[]>(() => financeSummary.value?.by_status ?? [])

const filteredRiskPreview = computed(() => {
  const list = riskAlerts.value.slice(0, 8)
  if (!filters.severity) return list
  return list.filter((a) => a.severity === filters.severity)
})

const kpiCards = computed(() => {
  const o = overview.value
  if (!o) return []
  return [
    { key: 'orders', label: '订单笔数', value: String(o.order_count), hint: formatMoney(o.order_amount_total, o.currency) },
    { key: 'finance', label: '融资单数', value: String(o.finance_count), hint: `在贷 ${o.outstanding_finance_count} 笔` },
    { key: 'disbursed', label: '累计放款', value: formatMoney(o.disbursed_amount_total, o.currency) },
    { key: 'inventory', label: '库存批次', value: String(o.inventory_lot_count), hint: formatMoney(o.inventory_valuation_total, o.currency) },
    { key: 'clearing', label: '清分执行', value: String(o.clearing_executed_count), hint: `已还 ${formatMoney(o.repaid_amount_total, o.currency)}` },
    { key: 'voucher', label: '凭证融资', value: String(o.voucher_finance_count) },
    { key: 'risk', label: '风险告警', value: String(o.risk_alert_count), hint: canDrilldown.value ? (canViewInbox.value ? '点击进入待办中心' : canViewRiskCenter.value ? '点击进入预警中心' : '点击查看明细') : undefined }
  ]
})

function formatAsOf(iso: string) {
  try {
    return new Date(iso).toLocaleString('zh-CN')
  } catch {
    return iso
  }
}

function severityType(severity: string) {
  if (severity === 'HIGH') return 'danger'
  if (severity === 'MEDIUM') return 'warning'
  return 'info'
}

function goRiskCenter(alertCode?: string) {
  if (canViewInbox.value) {
    router.push({
      path: '/inbox',
      query: { source: 'RISK', ...(alertCode ? { alert_code: alertCode } : {}) }
    })
    return
  }
  router.push({
    path: '/risk/alerts',
    query: alertCode ? { alert_code: alertCode } : undefined
  })
}

function openRiskDrawer(code?: string) {
  drawerCodeFilter.value = code ?? ''
  riskDrawerOpen.value = true
}

function onKpiClick(key: string) {
  if (key === 'risk' && canDrilldown.value) {
    if (canViewInbox.value || canViewRiskCenter.value) {
      goRiskCenter()
    } else {
      drawerCodeFilter.value = ''
      riskDrawerOpen.value = true
    }
  }
}

async function reloadTrend() {
  try {
    const trend = await getBiTradeTrend(filters.months)
    trendPoints.value = trend.data?.points ?? []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '趋势数据加载失败')
  }
}

async function loadRiskPreview() {
  if (!canDrilldown.value) {
    riskAlerts.value = []
    return
  }
  const risk = await getBiRiskAlerts()
  if (!risk.success) throw new Error(risk.message || '风险告警加载失败')
  riskAlerts.value = risk.data?.alerts ?? []
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const tasks: Promise<unknown>[] = [
      getBiOverview(),
      getBiTradeTrend(filters.months),
      getBiFinanceSummary(),
      getBiWarehouseSummary(),
      getBiClearingSummary()
    ]
    const results = await Promise.all(tasks)
    const [ov, trend, finance, warehouse, clearing] = results as any[]

    if (!ov.success) throw new Error(ov.message || '概览加载失败')
    overview.value = ov.data
    trendPoints.value = trend.data?.points ?? []
    financeSummary.value = finance.data
    warehouseSummary.value = warehouse.data
    clearingSummary.value = clearing.data

    await loadRiskPreview()
  } catch (e: any) {
    error.value = e?.response?.data?.message || e.message || '看板数据加载失败'
  } finally {
    loading.value = false
  }
}

async function onExport() {
  if (!canExport.value) return
  exporting.value = true
  try {
    const res = await exportBiDashboard()
    if (!res.success) throw new Error(res.message || '导出失败')
    ElMessage.success(res.data?.message || '导出任务已受理')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.bi-dashboard { padding-bottom: 24px; }
.pilot-banner { margin-bottom: 4px; }
.pilot-banner-actions { margin-bottom: 12px; }
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 16px;
}
.page-header h2 { margin: 0 0 4px; }
.as-of { margin: 0; font-size: 13px; color: #909399; }
.header-actions { display: flex; gap: 8px; flex-shrink: 0; }
.filter-bar { margin-bottom: 16px; }
.filter-bar :deep(.el-form-item) { margin-bottom: 0; }
.error-banner { margin-bottom: 16px; }
.kpi-row { margin-bottom: 16px; }
.kpi-card { min-height: 96px; }
.kpi-card.clickable { cursor: pointer; }
.kpi-label { font-size: 13px; color: #909399; }
.kpi-value { font-size: 22px; font-weight: 600; margin-top: 8px; }
.kpi-hint { font-size: 12px; color: #606266; margin-top: 6px; }
.section-row { margin-bottom: 16px; }
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.muted { color: #909399; font-size: 13px; margin-top: 4px; }
</style>
