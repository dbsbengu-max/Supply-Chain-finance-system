<template>
  <el-card shadow="never" class="balance-card" v-loading="loading">
    <template #header>
      <div class="header-row">
        <span>资金账户余额</span>
        <el-button link type="primary" :loading="loading" @click="load">刷新</el-button>
      </div>
    </template>
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" style="margin-bottom: 12px" />
    <el-table v-if="accounts.length" :data="accounts" size="small" stripe>
      <el-table-column prop="account_name" label="账户名称" min-width="140" />
      <el-table-column prop="account_type" label="类型" width="100" />
      <el-table-column prop="id" label="账户 ID" width="130" />
      <el-table-column label="余额" width="140" align="right">
        <template #default="{ row }">{{ formatMoney(row.balance, row.currency) }}</template>
      </el-table-column>
      <el-table-column label="冻结" width="140" align="right">
        <template #default="{ row }">{{ formatMoney(row.frozen_balance, row.currency) }}</template>
      </el-table-column>
      <el-table-column label="可用" width="140" align="right">
        <template #default="{ row }">{{ formatMoney(row.available_balance, row.currency) }}</template>
      </el-table-column>
    </el-table>
    <el-empty v-else-if="!loading" description="暂无可见资金账户" />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAccountSummary, type AccountBalanceSummary } from '../api/account'
import { formatMoney } from '../utils/format'

const loading = ref(false)
const accounts = ref<AccountBalanceSummary[]>([])
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getAccountSummary()
    if (!res.success) throw new Error(res.message || '加载余额失败')
    accounts.value = res.data || []
  } catch (e: any) {
    accounts.value = []
    error.value = e.response?.data?.message || e.message || '加载余额失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

defineExpose({ load })
</script>

<style scoped>
.balance-card {
  margin-bottom: 16px;
}
.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
