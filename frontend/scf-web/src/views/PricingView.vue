<template>
  <div>
    <h2>价格管理</h2>
    <el-tabs v-model="tab">
      <el-tab-pane label="商品价格" name="prices">
        <div class="toolbar">
          <el-button type="primary" @click="showPrice = true">录入价格</el-button>
        </div>
        <el-table :data="prices" v-loading="loadingPrices" stripe>
          <el-table-column prop="sku_id" label="SKU" width="140" />
          <el-table-column prop="price_date" label="日期" width="120" />
          <el-table-column prop="price" label="价格" width="120" />
          <el-table-column prop="currency" label="币种" width="80" />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column prop="review_status" label="审核状态" width="110" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button v-if="row.review_status === 'DRAFT'" link @click="onSubmit(row.id)">提交</el-button>
              <el-button v-if="row.review_status === 'PENDING'" link type="success" @click="onApprove(row.id)">通过</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="汇率" name="fx">
        <el-table :data="fxRates" v-loading="loadingFx" stripe>
          <el-table-column prop="base_currency" label="基准币" width="100" />
          <el-table-column prop="quote_currency" label="报价币" width="100" />
          <el-table-column prop="rate" label="汇率" />
          <el-table-column prop="rate_date" label="日期" width="120" />
          <el-table-column prop="review_status" label="状态" width="100" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showPrice" title="录入价格" width="480px">
      <el-form :model="priceForm" label-width="100px">
        <el-form-item label="SKU" required>
          <el-select v-model="priceForm.sku_id" style="width: 100%">
            <el-option v-for="s in skus" :key="s.id" :label="`${s.sku_code} - ${s.spec}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格日期" required>
          <el-date-picker v-model="priceForm.price_date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input v-model="priceForm.price" />
        </el-form-item>
        <el-form-item label="币种" required>
          <el-input v-model="priceForm.currency" />
        </el-form-item>
        <el-form-item label="单位" required>
          <el-input v-model="priceForm.unit" />
        </el-form-item>
        <el-form-item label="来源类型" required>
          <el-input v-model="priceForm.source_type" placeholder="MANUAL / EXTERNAL_MARKET" />
        </el-form-item>
        <el-form-item label="可信度" required>
          <el-select v-model="priceForm.trust_level" style="width: 100%">
            <el-option label="A" value="A" />
            <el-option label="B" value="B" />
            <el-option label="C" value="C" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPrice = false">取消</el-button>
        <el-button type="primary" @click="onCreatePrice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approvePrice,
  createPrice,
  listFxRates,
  listPrices,
  listSkus,
  submitPrice,
  type FxRate,
  type PriceRecord
} from '../api/pricing'

const tab = ref('prices')
const loadingPrices = ref(false)
const loadingFx = ref(false)
const prices = ref<PriceRecord[]>([])
const fxRates = ref<FxRate[]>([])
const skus = ref<any[]>([])
const showPrice = ref(false)
const priceForm = reactive({
  sku_id: '',
  price_date: '',
  price: '',
  currency: 'CNY',
  unit: 'ton',
  source_type: 'MANUAL',
  trust_level: 'B'
})

async function loadPrices() {
  loadingPrices.value = true
  try {
    const res = await listPrices({ page_no: 1, page_size: 50 })
    if (res.success) prices.value = res.data?.records || []
  } finally {
    loadingPrices.value = false
  }
}

async function loadFx() {
  loadingFx.value = true
  try {
    const res = await listFxRates({ page_no: 1, page_size: 50 })
    if (res.success) fxRates.value = res.data?.records || []
  } finally {
    loadingFx.value = false
  }
}

async function loadSkus() {
  const res = await listSkus()
  if (res.success) skus.value = res.data || []
}

async function onCreatePrice() {
  try {
    const res = await createPrice(priceForm)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('价格已录入')
    showPrice.value = false
    await loadPrices()
  } catch (e: any) {
    ElMessage.error(e.message || '录入失败')
  }
}

async function onSubmit(id: string) {
  const res = await submitPrice(id)
  if (res.success) {
    ElMessage.success('已提交审核')
    await loadPrices()
  }
}

async function onApprove(id: string) {
  const res = await approvePrice(id)
  if (res.success) {
    ElMessage.success('审核通过')
    await loadPrices()
  }
}

watch(tab, (v) => {
  if (v === 'fx') loadFx()
})

onMounted(async () => {
  await Promise.all([loadPrices(), loadSkus()])
})
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
</style>
