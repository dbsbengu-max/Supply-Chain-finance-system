<template>
  <div>
    <div class="toolbar">
      <h2>库存货权</h2>
      <el-button v-if="canInbound" type="primary" @click="showInbound = true">入库</el-button>
    </div>
    <el-form :inline="true" class="filters" @submit.prevent="load">
      <el-form-item label="仓库">
        <el-input v-model="filters.warehouse_id" clearable placeholder="WH001" style="width: 120px" />
      </el-form-item>
      <el-form-item label="货权状态">
        <el-select v-model="filters.right_status" clearable placeholder="全部" style="width: 150px">
          <el-option
            v-for="item in meta?.right_statuses || []"
            :key="item.code"
            :label="item.label"
            :value="item.code"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="inventories" v-loading="loading" stripe>
      <el-table-column prop="batch_no" label="批次" width="150" />
      <el-table-column prop="sku_id" label="SKU" width="140" />
      <el-table-column prop="warehouse_id" label="仓库" width="90" />
      <el-table-column prop="owner_id" label="货主" width="130" />
      <el-table-column prop="quantity" label="总量" width="90" />
      <el-table-column prop="available_quantity" label="可用" width="90" />
      <el-table-column prop="frozen_quantity" label="冻结" width="90" />
      <el-table-column prop="pledged_quantity" label="质押" width="90" />
      <el-table-column label="货权状态" width="110">
        <template #default="{ row }">{{ row.right_status_label }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showInbound" title="入库登记" width="520px">
      <el-form :model="inboundForm" label-width="100px">
        <el-form-item label="仓库 ID" required>
          <el-input v-model="inboundForm.warehouse_id" placeholder="WH001" />
        </el-form-item>
        <el-form-item label="SKU ID" required>
          <el-input v-model="inboundForm.sku_id" placeholder="SKU_GARLIC_A" />
        </el-form-item>
        <el-form-item label="批次号" required>
          <el-input v-model="inboundForm.batch_no" />
        </el-form-item>
        <el-form-item label="货主 ID" required>
          <el-input v-model="inboundForm.owner_id" placeholder="ENT_MEMBER_001" />
        </el-form-item>
        <el-form-item label="库位">
          <el-input v-model="inboundForm.location_code" />
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number v-model="inboundForm.quantity" :min="0.000001" :precision="6" />
        </el-form-item>
        <el-form-item label="估值">
          <el-input-number v-model="inboundForm.valuation_amount" :min="0" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showInbound = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitInbound">确认入库</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createInbound,
  getWarehouseMeta,
  listInventories,
  type Inventory,
  type WarehouseMeta
} from '../api/warehouse'
import { usePermission } from '../composables/usePermission'

const router = useRouter()
const { hasPermission } = usePermission()
const canInbound = computed(() => hasPermission('WAREHOUSE_INBOUND'))

const loading = ref(false)
const submitting = ref(false)
const showInbound = ref(false)
const meta = ref<WarehouseMeta | null>(null)
const inventories = ref<Inventory[]>([])
const filters = reactive({ warehouse_id: '', right_status: '' })
const inboundForm = reactive({
  warehouse_id: 'WH001',
  sku_id: 'SKU_GARLIC_A',
  batch_no: '',
  owner_id: 'ENT_MEMBER_001',
  location_code: '',
  quantity: 10,
  valuation_amount: undefined as number | undefined
})

async function loadMeta() {
  const res = await getWarehouseMeta()
  if (res.success) meta.value = res.data
}

async function load() {
  loading.value = true
  try {
    const res = await listInventories({
      page_no: 1,
      page_size: 50,
      warehouse_id: filters.warehouse_id || undefined,
      right_status: filters.right_status || undefined
    })
    if (res.success) inventories.value = res.data?.records || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function submitInbound() {
  submitting.value = true
  try {
    const res = await createInbound({ ...inboundForm })
    if (!res.success) throw new Error(res.message)
    ElMessage.success('入库成功')
    showInbound.value = false
    if (res.data?.inventory_id) router.push(`/warehouse/inventories/${res.data.inventory_id}`)
    else load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '入库失败')
  } finally {
    submitting.value = false
  }
}

function goDetail(id: string) {
  router.push(`/warehouse/inventories/${id}`)
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
  margin-bottom: 12px;
}
</style>
