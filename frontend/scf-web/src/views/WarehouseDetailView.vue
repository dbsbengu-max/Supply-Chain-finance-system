<template>
  <div v-loading="loading">
    <div class="toolbar">
      <h2>仓库详情</h2>
      <el-button @click="goBack">返回</el-button>
    </div>
    <el-descriptions v-if="warehouse" :column="2" border>
      <el-descriptions-item label="编码">{{ warehouse.warehouse_code }}</el-descriptions-item>
      <el-descriptions-item label="名称">{{ warehouse.warehouse_name }}</el-descriptions-item>
      <el-descriptions-item label="仓储企业">{{ warehouse.warehouse_company_id }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ warehouse.status }}</el-descriptions-item>
      <el-descriptions-item label="区域">{{ warehouse.country_region }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ warehouse.warehouse_type }}</el-descriptions-item>
      <el-descriptions-item label="地址" :span="2">{{ warehouse.address }}</el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getWarehouse, type Warehouse } from '../api/warehouse'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const warehouse = ref<Warehouse | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await getWarehouse(route.params.id as string)
    if (res.success) warehouse.value = res.data
    else throw new Error(res.message)
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
    goBack()
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/warehouse/warehouses')
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
</style>
