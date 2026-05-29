<template>
  <div>
    <h2>OCR 识别中心</h2>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="Mock 模式：识别结果仅预览，确认后不会写入客户/订单/价格/融资等业务表。"
      style="margin-bottom: 16px"
    />

    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>创建识别任务</template>
      <el-form :inline="true" label-width="100px">
        <el-form-item label="业务类型">
          <el-select v-model="form.business_type" style="width: 180px">
            <el-option label="贸易单据" value="TRADE_DOCUMENT" />
            <el-option label="企业证照" value="ENTERPRISE_CERT" />
            <el-option label="通用" value="GENERIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input v-model="form.business_id" placeholder="可选" style="width: 160px" />
        </el-form-item>
        <el-form-item label="识别类型">
          <el-select v-model="form.recognition_type" style="width: 160px">
            <el-option label="表格 OCR" value="TABLE_OCR" />
            <el-option label="证照 OCR" value="CERT_OCR" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件">
          <input ref="fileInput" type="file" accept=".pdf,.jpg,.jpeg,.png" @change="onFileChange" />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="creating"
            :disabled="!canExecute || !selectedFile"
            @click="onCreateJob"
          >
            上传并识别
          </el-button>
        </el-form-item>
      </el-form>
      <div v-if="uploadInfo" class="meta">file_id: {{ uploadInfo.file_id }} · {{ uploadInfo.file_name }}</div>
    </el-card>

    <el-card v-if="job" shadow="never">
      <template #header>
        <div class="header-row">
          <span>识别结果 · {{ job.id }}</span>
          <el-tag :type="job.status === 'CONFIRMED' ? 'success' : 'warning'">{{ job.status }}</el-tag>
        </div>
      </template>
      <p class="meta">待人工确认字段：{{ job.pending_manual_count }} · 模型 {{ job.model_version }}</p>
      <el-table :data="job.fields" stripe>
        <el-table-column prop="field_name" label="字段" width="160" />
        <el-table-column prop="suggested_value" label="建议值" min-width="140" />
        <el-table-column label="置信度" width="100">
          <template #default="{ row }">
            <el-tag :type="row.confidence < 0.85 ? 'danger' : 'success'" size="small">
              {{ (row.confidence * 100).toFixed(1) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="source_text" label="原文定位" min-width="180" show-overflow-tooltip />
        <el-table-column label="页码/区域" width="120">
          <template #default="{ row }">P{{ row.page_no }} {{ row.bbox }}</template>
        </el-table-column>
        <el-table-column prop="confirm_status" label="确认状态" width="100" />
        <el-table-column label="人工确认值" min-width="180">
          <template #default="{ row }">
            <el-input
              v-if="row.requires_manual_confirm && job.status !== 'CONFIRMED'"
              v-model="confirmFields[row.field_name]"
              :placeholder="row.suggested_value"
              size="small"
            />
            <span v-else>{{ row.confirmed_value || row.suggested_value }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 16px">
        <el-button
          type="primary"
          :disabled="!canConfirm || job.status === 'CONFIRMED'"
          :loading="confirming"
          @click="onConfirm"
        >
          确认识别结果
        </el-button>
      </div>
      <el-alert v-if="confirmMessage" :title="confirmMessage" type="success" show-icon style="margin-top: 12px" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadFile } from '../api/file'
import { confirmOcrJob, createOcrJob, getOcrJob, type OcrJob } from '../api/ocr'
import { usePermission } from '../composables/usePermission'

const { hasPermission } = usePermission()
const canExecute = computed(() => hasPermission('AI_OCR_EXECUTE') && hasPermission('FILE_UPLOAD'))
const canConfirm = computed(() => hasPermission('OCR_RESULT_CONFIRM'))

const form = reactive({
  business_type: 'TRADE_DOCUMENT',
  business_id: '',
  recognition_type: 'TABLE_OCR'
})
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const uploadInfo = ref<{ file_id: string; file_name?: string } | null>(null)
const job = ref<OcrJob | null>(null)
const confirmFields = reactive<Record<string, string>>({})
const creating = ref(false)
const confirming = ref(false)
const confirmMessage = ref('')

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function onCreateJob() {
  if (!selectedFile.value) return
  creating.value = true
  confirmMessage.value = ''
  try {
    const uploaded = await uploadFile(selectedFile.value, {
      business_type: form.business_type,
      business_id: form.business_id || undefined
    })
    uploadInfo.value = uploaded
    job.value = await createOcrJob({
      file_id: uploaded.file_id,
      business_type: form.business_type,
      business_id: form.business_id || undefined,
      recognition_type: form.recognition_type
    })
    Object.keys(confirmFields).forEach((k) => delete confirmFields[k])
    for (const f of job.value.fields) {
      if (f.requires_manual_confirm) {
        confirmFields[f.field_name] = f.suggested_value
      }
    }
    ElMessage.success('OCR 任务已创建')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function onConfirm() {
  if (!job.value) return
  confirming.value = true
  try {
    const payload: Record<string, string> = {}
    for (const f of job.value.fields) {
      if (f.requires_manual_confirm) {
        payload[f.field_name] = confirmFields[f.field_name] || f.suggested_value
      }
    }
    const res = await confirmOcrJob(job.value.id, payload)
    job.value = await getOcrJob(job.value.id)
    confirmMessage.value = res.message
    ElMessage.success('确认完成')
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
</style>
