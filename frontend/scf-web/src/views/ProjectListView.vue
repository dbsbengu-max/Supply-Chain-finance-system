<template>
  <div>
    <div class="toolbar">
      <h2>项目配置</h2>
      <el-button type="primary" @click="showCreate = true">新建项目</el-button>
    </div>
    <el-table :data="projects" v-loading="loading" stripe>
      <el-table-column prop="project_code" label="项目编码" width="160" />
      <el-table-column prop="project_name" label="项目名称" />
      <el-table-column prop="countries" label="覆盖国家" />
      <el-table-column prop="currencies" label="币种" />
      <el-table-column prop="status" label="状态" width="100" />
    </el-table>

    <el-dialog v-model="showCreate" title="新建项目" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目编码" required>
          <el-input v-model="form.project_code" />
        </el-form-item>
        <el-form-item label="项目名称" required>
          <el-input v-model="form.project_name" />
        </el-form-item>
        <el-form-item label="国家列表" required>
          <el-input v-model="form.countries" placeholder="逗号分隔，如 CN_MAINLAND,HK,MY" />
        </el-form-item>
        <el-form-item label="币种列表" required>
          <el-input v-model="form.currencies" placeholder="逗号分隔，如 CNY,HKD,USD" />
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
import { createProject, listProjects, type Project } from '../api/project'

const loading = ref(false)
const projects = ref<Project[]>([])
const showCreate = ref(false)
const form = reactive({
  project_code: '',
  project_name: '',
  countries: '',
  currencies: ''
})

async function load() {
  loading.value = true
  try {
    const res = await listProjects()
    if (res.success) projects.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  try {
    const res = await createProject(form)
    if (!res.success) throw new Error(res.message)
    ElMessage.success('项目已创建')
    showCreate.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
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
