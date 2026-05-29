<template>
  <div v-loading="loading">
    <div class="toolbar">
      <h2>{{ isEdit ? '编辑代采申请' : '新建代采申请' }}</h2>
      <el-button @click="goBack">返回列表</el-button>
    </div>

    <el-alert
      v-if="meta"
      type="info"
      :closable="false"
      show-icon
      title="请选择有效的 8 类代采模式组合；提交后仅进入 BPM 占位，不触发资金/库存/货权/清分。"
      style="margin-bottom: 16px"
    />

    <el-form :model="form" label-width="120px" style="max-width: 720px">
      <el-form-item label="订单/备货" required>
        <el-select v-model="form.order_mode" style="width: 100%" @change="onModeDimensionChange">
          <el-option
            v-for="item in meta?.order_modes || []"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="资金来源" required>
        <el-select v-model="form.fund_source" style="width: 100%" @change="onModeDimensionChange">
          <el-option
            v-for="item in meta?.fund_sources || []"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="提货方式" required>
        <el-select v-model="form.pickup_type" style="width: 100%" @change="syncModePreview">
          <el-option
            v-for="item in pickupOptions"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="模式预览">
        <el-tag v-if="modePreview" type="success">{{ modePreview }}</el-tag>
        <el-tag v-else type="danger">无效组合，请调整上方选项</el-tag>
      </el-form-item>
      <el-form-item label="客户 ID" required>
        <el-input v-model="form.customer_id" placeholder="ENT_MEMBER_001" />
      </el-form-item>
      <el-form-item label="贸易公司 ID" required>
        <el-input v-model="form.trade_company_id" placeholder="ENT_TRADE_001" />
      </el-form-item>
      <el-form-item label="关联订单 ID" :required="form.order_mode === 'STOCK_ORDER'">
        <el-input v-model="form.order_id" placeholder="订单模式下必填，如 ORD001" />
      </el-form-item>
      <el-form-item label="币种" required>
        <el-input v-model="form.currency" />
      </el-form-item>
      <el-form-item label="申请金额" required>
        <el-input v-model="form.total_amount" placeholder="100000.00" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="3" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :disabled="!modePreview" @click="onSave">保存草稿</el-button>
        <el-button @click="goBack">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createAgencyPurchaseApplication,
  getAgencyPurchaseApplication,
  updateAgencyPurchaseApplication,
  type AgencyPurchaseMeta
} from '../api/agencyPurchase'
import {
  agencyPurchaseModeLabel,
  filterValidModes,
  isDraftStatus,
  isValidModeCombination,
  loadAgencyPurchaseMeta
} from '../constants/agencyPurchaseDict'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const meta = ref<AgencyPurchaseMeta | null>(null)
const applicationId = computed(() => route.params.id as string | undefined)
const isEdit = computed(() => !!applicationId.value && route.name === 'agency-purchase-edit')

const form = reactive({
  order_mode: 'STOCK_ORDER',
  fund_source: 'SELF_FUNDED',
  pickup_type: 'PAYMENT_PICKUP',
  customer_id: 'ENT_MEMBER_001',
  trade_company_id: 'ENT_TRADE_001',
  order_id: 'ORD001',
  currency: 'CNY',
  total_amount: '100000.00',
  remark: ''
})

const pickupOptions = computed(() => {
  const allowed = filterValidModes(meta.value, form.order_mode, form.fund_source)
  const codes = new Set(allowed.map((m) => m.pickup_type))
  return (meta.value?.pickup_types || []).filter((p) => codes.has(p.code))
})

const modePreview = computed(() => {
  if (!isValidModeCombination(meta.value, form.order_mode, form.fund_source, form.pickup_type)) {
    return ''
  }
  const mode = meta.value?.valid_modes.find(
    (m) =>
      m.order_mode === form.order_mode &&
      m.fund_source === form.fund_source &&
      m.pickup_type === form.pickup_type
  )
  return mode ? agencyPurchaseModeLabel(meta.value, mode.mode_key) : ''
})

function onModeDimensionChange() {
  const allowed = pickupOptions.value
  if (!allowed.some((p) => p.code === form.pickup_type) && allowed.length) {
    form.pickup_type = allowed[0].code
  }
  syncModePreview()
}

function syncModePreview() {
  // computed handles preview
}

async function loadMeta() {
  meta.value = await loadAgencyPurchaseMeta()
}

async function loadApplication() {
  if (!isEdit.value || !applicationId.value) return
  loading.value = true
  try {
    const res = await getAgencyPurchaseApplication(applicationId.value)
    if (!res.success) throw new Error(res.message)
    const app = res.data
    if (!isDraftStatus(app.application_status)) {
      ElMessage.warning('仅草稿可编辑')
      router.replace(`/agency-purchase/applications/${app.id}`)
      return
    }
    form.order_mode = app.order_mode
    form.fund_source = app.fund_source
    form.pickup_type = app.pickup_type
    form.customer_id = app.customer_id
    form.trade_company_id = app.trade_company_id
    form.order_id = app.order_id || ''
    form.currency = app.currency
    form.total_amount = app.total_amount
    form.remark = app.remark || ''
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
    goBack()
  } finally {
    loading.value = false
  }
}

async function onSave() {
  if (!modePreview.value) {
    ElMessage.error('请选择有效的代采模式组合')
    return
  }
  const payload = {
    order_mode: form.order_mode,
    fund_source: form.fund_source,
    pickup_type: form.pickup_type,
    customer_id: form.customer_id,
    trade_company_id: form.trade_company_id,
    order_id: form.order_id || undefined,
    currency: form.currency,
    total_amount: form.total_amount,
    remark: form.remark || undefined
  }
  try {
    const res = isEdit.value && applicationId.value
      ? await updateAgencyPurchaseApplication(applicationId.value, payload)
      : await createAgencyPurchaseApplication(payload)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('草稿已保存')
    router.push(`/agency-purchase/applications/${res.data.id}`)
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

function goBack() {
  router.push('/agency-purchase/applications')
}

onMounted(async () => {
  await loadMeta()
  await loadApplication()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
</style>
