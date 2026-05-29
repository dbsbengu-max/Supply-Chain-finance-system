<template>
  <div>
    <div class="toolbar">
      <h2>订单贸易</h2>
      <el-button type="primary" @click="openCreate">新建订单</el-button>
    </div>
    <el-table :data="orders" v-loading="loading" stripe>
      <el-table-column prop="order_no" label="订单号" width="180" />
      <el-table-column prop="order_type" label="类型" width="140" />
      <el-table-column prop="buyer_id" label="买方" width="140" />
      <el-table-column prop="seller_id" label="卖方" width="140" />
      <el-table-column prop="total_amount" label="金额" width="120" />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column prop="order_status" label="状态" width="110" />
      <el-table-column label="操作" width="360">
        <template #default="{ row }">
          <el-button v-if="row.order_status === 'DRAFT'" link type="warning" @click="openEdit(row.id)">编辑</el-button>
          <el-button v-if="row.order_status === 'DRAFT'" link type="primary" @click="onSubmit(row.id)">提交</el-button>
          <el-button v-if="row.order_status === 'SUBMITTED'" link type="success" @click="onConfirm(row.id)">确认</el-button>
          <el-button
            v-if="row.order_status !== 'CONFIRMED' && row.order_status !== 'CANCELLED'"
            link
            type="danger"
            @click="onCancel(row.id)"
          >取消</el-button>
          <el-button link @click="onValidate(row.id)">背景核验</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showForm" :title="editingId ? '编辑贸易订单' : '新建贸易订单'" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="订单类型" required>
          <el-select v-model="form.order_type" style="width: 100%">
            <el-option label="代采贸易" value="AGENCY_PURCHASE" />
            <el-option label="一般贸易" value="GENERAL_TRADE" />
          </el-select>
        </el-form-item>
        <el-form-item label="买方 ID" required>
          <el-input v-model="form.buyer_id" placeholder="如 ENT_MEMBER_001" />
        </el-form-item>
        <el-form-item label="卖方 ID" required>
          <el-input v-model="form.seller_id" placeholder="如 ENT_CORE_001" />
        </el-form-item>
        <el-form-item label="贸易公司">
          <el-input v-model="form.trade_company_id" placeholder="如 ENT_TRADE_001" />
        </el-form-item>
        <el-form-item label="币种" required>
          <el-input v-model="form.currency" />
        </el-form-item>
        <el-divider>明细行</el-divider>
        <el-form-item label="SKU" required>
          <el-select v-model="line.sku_id" style="width: 100%">
            <el-option v-for="s in skus" :key="s.id" :label="`${s.sku_code} - ${s.spec}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input v-model="line.quantity" />
        </el-form-item>
        <el-form-item label="单位" required>
          <el-input v-model="line.unit" />
        </el-form-item>
        <el-form-item label="单价" required>
          <el-input v-model="line.unit_price" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSkus } from '../api/pricing'
import {
  cancelOrder,
  confirmOrder,
  createOrder,
  getOrder,
  listOrders,
  submitOrder,
  updateOrder,
  validateBackground,
  type TradeOrder
} from '../api/trade'

const loading = ref(false)
const orders = ref<TradeOrder[]>([])
const skus = ref<any[]>([])
const showForm = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({
  order_type: 'AGENCY_PURCHASE',
  buyer_id: 'ENT_MEMBER_001',
  seller_id: 'ENT_CORE_001',
  trade_company_id: 'ENT_TRADE_001',
  currency: 'CNY',
  country_from: 'CN_MAINLAND',
  country_to: 'MY'
})
const line = reactive({
  sku_id: '',
  quantity: '100',
  unit: 'ton',
  unit_price: '8500'
})

function resetFormDefaults() {
  form.order_type = 'AGENCY_PURCHASE'
  form.buyer_id = 'ENT_MEMBER_001'
  form.seller_id = 'ENT_CORE_001'
  form.trade_company_id = 'ENT_TRADE_001'
  form.currency = 'CNY'
  form.country_from = 'CN_MAINLAND'
  form.country_to = 'MY'
  line.quantity = '100'
  line.unit = 'ton'
  line.unit_price = '8500'
  if (skus.value.length) line.sku_id = skus.value[0].id
}

async function load() {
  loading.value = true
  try {
    const res = await listOrders({ page_no: 1, page_size: 50 })
    if (res.success) orders.value = res.data?.records || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSkus() {
  const res = await listSkus()
  if (res.success) {
    skus.value = res.data || []
    if (skus.value.length) line.sku_id = skus.value[0].id
  }
}

function openCreate() {
  editingId.value = null
  resetFormDefaults()
  showForm.value = true
}

async function openEdit(id: string) {
  try {
    const res = await getOrder(id)
    if (!res.success) throw new Error(res.message)
    const order = res.data
    if (order.order_status !== 'DRAFT') {
      ElMessage.warning('仅草稿订单可编辑')
      return
    }
    editingId.value = id
    form.order_type = order.order_type
    form.buyer_id = order.buyer_id
    form.seller_id = order.seller_id
    form.trade_company_id = order.trade_company_id || ''
    form.currency = order.currency
    form.country_from = order.country_from || 'CN_MAINLAND'
    form.country_to = order.country_to || 'MY'
    const firstItem = order.items?.[0]
    if (firstItem) {
      line.sku_id = firstItem.sku_id
      line.quantity = firstItem.quantity
      line.unit = firstItem.unit
      line.unit_price = firstItem.unit_price
    }
    showForm.value = true
  } catch (e: any) {
    ElMessage.error(e.message || '加载订单失败')
  }
}

async function onSave() {
  const payload = { ...form, items: [{ ...line }] }
  try {
    const res = editingId.value
      ? await updateOrder(editingId.value, payload)
      : await createOrder(payload)
    if (!res.success) throw new Error(res.message)
    ElMessage.success(editingId.value ? '订单已更新' : '订单已创建')
    showForm.value = false
    editingId.value = null
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function onSubmit(id: string) {
  const res = await submitOrder(id)
  if (res.success) {
    ElMessage.success('已提交')
    await load()
  }
}

async function onConfirm(id: string) {
  const res = await confirmOrder(id)
  if (res.success) {
    ElMessage.success('已确认')
    await load()
  }
}

async function onCancel(id: string) {
  const res = await cancelOrder(id)
  if (res.success) {
    ElMessage.success('已取消')
    await load()
  }
}

async function onValidate(id: string) {
  const res = await validateBackground(id)
  if (res.success) {
    const data = res.data
    ElMessageBox.alert(
      `通过: ${data.passed ? '是' : '否'}\n风险等级: ${data.risk_level}\n检查项: ${data.checks?.length || 0}`,
      '贸易背景核验'
    )
  }
}

onMounted(async () => {
  await Promise.all([load(), loadSkus()])
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
