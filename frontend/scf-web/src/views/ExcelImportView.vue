<template>
  <div>
    <h2>Excel 导入预览</h2>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="Mock 模式：仅展示校验预览，确认后不会写入价格/订单/客户等业务表。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>上传并预览</template>
      <el-form :inline="true" label-width="100px">
        <el-form-item label="导入类型">
          <el-select v-model="importType" style="width: 180px">
            <el-option label="通用导入" value="GENERIC" />
            <el-option label="价格记录" value="PRICE_RECORD" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <input ref="fileInput" type="file" accept=".xlsx,.xls" @change="onFileChange" />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="creating"
            :disabled="!canImport || !selectedFile || (importType === 'PRICE_RECORD' && !canPriceImport)"
            @click="onPreview"
          >
            上传并预览
          </el-button>
        </el-form-item>
      </el-form>
      <div v-if="uploadInfo" class="meta">file_id: {{ uploadInfo.file_id }}</div>
    </el-card>

    <el-card v-if="job" shadow="never">
      <template #header>
        <div class="header-row">
          <span>预览结果 · batch {{ job.batch_id }}</span>
          <el-tag>{{ job.status }}</el-tag>
        </div>
      </template>
      <el-row :gutter="12" style="margin-bottom: 12px">
        <el-col :span="6"><el-statistic title="总行数" :value="job.total_rows" /></el-col>
        <el-col :span="6"><el-statistic title="正常" :value="job.ok_rows" /></el-col>
        <el-col :span="6"><el-statistic title="错误" :value="job.error_rows" /></el-col>
        <el-col :span="6"><el-statistic title="警告" :value="job.warning_rows" /></el-col>
      </el-row>
      <el-table :data="job.rows" stripe :row-class-name="rowClassName">
        <el-table-column prop="row_no" label="行号" width="80" />
        <el-table-column prop="row_status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.row_status)" size="small">{{ row.row_status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="行数据" min-width="260">
          <template #default="{ row }">
            <code class="row-data">{{ formatRowData(row.row_data) }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="error_message" label="错误" min-width="160" />
        <el-table-column prop="warning_message" label="警告" min-width="160" />
      </el-table>
      <div style="margin-top: 16px; display: flex; gap: 8px; align-items: center">
        <el-checkbox v-model="ignoreWarning" :disabled="job.error_rows > 0">忽略警告行并确认</el-checkbox>
        <el-button
          type="primary"
          :disabled="!canConfirm || job.status === 'CONFIRMED' || job.error_rows > 0"
          :loading="confirming"
          @click="onConfirm"
        >
          确认导入（Mock）
        </el-button>
      </div>
      <el-alert v-if="confirmMessage" :title="confirmMessage" type="success" show-icon style="margin-top: 12px" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '../api/file'
import {
  confirmExcelImportJob,
  createExcelImportJob,
  getExcelImportJob,
  type ExcelImportJob
} from '../api/import'
import { usePermission } from '../composables/usePermission'

const { hasPermission } = usePermission()
const canImport = computed(() => hasPermission('EXCEL_IMPORT') && hasPermission('FILE_UPLOAD'))
const canPriceImport = computed(() => hasPermission('PRICE_IMPORT'))
const canConfirm = computed(() => hasPermission('EXCEL_IMPORT_CONFIRM'))

const importType = ref('GENERIC')
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const uploadInfo = ref<{ file_id: string } | null>(null)
const job = ref<ExcelImportJob | null>(null)
const creating = ref(false)
const confirming = ref(false)
const ignoreWarning = ref(false)
const confirmMessage = ref('')

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

function formatRowData(raw: string) {
  try {
    return JSON.stringify(JSON.parse(raw), null, 0)
  } catch {
    return raw
  }
}

function statusTag(status: string) {
  if (status === 'OK') return 'success'
  if (status === 'ERROR') return 'danger'
  return 'warning'
}

function rowClassName({ row }: { row: { row_status: string } }) {
  if (row.row_status === 'ERROR') return 'row-error'
  if (row.row_status === 'WARNING') return 'row-warning'
  return ''
}

async function onPreview() {
  if (!selectedFile.value) return
  creating.value = true
  confirmMessage.value = ''
  try {
    const uploaded = await uploadFile(selectedFile.value, {
      business_type: 'EXCEL_IMPORT',
      business_id: importType.value
    })
    uploadInfo.value = uploaded
    job.value = await createExcelImportJob({
      file_id: uploaded.file_id,
      import_type: importType.value,
      dry_run: true
    })
    ElMessage.success('预览任务已生成')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '预览失败')
  } finally {
    creating.value = false
  }
}

async function onConfirm() {
  if (!job.value) return
  confirming.value = true
  try {
    const res = await confirmExcelImportJob(job.value.id, {
      batch_id: job.value.batch_id,
      ignore_warning: ignoreWarning.value
    })
    job.value = await getExcelImportJob(job.value.id)
    confirmMessage.value = res.message
    ElMessage.success('Mock 确认完成')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '确认失败')
  } finally {
    confirming.value = false
  }
}
</script>

<style scoped>
.meta { color: #666; font-size: 13px; margin-top: 8px; }
.header-row { display: flex; align-items: center; justify-content: space-between; }
.row-data { font-size: 12px; word-break: break-all; }
:deep(.row-error) { --el-table-tr-bg-color: #fef0f0; }
:deep(.row-warning) { --el-table-tr-bg-color: #fdf6ec; }
</style>
