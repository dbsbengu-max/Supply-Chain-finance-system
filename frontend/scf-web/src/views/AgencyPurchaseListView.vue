<template>
  <div>
    <div class="toolbar">
      <h2>贸易代采申请</h2>
      <el-button v-if="canCreate" type="primary" @click="goCreate">新建申请</el-button>
    </div>

    <el-form :inline="true" class="filters" @submit.prevent="load">
      <el-form-item label="状态">
        <el-select v-model="filters.application_status" clearable placeholder="全部" style="width: 140px">
          <el-option
            v-for="item in meta?.application_statuses || []"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Saga">
        <el-select v-model="filters.saga_status" clearable placeholder="全部" style="width: 120px">
          <el-option
            v-for="item in meta?.saga_statuses || []"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="订单/备货">
        <el-select v-model="filters.order_mode" clearable placeholder="全部" style="width: 140px">
          <el-option
            v-for="item in meta?.order_modes || []"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="资金">
        <el-select v-model="filters.fund_source" clearable placeholder="全部" style="width: 140px">
          <el-option
            v-for="item in meta?.fund_sources || []"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="提货方式">
        <el-select v-model="filters.pickup_type" clearable placeholder="全部" style="width: 140px">
          <el-option
            v-for="item in meta?.pickup_types || []"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="客户 ID">
        <el-input v-model="filters.customer_id" clearable placeholder="ENT_MEMBER_001" style="width: 160px" />
      </el-form-item>
      <el-form-item label="创建日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始"
          end-placeholder="结束"
          style="width: 260px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="applications" v-loading="loading" stripe>
      <template #empty>
        <ListEmptyState description="暂无代采申请" action-label="新建申请" @action="goCreate" />
      </template>
      <el-table-column prop="application_no" label="申请单号" width="180" />
      <el-table-column label="代采模式" min-width="220">
        <template #default="{ row }">
          {{ agencyPurchaseModeLabel(meta, row.mode_key) }}
        </template>
      </el-table-column>
      <el-table-column prop="customer_id" label="客户" width="140" />
      <el-table-column label="金额" width="160">
        <template #default="{ row }">{{ formatMoney(row.total_amount, row.currency) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          {{ agencyPurchaseStatusLabel(meta, row.application_status) }}
        </template>
      </el-table-column>
      <el-table-column label="Saga" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.saga_status" :type="sagaStatusTagType(row.saga_status)" size="small">
            {{ agencyPurchaseSagaStatusLabel(row.saga_status) }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="created_at" label="创建时间" width="180" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link @click="goDetail(row.id)">详情</el-button>
          <el-button
            v-if="canCreate && isDraftStatus(row.application_status)"
            link
            type="warning"
            @click="goEdit(row.id)"
          >编辑</el-button>
          <el-button
            v-if="canSubmit && isDraftStatus(row.application_status)"
            link
            type="primary"
            @click="onSubmit(row.id)"
          >提交</el-button>
          <el-button
            v-if="canCancel && isCancellableStatus(row.application_status)"
            link
            type="danger"
            @click="onCancel(row.id)"
          >取消</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAgencyPurchaseApplication,
  listAgencyPurchaseApplications,
  submitAgencyPurchaseApplication,
  type AgencyPurchaseApplication,
  type AgencyPurchaseMeta
} from '../api/agencyPurchase'
import {
  agencyPurchaseModeLabel,
  agencyPurchaseSagaStatusLabel,
  agencyPurchaseStatusLabel,
  isCancellableStatus,
  isDraftStatus,
  loadAgencyPurchaseMeta,
  sagaStatusTagType
} from '../constants/agencyPurchaseDict'
import { usePermission } from '../composables/usePermission'
import { formatMoney } from '../utils/format'
import { apiErrorMessage } from '../utils/apiError'
import ListEmptyState from '../components/ListEmptyState.vue'

const router = useRouter()
const { hasPermission } = usePermission()
const canCreate = computed(() => hasPermission('AGENCY_PURCHASE_CREATE'))
const canSubmit = computed(() => hasPermission('AGENCY_PURCHASE_SUBMIT'))
const canCancel = computed(() => hasPermission('AGENCY_PURCHASE_CANCEL'))

const loading = ref(false)
const meta = ref<AgencyPurchaseMeta | null>(null)
const applications = ref<AgencyPurchaseApplication[]>([])
const dateRange = ref<[string, string] | null>(null)
const filters = reactive({
  application_status: '',
  saga_status: '',
  order_mode: '',
  fund_source: '',
  pickup_type: '',
  customer_id: ''
})

async function loadMeta() {
  meta.value = await loadAgencyPurchaseMeta()
}

async function load() {
  loading.value = true
  try {
    const res = await listAgencyPurchaseApplications({
      page_no: 1,
      page_size: 50,
      application_status: filters.application_status || undefined,
      saga_status: filters.saga_status || undefined,
      order_mode: filters.order_mode || undefined,
      fund_source: filters.fund_source || undefined,
      pickup_type: filters.pickup_type || undefined,
      customer_id: filters.customer_id || undefined,
      created_from: dateRange.value?.[0],
      created_to: dateRange.value?.[1]
    })
    if (res.success) applications.value = res.data?.records || []
  } catch (e: unknown) {
    ElMessage.error(apiErrorMessage(e, '加载失败'))
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.application_status = ''
  filters.saga_status = ''
  filters.order_mode = ''
  filters.fund_source = ''
  filters.pickup_type = ''
  filters.customer_id = ''
  dateRange.value = null
  load()
}

function goCreate() {
  router.push('/agency-purchase/applications/new')
}

function goEdit(id: string) {
  router.push(`/agency-purchase/applications/${id}/edit`)
}

function goDetail(id: string) {
  router.push(`/agency-purchase/applications/${id}`)
}

async function onSubmit(id: string) {
  try {
    const res = await submitAgencyPurchaseApplication(id)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('已提交，进入 BPM 待办占位')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  }
}

async function onCancel(id: string) {
  try {
    await ElMessageBox.confirm('确认取消该代采申请？', '提示', { type: 'warning' })
    const res = await cancelAgencyPurchaseApplication(id)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('已取消')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '取消失败')
  }
}

onMounted(async () => {
  await loadMeta()
  await load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.filters {
  margin-bottom: 16px;
}
</style>
