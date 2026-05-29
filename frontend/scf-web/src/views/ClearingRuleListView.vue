<template>
  <div v-if="!canView">
    <el-result icon="warning" title="无权访问" sub-title="当前身份无清分规则查看权限（CLEARING_RULE_LIST）" />
  </div>
  <div v-else>
    <div class="toolbar">
      <h2>清分规则</h2>
      <el-button v-if="canCreate" type="primary" @click="openCreate">新建规则</el-button>
    </div>

    <el-form :inline="true" class="filters" @submit.prevent="load">
      <el-form-item label="产品类型">
        <el-select v-model="filters.product_type" clearable placeholder="全部" style="width: 160px">
          <el-option label="凭证融资" value="VOUCHER_FINANCE" />
          <el-option label="订单融资" value="ORDER_FINANCE" />
        </el-select>
      </el-form-item>
      <el-form-item label="审批状态">
        <el-select v-model="filters.review_status" clearable placeholder="全部" style="width: 140px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审批" value="PENDING" />
          <el-option label="已批准" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rules" v-loading="loading" stripe>
      <el-table-column prop="rule_name" label="规则名称" min-width="160" />
      <el-table-column prop="product_type" label="产品类型" width="140" />
      <el-table-column prop="funding_party_id" label="资方 ID" width="140">
        <template #default="{ row }">
          <span>{{ row.funding_party_id || '全局' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="currency_rule" label="币种规则" width="140" />
      <el-table-column prop="effective_from" label="生效日" width="120" />
      <el-table-column prop="review_status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.review_status)" size="small">{{ statusLabel(row.review_status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version_no" label="版本" width="70" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="canUpdate && editable(row.review_status)"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-if="canSubmit && (row.review_status === 'DRAFT' || row.review_status === 'REJECTED')"
            link
            type="warning"
            @click="onSubmit(row)"
          >
            提交
          </el-button>
          <el-button
            v-if="canApprove && row.review_status === 'PENDING'"
            link
            type="success"
            @click="onApprove(row)"
          >
            批准
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showForm" :title="formMode === 'create' ? '新建清分规则' : '编辑清分规则'" width="720px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.rule_name" />
        </el-form-item>
        <el-form-item label="产品类型" required>
          <el-select v-model="form.product_type" style="width: 100%">
            <el-option label="凭证融资 VOUCHER_FINANCE" value="VOUCHER_FINANCE" />
            <el-option label="订单融资 ORDER_FINANCE" value="ORDER_FINANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="资方 ID">
          <el-input v-model="form.funding_party_id" placeholder="留空表示全局规则；资方用户自动绑定本企业" />
        </el-form-item>
        <el-form-item label="币种规则" required>
          <el-select v-model="form.currency_rule" style="width: 100%">
            <el-option label="原币 ORIGINAL_CURRENCY" value="ORIGINAL_CURRENCY" />
            <el-option label="本位币 LOCAL_CURRENCY" value="LOCAL_CURRENCY" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效日" required>
          <el-date-picker v-model="form.effective_from" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效日">
          <el-date-picker v-model="form.effective_to" type="date" value-format="YYYY-MM-DD" clearable style="width: 100%" />
        </el-form-item>
        <el-form-item label="优先级 JSON" required>
          <el-input v-model="form.priority_json" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="费用公式 JSON">
          <el-input v-model="form.fee_formula_json" type="textarea" :rows="3" placeholder='{"interest":"principal*rate*days/360"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="规则详情" width="720px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="规则名称">{{ detail.rule_name }}</el-descriptions-item>
        <el-descriptions-item label="产品类型">{{ detail.product_type }}</el-descriptions-item>
        <el-descriptions-item label="资方">{{ detail.funding_party_id || '全局' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(detail.review_status) }}</el-descriptions-item>
        <el-descriptions-item label="生效区间">{{ detail.effective_from }} ~ {{ detail.effective_to || '长期' }}</el-descriptions-item>
        <el-descriptions-item label="priority_json">
          <pre class="json-block">{{ detail.priority_json }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="fee_formula_json">
          <pre class="json-block">{{ detail.fee_formula_json || '—' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveClearingRule,
  createClearingRule,
  DEFAULT_PRIORITY_JSON,
  listClearingRules,
  submitClearingRule,
  updateClearingRule,
  type ClearingRule
} from '../api/account'
import { usePermission } from '../composables/usePermission'

function apiErrorMessage(e: unknown, fallback: string) {
  const err = e as { response?: { data?: { message?: string } }; message?: string }
  return err.response?.data?.message || err.message || fallback
}

const { hasPermission } = usePermission()
const canView = computed(() => hasPermission('CLEARING_RULE_LIST'))
const canCreate = computed(() => hasPermission('CLEARING_RULE_CREATE'))
const canUpdate = computed(() => hasPermission('CLEARING_RULE_UPDATE'))
const canSubmit = computed(() => hasPermission('CLEARING_RULE_SUBMIT'))
const canApprove = computed(() => hasPermission('CLEARING_RULE_APPROVE'))

const loading = ref(false)
const saving = ref(false)
const rules = ref<ClearingRule[]>([])
const filters = reactive({ product_type: '', review_status: '' })

const showForm = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const editingId = ref('')
const form = reactive({
  rule_name: '',
  product_type: 'VOUCHER_FINANCE',
  funding_party_id: '',
  currency_rule: 'ORIGINAL_CURRENCY',
  effective_from: '',
  effective_to: '',
  priority_json: DEFAULT_PRIORITY_JSON,
  fee_formula_json: '{"interest":"principal*rate*days/360"}'
})

const showDetail = ref(false)
const detail = ref<ClearingRule | null>(null)

function statusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待审批',
    APPROVED: '已批准',
    REJECTED: '已驳回'
  }
  return map[status] || status
}

function statusTagType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

function editable(status: string) {
  return status === 'DRAFT' || status === 'REJECTED'
}

function resetForm() {
  form.rule_name = ''
  form.product_type = 'VOUCHER_FINANCE'
  form.funding_party_id = ''
  form.currency_rule = 'ORIGINAL_CURRENCY'
  form.effective_from = new Date().toISOString().slice(0, 10)
  form.effective_to = ''
  form.priority_json = DEFAULT_PRIORITY_JSON
  form.fee_formula_json = '{"interest":"principal*rate*days/360"}'
}

function openCreate() {
  formMode.value = 'create'
  editingId.value = ''
  resetForm()
  showForm.value = true
}

function openEdit(row: ClearingRule) {
  formMode.value = 'edit'
  editingId.value = row.id
  form.rule_name = row.rule_name
  form.product_type = row.product_type
  form.funding_party_id = row.funding_party_id || ''
  form.currency_rule = row.currency_rule
  form.effective_from = row.effective_from
  form.effective_to = row.effective_to || ''
  form.priority_json = row.priority_json
  form.fee_formula_json = row.fee_formula_json || ''
  showForm.value = true
}

function openDetail(row: ClearingRule) {
  detail.value = row
  showDetail.value = true
}

function buildPayload() {
  return {
    funding_party_id: form.funding_party_id || undefined,
    product_type: form.product_type,
    rule_name: form.rule_name,
    priority_json: form.priority_json,
    fee_formula_json: form.fee_formula_json || undefined,
    currency_rule: form.currency_rule,
    effective_from: form.effective_from,
    effective_to: form.effective_to || undefined
  }
}

async function load() {
  loading.value = true
  try {
    const res = await listClearingRules({
      page_no: 1,
      page_size: 50,
      product_type: filters.product_type || undefined,
      review_status: filters.review_status || undefined
    })
    if (res.success) {
      rules.value = res.data?.records || []
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (e: unknown) {
    ElMessage.error(apiErrorMessage(e, '加载失败'))
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.product_type = ''
  filters.review_status = ''
  load()
}

async function onSave() {
  if (!form.rule_name || !form.effective_from) {
    ElMessage.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    const res =
      formMode.value === 'create'
        ? await createClearingRule(payload)
        : await updateClearingRule(editingId.value, payload)
    if (res.success) {
      ElMessage.success('已保存')
      showForm.value = false
      await load()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e: unknown) {
    ElMessage.error(apiErrorMessage(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function onSubmit(row: ClearingRule) {
  await ElMessageBox.confirm(`提交规则「${row.rule_name}」进入审批？`, '确认')
  try {
    const res = await submitClearingRule(row.id)
    if (res.success) {
      ElMessage.success('已提交')
      await load()
    } else {
      ElMessage.error(res.message || '提交失败')
    }
  } catch (e: unknown) {
    if (e !== 'cancel') ElMessage.error(apiErrorMessage(e, '提交失败'))
  }
}

async function onApprove(row: ClearingRule) {
  await ElMessageBox.confirm(`批准规则「${row.rule_name}」？批准后将用于清分试算。`, '确认')
  try {
    const res = await approveClearingRule(row.id)
    if (res.success) {
      ElMessage.success('已批准')
      await load()
    } else {
      ElMessage.error(res.message || '批准失败')
    }
  } catch (e: unknown) {
    if (e !== 'cancel') ElMessage.error(apiErrorMessage(e, '批准失败'))
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
.filters { margin-bottom: 16px; }
.json-block {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
</style>
