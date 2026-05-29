<template>
  <div v-loading="loading">
    <div class="toolbar">
      <h2>库存详情</h2>
      <div class="actions">
        <el-button @click="goBack">返回列表</el-button>
        <el-button v-if="canFreeze" type="warning" @click="openAction('freeze')">冻结</el-button>
        <el-button v-if="canPledge" type="primary" @click="openAction('pledge')">质押</el-button>
        <el-button v-if="canRelease" @click="openAction('release')">解押申请</el-button>
        <el-button
          v-if="canRelease && pendingReleaseRequestId"
          type="success"
          @click="onApproveRelease"
        >解押审批</el-button>
        <el-button v-if="canOutbound" type="danger" @click="openAction('outbound')">出库申请</el-button>
      </div>
    </div>

    <el-descriptions v-if="inv" :column="2" border>
      <el-descriptions-item label="库存 ID">{{ inv.id }}</el-descriptions-item>
      <el-descriptions-item label="货权状态">{{ inv.right_status_label }}</el-descriptions-item>
      <el-descriptions-item label="仓库">{{ inv.warehouse_id }}</el-descriptions-item>
      <el-descriptions-item label="SKU">{{ inv.sku_id }}</el-descriptions-item>
      <el-descriptions-item label="批次">{{ inv.batch_no }}</el-descriptions-item>
      <el-descriptions-item label="货主">{{ inv.owner_id }}</el-descriptions-item>
      <el-descriptions-item label="库位">{{ inv.location_code || '-' }}</el-descriptions-item>
      <el-descriptions-item label="盘库异常">{{ inv.stocktake_exception ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="总量">{{ inv.quantity }}</el-descriptions-item>
      <el-descriptions-item label="可用">{{ inv.available_quantity }}</el-descriptions-item>
      <el-descriptions-item label="冻结">{{ inv.frozen_quantity }}</el-descriptions-item>
      <el-descriptions-item label="质押">{{ inv.pledged_quantity }}</el-descriptions-item>
      <el-descriptions-item label="待出库">{{ inv.outbound_pending_quantity }}</el-descriptions-item>
      <el-descriptions-item label="估值">{{ inv.valuation_amount }} {{ inv.currency }}</el-descriptions-item>
    </el-descriptions>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="400px">
      <el-form label-width="80px">
        <el-form-item label="数量">
          <el-input-number v-model="actionQty" :min="0.000001" :precision="6" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="actionType === 'release'" label="备注">
          <el-input v-model="actionRemark" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAction">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  applyOutbound,
  applyRelease,
  approveRelease,
  freezeInventory,
  getInventory,
  pledgeInventory,
  type Inventory
} from '../api/warehouse'
import { usePermission } from '../composables/usePermission'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const canFreeze = computed(() => hasPermission('WAREHOUSE_FREEZE'))
const canPledge = computed(() => hasPermission('WAREHOUSE_PLEDGE'))
const canRelease = computed(() => hasPermission('WAREHOUSE_RELEASE'))
const canOutbound = computed(() => hasPermission('WAREHOUSE_OUTBOUND'))

const loading = ref(false)
const submitting = ref(false)
const inv = ref<Inventory | null>(null)
const dialogVisible = ref(false)
const actionType = ref<'freeze' | 'pledge' | 'release' | 'outbound'>('freeze')
const actionQty = ref(1)
const actionRemark = ref('')
const pendingReleaseRequestId = ref('')
const dialogTitle = computed(() => {
  const map = { freeze: '冻结', pledge: '质押', release: '解押申请', outbound: '出库申请' }
  return map[actionType.value]
})

async function load() {
  loading.value = true
  try {
    const res = await getInventory(route.params.id as string)
    if (res.success) inv.value = res.data
    else throw new Error(res.message)
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
    goBack()
  } finally {
    loading.value = false
  }
}

function openAction(type: typeof actionType.value) {
  actionType.value = type
  actionQty.value = 1
  actionRemark.value = ''
  dialogVisible.value = true
}

async function submitAction() {
  if (!inv.value) return
  submitting.value = true
  try {
    const body = { quantity: actionQty.value, remark: actionRemark.value || undefined }
    let res
    if (actionType.value === 'freeze') {
      res = await freezeInventory(inv.value.id, body)
    } else if (actionType.value === 'pledge') {
      res = await pledgeInventory(inv.value.id, body)
    } else if (actionType.value === 'release') {
      res = await applyRelease(inv.value.id, body)
      if (res.success && res.data?.id) pendingReleaseRequestId.value = res.data.id
    } else {
      res = await applyOutbound({ inventory_id: inv.value.id, ...body })
    }
    if (!res?.success) throw new Error(res?.message || '操作失败')
    ElMessage.success('操作成功')
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    const msg = e.response?.data?.message || e.message || '操作失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

async function onApproveRelease() {
  if (!pendingReleaseRequestId.value) return
  submitting.value = true
  try {
    const res = await approveRelease(pendingReleaseRequestId.value)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('解押审批完成')
    pendingReleaseRequestId.value = ''
    await load()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '审批失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/warehouse/inventories')
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
  flex-wrap: wrap;
}
</style>
