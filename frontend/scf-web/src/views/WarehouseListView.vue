<template>
  <div>
    <div class="toolbar">
      <h2>仓库管理</h2>
    </div>
    <el-form :inline="true" class="filters" @submit.prevent="load">
      <el-form-item label="状态">
        <el-input v-model="filters.status" clearable placeholder="ACTIVE" style="width: 120px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="warehouses" v-loading="loading" stripe>
      <el-table-column prop="warehouse_code" label="仓库编码" width="140" />
      <el-table-column prop="warehouse_name" label="仓库名称" min-width="180" />
      <el-table-column prop="warehouse_company_id" label="仓储企业" width="140" />
      <el-table-column prop="country_region" label="区域" width="100" />
      <el-table-column prop="status" label="状态" width="90" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listWarehouses, type Warehouse } from '../api/warehouse'

const router = useRouter()
const loading = ref(false)
const warehouses = ref<Warehouse[]>([])
const filters = reactive({ status: '' })

async function load() {
  loading.value = true
  try {
    const res = await listWarehouses({ page_no: 1, page_size: 50, status: filters.status || undefined })
    if (res.success) warehouses.value = res.data?.records || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function goDetail(id: string) {
  router.push(`/warehouse/warehouses/${id}`)
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
.filters {
  margin-bottom: 12px;
}
</style>
