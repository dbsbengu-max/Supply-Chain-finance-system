<template>
  <div>
    <div class="toolbar">
      <h2>客户 / KYC</h2>
      <el-button type="primary" @click="showCreate = true">新增企业</el-button>
    </div>
    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="enterprise_code" label="编码" width="120" />
      <el-table-column prop="enterprise_name" label="企业名称" />
      <el-table-column prop="enterprise_type" label="类型" width="140" />
      <el-table-column prop="country_region" label="地区" width="100" />
      <el-table-column prop="kyc_status" label="KYC 状态" width="110" />
      <el-table-column label="操作" width="260">
        <template #default="{ row }">
          <el-button
            v-if="row.kyc_status === 'DRAFT' || row.kyc_status === 'REJECTED'"
            link
            type="primary"
            @click="onSubmitKyc(row.id)"
          >提交 KYC</el-button>
          <el-button v-if="row.kyc_status === 'PENDING'" link type="success" @click="onApprove(row.id)">通过</el-button>
          <el-button v-if="row.kyc_status === 'PENDING'" link type="danger" @click="onReject(row.id)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新增企业" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="企业名称" required>
          <el-input v-model="form.enterprise_name" />
        </el-form-item>
        <el-form-item label="企业类型" required>
          <el-select v-model="form.enterprise_type" style="width: 100%">
            <el-option label="成员企业" value="MEMBER_ENTERPRISE" />
            <el-option label="核心企业" value="CORE_ENTERPRISE" />
            <el-option label="贸易公司" value="TRADE_COMPANY" />
          </el-select>
        </el-form-item>
        <el-form-item label="国家/地区" required>
          <el-input v-model="form.country_region" placeholder="如 MY / HK / CN_MAINLAND" />
        </el-form-item>
        <el-form-item label="法人">
          <el-input v-model="form.legal_person" />
        </el-form-item>
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
import {
  approveKyc,
  createEnterprise,
  listEnterprises,
  rejectKyc,
  submitKyc,
  type Enterprise
} from '../api/customer'

const loading = ref(false)
const records = ref<Enterprise[]>([])
const showCreate = ref(false)
const form = reactive({
  enterprise_name: '',
  enterprise_type: 'MEMBER_ENTERPRISE',
  country_region: '',
  legal_person: ''
})

async function load() {
  loading.value = true
  try {
    const res = await listEnterprises({ page_no: 1, page_size: 50 })
    if (res.success) records.value = res.data?.records || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  try {
    const res = await createEnterprise(form)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('创建成功')
    showCreate.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  }
}

async function onSubmitKyc(id: string) {
  try {
    const res = await submitKyc(id)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('已提交 KYC')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '提交失败')
  }
}

async function onApprove(id: string) {
  try {
    const res = await approveKyc(id)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('KYC 已通过')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function onReject(id: string) {
  try {
    const res = await rejectKyc(id, '资料不完整')
    if (!res.success) throw new Error(res.message)
    ElMessage.success('已驳回')
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
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
</style>
