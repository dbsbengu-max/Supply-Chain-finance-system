<template>
  <div v-loading="loading">
    <div class="toolbar">
      <h2>代采申请详情</h2>
      <div class="actions">
        <el-button @click="goBack">返回列表</el-button>
        <el-button
          v-if="canCreate && app && isDraftStatus(app.application_status)"
          type="warning"
          @click="goEdit"
        >编辑</el-button>
        <el-button
          v-if="canSubmit && app && isDraftStatus(app.application_status)"
          type="primary"
          @click="onSubmit"
        >提交</el-button>
        <el-button
          v-if="canCancel && app && isCancellableStatus(app.application_status)"
          type="danger"
          @click="onCancel"
        >取消</el-button>
      </div>
    </div>

    <el-descriptions v-if="app" :column="2" border>
      <el-descriptions-item label="申请单号">{{ app.application_no }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        {{ agencyPurchaseStatusLabel(meta, app.application_status) }}
      </el-descriptions-item>
      <el-descriptions-item label="代采模式" :span="2">
        {{ agencyPurchaseModeLabel(meta, app.mode_key) }}
      </el-descriptions-item>
      <el-descriptions-item label="订单/备货">
        {{ agencyPurchaseOrderModeLabel(meta, app.order_mode) }}
      </el-descriptions-item>
      <el-descriptions-item label="资金来源">
        {{ agencyPurchaseFundSourceLabel(meta, app.fund_source) }}
      </el-descriptions-item>
      <el-descriptions-item label="提货方式">
        {{ agencyPurchasePickupTypeLabel(meta, app.pickup_type) }}
      </el-descriptions-item>
      <el-descriptions-item label="客户 ID">{{ app.customer_id }}</el-descriptions-item>
      <el-descriptions-item label="贸易公司 ID">{{ app.trade_company_id }}</el-descriptions-item>
      <el-descriptions-item label="关联订单 ID">{{ app.order_id || '-' }}</el-descriptions-item>
      <el-descriptions-item label="金额">{{ app.total_amount }} {{ app.currency }}</el-descriptions-item>
      <el-descriptions-item label="BPM 实例">{{ app.bpm_instance_id || '未启动' }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ app.created_at }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ app.remark || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-divider content-position="left">跨域动作（占位）</el-divider>
    <div class="cross-domain">
      <el-tooltip
        v-for="action in meta?.cross_domain_actions || []"
        :key="action.code"
        :content="action.hint"
        placement="top"
      >
        <span>
          <el-button disabled>{{ action.label }}</el-button>
        </span>
      </el-tooltip>
    </div>
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="以上按钮为跨域动作占位，待 Saga/资金/仓储模块接入后启用。"
      style="margin-top: 12px"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAgencyPurchaseApplication,
  getAgencyPurchaseApplication,
  submitAgencyPurchaseApplication,
  type AgencyPurchaseApplication,
  type AgencyPurchaseMeta
} from '../api/agencyPurchase'
import {
  agencyPurchaseFundSourceLabel,
  agencyPurchaseModeLabel,
  agencyPurchaseOrderModeLabel,
  agencyPurchasePickupTypeLabel,
  agencyPurchaseStatusLabel,
  isCancellableStatus,
  isDraftStatus,
  loadAgencyPurchaseMeta
} from '../constants/agencyPurchaseDict'
import { usePermission } from '../composables/usePermission'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()
const canCreate = computed(() => hasPermission('AGENCY_PURCHASE_CREATE'))
const canSubmit = computed(() => hasPermission('AGENCY_PURCHASE_SUBMIT'))
const canCancel = computed(() => hasPermission('AGENCY_PURCHASE_CANCEL'))

const loading = ref(false)
const meta = ref<AgencyPurchaseMeta | null>(null)
const app = ref<AgencyPurchaseApplication | null>(null)
const applicationId = computed(() => route.params.id as string)

async function load() {
  loading.value = true
  try {
    meta.value = await loadAgencyPurchaseMeta()
    const res = await getAgencyPurchaseApplication(applicationId.value)
    if (!res.success) throw new Error(res.message)
    app.value = res.data
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
    goBack()
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/agency-purchase/applications')
}

function goEdit() {
  router.push(`/agency-purchase/applications/${applicationId.value}/edit`)
}

async function onSubmit() {
  try {
    const res = await submitAgencyPurchaseApplication(applicationId.value)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('已提交，进入 BPM 待办占位')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  }
}

async function onCancel() {
  try {
    await ElMessageBox.confirm('确认取消该代采申请？', '提示', { type: 'warning' })
    const res = await cancelAgencyPurchaseApplication(applicationId.value)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('已取消')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.message || '取消失败')
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
.actions {
  display: flex;
  gap: 8px;
}
.cross-domain {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
