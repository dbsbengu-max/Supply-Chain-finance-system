<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>供应链金融管理系统</h2>
      <p class="subtitle">Supply Chain Finance V1.1</p>
      <el-form :model="form" @submit.prevent="onSubmit">
        <el-form-item label="账号">
          <el-input v-model="form.loginName" placeholder="platform_admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">
          登录
        </el-button>
      </el-form>
      <p class="hint">演示账号：platform_admin / Admin@123；资金方清分：funding_user / Fund@123（登录后切换身份选 PJ001）</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ loginName: 'platform_admin', password: 'Admin@123' })

async function onSubmit() {
  loading.value = true
  try {
    await auth.login(form.loginName, form.password)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: any) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f2744, #1f5f8b);
}
.login-card {
  width: 420px;
}
.subtitle {
  color: #666;
  margin-top: -8px;
}
.hint {
  margin-top: 16px;
  font-size: 12px;
  color: #999;
}
</style>
