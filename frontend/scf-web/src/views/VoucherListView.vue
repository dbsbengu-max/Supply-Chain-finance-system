<template>
  <div>
    <div class="toolbar">
      <h2>数字债权凭证</h2>
      <div class="actions">
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 150px" @change="load">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已签发" value="ACCEPTED" />
          <el-option label="兑付待审" value="REDEEM_PENDING" />
          <el-option label="兑付已批" value="REDEEM_APPROVED" />
          <el-option label="已兑付" value="REDEEMED" />
          <el-option label="已驳回" value="REJECTED" />
          <el-option label="已作废" value="CANCELLED" />
        </el-select>
        <el-button v-if="canCreate" type="primary" @click="showCreate = true">新建凭证</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="voucher_no" label="凭证编号" width="170" />
      <el-table-column prop="holder_id" label="当前持有人" width="150" />
      <el-table-column prop="amount" label="票面金额" width="130" />
      <el-table-column prop="available_amount" label="可用余额" width="130" />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column prop="voucher_status" label="状态" width="120" />
      <el-table-column prop="due_date" label="到期日" width="120" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/vouchers/${row.id}`)">详情</el-button>
          <el-button v-if="canIssue && row.voucher_status === 'DRAFT'" link type="success" @click="onIssue(row.id)">签发</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新建数字凭证" width="620px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="签发方" required><el-input v-model="form.issuer_id" /></el-form-item>
        <el-form-item label="承兑方" required><el-input v-model="form.acceptor_id" /></el-form-item>
        <el-form-item label="持有人" required><el-input v-model="form.holder_id" /></el-form-item>
        <el-form-item label="金额" required><el-input v-model="form.amount" /></el-form-item>
        <el-form-item label="币种" required><el-input v-model="form.currency" /></el-form-item>
        <el-form-item label="签发日"><el-date-picker v-model="form.issue_date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="到期日" required><el-date-picker v-model="form.due_date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="onCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { usePermission } from '../composables/usePermission'
import { createVoucher, issueVoucher, listVouchers, type Voucher } from '../api/voucher'

const router = useRouter()
const { hasPermission } = usePermission()
const canCreate = hasPermission('VOUCHER_CREATE')
const canIssue = hasPermission('VOUCHER_ISSUE')
const loading = ref(false)
const showCreate = ref(false)
const status = ref('')
const records = ref<Voucher[]>([])
const form = reactive({
  issuer_id: 'ENT_CORE_001',
  acceptor_id: 'ENT_CORE_001',
  holder_id: 'ENT_MEMBER_001',
  amount: '100000.00',
  currency: 'CNY',
  issue_date: '',
  due_date: '2026-08-25'
})

async function load() {
  loading.value = true
  try {
    const res = await listVouchers({ page_no: 1, page_size: 50, status: status.value || undefined })
    if (!res.success) throw new Error(res.message || '加载失败')
    records.value = res.data?.records ?? []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  try {
    const res = await createVoucher({ ...form, issue_date: form.issue_date || undefined })
    if (!res.success) throw new Error(res.message || '保存失败')
    ElMessage.success('凭证已创建')
    showCreate.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '保存失败')
  }
}

async function onIssue(id: string) {
  try {
    const res = await issueVoucher(id)
    if (!res.success) throw new Error(res.message || '签发失败')
    ElMessage.success('凭证已签发')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '签发失败')
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
  gap: 10px;
}
</style>
