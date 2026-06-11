<template>
  <div class="document-center">
    <div class="page-header">
      <div>
        <h2>签章中心</h2>
        <p class="subtitle">合同单证管理、OCR 复核、发起签署与状态跟踪（Mock/HTTP Adapter）</p>
      </div>
      <div class="header-actions">
        <el-button v-if="canUpload" type="primary" @click="openUpload">登记单证</el-button>
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="routeExternalSignRef"
      type="info"
      :closable="false"
      show-icon
      :title="`来自 Saga 补偿池跳转，外部签章单号：${routeExternalSignRef}`"
      style="margin-bottom: 12px"
    />

    <el-tabs v-model="activeTab">
      <el-tab-pane label="单证列表" name="list">
        <el-card shadow="never" class="filter-bar">
          <el-form inline @submit.prevent="load">
            <el-form-item label="业务类型">
              <el-select v-model="filters.business_type" clearable placeholder="全部" style="width: 160px">
                <el-option v-for="opt in businessTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="单证类型">
              <el-input v-model="filters.document_type" clearable placeholder="如 INVOICE" style="width: 140px" />
            </el-form-item>
            <el-form-item label="复核状态">
              <el-select v-model="filters.review_status" clearable placeholder="全部" style="width: 140px">
                <el-option v-for="opt in reviewStatusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="load">查询</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-table v-loading="loading" :data="records" stripe>
          <el-table-column prop="document_type" label="单证类型" width="140" />
          <el-table-column label="业务对象" min-width="180">
            <template #default="{ row }">{{ row.business_type }} / {{ row.business_id }}</template>
          </el-table-column>
          <el-table-column prop="document_no" label="单证编号" width="140" show-overflow-tooltip />
          <el-table-column label="OCR置信度" width="110">
            <template #default="{ row }">
              <span v-if="row.ocr_confidence != null">{{ (row.ocr_confidence * 100).toFixed(1) }}%</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="复核" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="reviewTagType(row.review_status)">{{ row.review_status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="合同/签署" width="150">
            <template #default="{ row }">
              <template v-if="row.document_type === 'PURCHASE_CONTRACT' || row.contract_status !== 'NOT_CONTRACT'">
                <el-tag size="small" :type="signTagType(row.sign_status)">{{ row.sign_status || '—' }}</el-tag>
              </template>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ row.document_status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="total > 0" class="pager">
          <el-pagination
            v-model:current-page="pageNo"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="load"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="必备规则" name="requirements">
        <el-table v-loading="reqLoading" :data="requirements" stripe>
          <el-table-column prop="business_type" label="业务类型" width="140" />
          <el-table-column prop="business_stage" label="阶段" width="120" />
          <el-table-column prop="document_type" label="单证类型" width="140" />
          <el-table-column label="必备" width="80">
            <template #default="{ row }">{{ row.required_flag ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="OCR" width="80">
            <template #default="{ row }">{{ row.ocr_required ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="min_confidence" label="最低置信度" width="120" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="齐备性校验" name="validate">
        <el-card shadow="never">
          <el-form label-width="100px" style="max-width: 520px">
            <el-form-item label="业务类型">
              <el-select v-model="validateForm.business_type" style="width: 100%">
                <el-option v-for="opt in businessTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="业务ID">
              <el-input v-model="validateForm.business_id" />
            </el-form-item>
            <el-form-item label="业务阶段">
              <el-input v-model="validateForm.business_stage" placeholder="如 DISBURSE" />
            </el-form-item>
            <el-form-item label="产品类型">
              <el-input v-model="validateForm.product_type" placeholder="可选" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="validateLoading" @click="runValidate">执行校验</el-button>
            </el-form-item>
          </el-form>
          <el-result v-if="validateResult" :icon="validateResult.passed ? 'success' : 'warning'" :title="validateResult.passed ? '校验通过' : '校验未通过'">
            <template #sub-title>
              <p v-if="validateResult.missing.length">缺失：{{ validateResult.missing.map((m) => m.message).join('；') }}</p>
              <p v-if="validateResult.pending_review.length">待复核：{{ validateResult.pending_review.length }} 项</p>
              <p v-if="validateResult.warnings.length">警告：{{ validateResult.warnings.map((w) => w.message).join('；') }}</p>
            </template>
          </el-result>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" title="单证详情" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="单证类型">{{ detail.document_type }}</el-descriptions-item>
          <el-descriptions-item label="业务对象">{{ detail.business_type }} / {{ detail.business_id }}</el-descriptions-item>
          <el-descriptions-item label="文件ID">{{ detail.file_id }}</el-descriptions-item>
          <el-descriptions-item label="OCR任务">{{ detail.ocr_job_id || '—' }}</el-descriptions-item>
          <el-descriptions-item label="复核状态">{{ detail.review_status }}</el-descriptions-item>
          <el-descriptions-item label="合同状态">{{ detail.contract_status }}</el-descriptions-item>
          <el-descriptions-item v-if="isContractDoc(detail)" label="签署状态">{{ detail.sign_status || '—' }}</el-descriptions-item>
          <el-descriptions-item label="单证状态">{{ detail.document_status }}</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-actions">
          <el-select
            v-if="canSign && isContractDoc(detail) && canInitiateSign(detail) && signProviders.length"
            v-model="initiateProviderCode"
            size="small"
            style="width: 160px; margin-right: 8px"
            placeholder="签章供应商"
          >
            <el-option
              v-for="p in signProviders"
              :key="p.provider_code"
              :label="p.display_name"
              :value="p.provider_code"
            />
          </el-select>
          <el-button v-if="canSign && isContractDoc(detail) && canInitiateSign(detail)" type="primary" @click="onInitiateSign">发起签署</el-button>
          <el-button v-if="canSignRetry && isContractDoc(detail) && canRetrySign(detail)" type="warning" @click="onRetrySign">重试签署</el-button>
          <el-button v-if="canOcr && detail.document_status !== 'ARCHIVED'" @click="onOcr">发起 OCR</el-button>
          <el-button v-if="canSubmitReview && detail.document_status !== 'ARCHIVED'" @click="onSubmitReview">提交复核</el-button>
          <el-button v-if="canApprove && detail.document_status !== 'ARCHIVED'" type="success" @click="onApprove">复核通过</el-button>
          <el-button v-if="canApprove && detail.document_status !== 'ARCHIVED'" type="danger" @click="onReject">驳回</el-button>
          <el-button v-if="canArchive && detail.document_status !== 'ARCHIVED'" @click="onArchive">归档</el-button>
        </div>

        <h4 v-if="isContractDoc(detail)">签署任务</h4>
        <el-table
          v-if="isContractDoc(detail) && signTasks.length"
          :data="signTasks"
          size="small"
          stripe
          highlight-current-row
          @row-click="onSignTaskRowClick"
        >
          <el-table-column prop="provider_code" label="供应商" width="88" />
          <el-table-column label="任务状态" width="118">
            <template #default="{ row }">
              <el-tag size="small" :type="taskTagType(row.task_status)">{{ row.task_status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="回调" width="88">
            <template #default="{ row }">{{ row.callback_status || '—' }}</template>
          </el-table-column>
          <el-table-column prop="external_sign_ref" label="外部单号" min-width="140" show-overflow-tooltip />
          <el-table-column label="重试" width="72">
            <template #default="{ row }">{{ row.retry_count }} / {{ maxRetryCount }}</template>
          </el-table-column>
          <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.failure_reason" class="failure-text">{{ row.failure_reason }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else-if="isContractDoc(detail)" description="暂无签署任务" />

        <el-descriptions
          v-if="selectedSignTask"
          :column="1"
          border
          size="small"
          class="sign-task-detail"
          title="任务详情"
        >
          <el-descriptions-item label="任务 ID">{{ selectedSignTask.id }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ selectedSignTask.provider_code }}</el-descriptions-item>
          <el-descriptions-item label="外部单号">{{ selectedSignTask.external_sign_ref || '—' }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">{{ selectedSignTask.task_status }}</el-descriptions-item>
          <el-descriptions-item label="回调状态">{{ selectedSignTask.callback_status || '—' }}</el-descriptions-item>
          <el-descriptions-item label="失败原因">{{ selectedSignTask.failure_reason || '—' }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ selectedSignTask.retry_count }} / {{ maxRetryCount }}</el-descriptions-item>
          <el-descriptions-item label="最近重试">{{ selectedSignTask.last_retry_at || '—' }}</el-descriptions-item>
          <el-descriptions-item label="签署完成">{{ selectedSignTask.signed_at || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ selectedSignTask.created_at }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ selectedSignTask.updated_at || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4>复核轨迹</h4>
        <el-timeline v-if="detail.review_logs?.length">
          <el-timeline-item v-for="log in detail.review_logs" :key="log.id" :timestamp="log.created_at">
            {{ log.action }} · {{ log.after_status || '' }}
            <span v-if="log.reason"> — {{ log.reason }}</span>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无复核记录" />
      </template>
    </el-drawer>

    <el-drawer v-model="uploadVisible" title="登记单证" size="480px">
      <el-form label-width="100px">
        <el-form-item label="业务类型">
          <el-select v-model="uploadForm.business_type" style="width: 100%">
            <el-option v-for="opt in businessTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input v-model="uploadForm.business_id" />
        </el-form-item>
        <el-form-item label="单证类型">
          <el-input v-model="uploadForm.document_type" placeholder="INVOICE / PURCHASE_CONTRACT" />
        </el-form-item>
        <el-form-item label="单证编号">
          <el-input v-model="uploadForm.document_no" />
        </el-form-item>
        <el-form-item label="文件">
          <input ref="fileInput" type="file" accept=".pdf,.jpg,.jpeg,.png" @change="onFileChange" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="uploadForm.trigger_ocr">登记后立即 OCR</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="submitUpload">提交</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadFile } from '../api/file'
import {
  getContractSignConfig,
  listContractSignProviders,
  type ContractSignProviderInfo
} from '../api/contractSign'
import {
  approveDocument,
  archiveDocument,
  getDocument,
  initiateContractSign,
  listContractSignTasks,
  listDocumentRequirements,
  listDocuments,
  registerDocument,
  rejectDocument,
  retryContractSign,
  submitDocumentReview,
  triggerDocumentOcr,
  validateDocuments,
  type ContractSignTask,
  type DocumentCenterDetail,
  type DocumentCenterItem,
  type DocumentRequirement,
  type DocumentValidateResult
} from '../api/documents'
import { usePermission } from '../composables/usePermission'

const { hasPermission } = usePermission()
const route = useRoute()
const canUpload = computed(() => hasPermission('DOCUMENT_UPLOAD') && hasPermission('FILE_UPLOAD'))
const canOcr = computed(() => hasPermission('AI_OCR_EXECUTE'))
const canSubmitReview = computed(() => hasPermission('DOCUMENT_REVIEW_SUBMIT'))
const canApprove = computed(() => hasPermission('DOCUMENT_REVIEW_APPROVE'))
const canArchive = computed(() => hasPermission('DOCUMENT_ARCHIVE'))
const canViewRequirements = computed(() => hasPermission('DOCUMENT_REQUIREMENT_VIEW'))
const canSign = computed(() => hasPermission('DOCUMENT_CONTRACT_SIGN'))
const canSignRetry = computed(() => hasPermission('DOCUMENT_CONTRACT_SIGN_RETRY'))

const signTasks = ref<ContractSignTask[]>([])
const selectedSignTask = ref<ContractSignTask | null>(null)
const signProviders = ref<ContractSignProviderInfo[]>([])
const defaultSignProvider = ref('MOCK')
const maxRetryCount = ref(3)
const initiateProviderCode = ref('')

const activeTab = ref('list')
const loading = ref(false)
const reqLoading = ref(false)
const validateLoading = ref(false)
const uploading = ref(false)
const records = ref<DocumentCenterItem[]>([])
const requirements = ref<DocumentRequirement[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = ref(20)
const detailVisible = ref(false)
const uploadVisible = ref(false)
const detail = ref<DocumentCenterDetail | null>(null)
const validateResult = ref<DocumentValidateResult | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const routeExternalSignRef = ref('')

const filters = reactive({
  business_type: '',
  document_type: '',
  review_status: ''
})

const uploadForm = reactive({
  business_type: 'TRADE_ORDER',
  business_id: '',
  document_type: 'INVOICE',
  document_no: '',
  trigger_ocr: false
})

const validateForm = reactive({
  business_type: 'FINANCE',
  business_id: '',
  business_stage: 'DISBURSE',
  product_type: 'AGENCY_PURCHASE'
})

const businessTypeOptions = [
  { label: '贸易订单', value: 'TRADE_ORDER' },
  { label: '贸易代采', value: 'AGENCY_PURCHASE' },
  { label: '融资', value: 'FINANCE' },
  { label: '仓储', value: 'WAREHOUSE' },
  { label: '数字凭证', value: 'VOUCHER' }
]

const reviewStatusOptions = [
  { label: '无需复核', value: 'NOT_REQUIRED' },
  { label: '待复核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' }
]

function reviewTagType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

function signTagType(status?: string) {
  if (status === 'SIGNED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'SIGNING') return 'warning'
  if (status === 'PENDING') return 'info'
  return 'info'
}

function taskTagType(status: string) {
  if (status === 'SIGNED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING_CALLBACK' || status === 'SIGNING') return 'warning'
  if (status === 'RETRYABLE') return 'info'
  return 'info'
}

function onSignTaskRowClick(row: ContractSignTask) {
  selectedSignTask.value = row
}

function isContractDoc(doc: DocumentCenterDetail) {
  return doc.document_type === 'PURCHASE_CONTRACT' || doc.contract_status !== 'NOT_CONTRACT'
}

function canInitiateSign(doc: DocumentCenterDetail) {
  return doc.review_status === 'APPROVED'
    && doc.document_status !== 'ARCHIVED'
    && doc.sign_status !== 'SIGNED'
    && doc.sign_status !== 'SIGNING'
}

function canRetrySign(doc: DocumentCenterDetail) {
  return doc.sign_status === 'FAILED' || doc.contract_status === 'SIGN_FAILED'
}

async function load() {
  loading.value = true
  try {
    const { data } = await listDocuments({
      page_no: pageNo.value,
      page_size: pageSize.value,
      business_type: filters.business_type || undefined,
      document_type: filters.document_type || undefined,
      review_status: filters.review_status || undefined
    })
    records.value = data.data.records
    total.value = data.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadRequirements() {
  if (!canViewRequirements.value) return
  reqLoading.value = true
  try {
    const { data } = await listDocumentRequirements()
    requirements.value = data.data
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载规则失败')
  } finally {
    reqLoading.value = false
  }
}

async function openDetail(id: string) {
  try {
    const { data } = await getDocument(id)
    detail.value = data.data
    signTasks.value = []
    selectedSignTask.value = null
    if (isContractDoc(data.data)) {
      try {
        const tasks = await listContractSignTasks(id)
        signTasks.value = tasks.data.data
        if (signTasks.value.length) {
          const matched = routeExternalSignRef.value
            ? signTasks.value.find((t) => t.external_sign_ref === routeExternalSignRef.value)
            : null
          selectedSignTask.value = matched ?? signTasks.value[0]
        }
      } catch {
        signTasks.value = []
      }
    }
    detailVisible.value = true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载详情失败')
  }
}

async function loadSignMeta() {
  if (!hasPermission('CONTRACT_SIGN_CONFIG_VIEW')) return
  try {
    const [cfg, prov] = await Promise.all([getContractSignConfig(), listContractSignProviders()])
    defaultSignProvider.value = cfg.data.data.default_provider
    maxRetryCount.value = cfg.data.data.max_retry_count
    initiateProviderCode.value = cfg.data.data.default_provider
    signProviders.value = prov.data.data
  } catch {
    signProviders.value = []
  }
}

function openUpload() {
  uploadVisible.value = true
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function submitUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const upload = await uploadFile(selectedFile.value, {
      business_type: uploadForm.business_type,
      business_id: uploadForm.business_id
    })
    await registerDocument({
      business_type: uploadForm.business_type,
      business_id: uploadForm.business_id,
      document_type: uploadForm.document_type,
      file_id: upload.file_id,
      document_no: uploadForm.document_no || undefined,
      trigger_ocr: uploadForm.trigger_ocr
    })
    ElMessage.success('单证已登记')
    uploadVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '登记失败')
  } finally {
    uploading.value = false
  }
}

async function refreshDetail() {
  if (!detail.value) return
  const id = detail.value.id
  const { data } = await getDocument(id)
  detail.value = data.data
  if (isContractDoc(data.data)) {
    const tasks = await listContractSignTasks(id)
    signTasks.value = tasks.data.data
    const prevId = selectedSignTask.value?.id
    selectedSignTask.value = signTasks.value.find((t) => t.id === prevId) ?? signTasks.value[0] ?? null
  }
  await load()
}

async function onInitiateSign() {
  if (!detail.value) return
  const provider = initiateProviderCode.value || defaultSignProvider.value
  await ElMessageBox.confirm(
    `确认向供应商 ${provider} 发起合同签署？`,
    '发起签署',
    { type: 'info' }
  )
  await initiateContractSign(detail.value.id, { provider_code: provider })
  ElMessage.success('签署已发起，等待供应商回调')
  await refreshDetail()
}

async function onRetrySign() {
  if (!detail.value) return
  await retryContractSign(detail.value.id)
  ElMessage.success('已重新发起签署')
  await refreshDetail()
}

async function onOcr() {
  if (!detail.value) return
  await triggerDocumentOcr(detail.value.id)
  ElMessage.success('OCR 已发起')
  await refreshDetail()
}

async function onSubmitReview() {
  if (!detail.value) return
  await submitDocumentReview(detail.value.id)
  ElMessage.success('已提交复核')
  await refreshDetail()
}

async function onApprove() {
  if (!detail.value) return
  await approveDocument(detail.value.id, '复核通过')
  ElMessage.success('复核已通过')
  await refreshDetail()
}

async function onReject() {
  if (!detail.value) return
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回单证', {
    confirmButtonText: '提交',
    cancelButtonText: '取消',
    inputPattern: /.+/,
    inputErrorMessage: '驳回原因不能为空'
  })
  await rejectDocument(detail.value.id, value)
  ElMessage.success('已驳回')
  await refreshDetail()
}

async function onArchive() {
  if (!detail.value) return
  await ElMessageBox.confirm('确认归档/作废该单证？', '二次确认', { type: 'warning' })
  await archiveDocument(detail.value.id, '用户归档')
  ElMessage.success('已归档')
  await refreshDetail()
}

async function runValidate() {
  validateLoading.value = true
  try {
    const { data } = await validateDocuments({ ...validateForm })
    validateResult.value = data.data
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '校验失败')
  } finally {
    validateLoading.value = false
  }
}

onMounted(async () => {
  const documentId = route.query.document_id as string | undefined
  const externalSignRef = route.query.external_sign_ref as string | undefined
  if (externalSignRef) {
    routeExternalSignRef.value = externalSignRef
  }
  await load()
  await loadRequirements()
  await loadSignMeta()
  if (documentId) {
    await openDetail(documentId)
  } else if (externalSignRef) {
    ElMessage.info(`未携带单证 ID，请从列表打开合同单证并核对签章任务：${externalSignRef}`)
  }
})
</script>

<style scoped>
.document-center { padding: 0 4px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.subtitle { color: #666; margin: 4px 0 0; font-size: 13px; }
.filter-bar { margin-bottom: 16px; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.drawer-actions { margin: 16px 0; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.sign-task-detail { margin: 12px 0 16px; }
.failure-text { color: #c45656; }
</style>
