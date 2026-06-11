<template>
  <div>
    <div class="toolbar">
      <h2>凭证详情</h2>
      <el-button @click="router.push('/vouchers')">返回</el-button>
    </div>
    <el-card v-if="detail" shadow="never">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="凭证编号">{{ detail.voucher.voucher_no }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.voucher.voucher_status }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ detail.voucher.currency }}</el-descriptions-item>
        <el-descriptions-item label="票面金额">{{ formatMoney(detail.voucher.amount, detail.voucher.currency) }}</el-descriptions-item>
        <el-descriptions-item label="可用余额">{{ formatMoney(detail.voucher.available_amount, detail.voucher.currency) }}</el-descriptions-item>
        <el-descriptions-item label="到期日">{{ detail.voucher.due_date }}</el-descriptions-item>
        <template v-if="detail.finance_summary">
          <el-descriptions-item label="融资占用">{{ formatMoney(detail.finance_summary.finance_occupied_amount, detail.voucher.currency) }}</el-descriptions-item>
          <el-descriptions-item label="已释放">{{ formatMoney(detail.finance_summary.released_amount, detail.voucher.currency) }}</el-descriptions-item>
          <el-descriptions-item label="待兑付">{{ formatMoney(detail.finance_summary.pending_redeem_amount, detail.voucher.currency) }}</el-descriptions-item>
        </template>
        <el-descriptions-item label="签发方">{{ detail.voucher.issuer_id }}</el-descriptions-item>
        <el-descriptions-item label="承兑方">{{ detail.voucher.acceptor_id }}</el-descriptions-item>
        <el-descriptions-item label="持有人">{{ detail.voucher.holder_id }}</el-descriptions-item>
      </el-descriptions>
      <div class="action-row">
        <el-button v-if="canIssue && detail.voucher.voucher_status === 'DRAFT'" type="success" @click="onIssue">签发</el-button>
        <el-button
          v-if="canTransfer && ['ACCEPTED', 'ISSUED', 'TRANSFERRED'].includes(detail.voucher.voucher_status)"
          @click="transferVisible = true"
        >
          转让
        </el-button>
        <el-button
          v-if="canSplit && ['ACCEPTED', 'ISSUED', 'TRANSFERRED'].includes(detail.voucher.voucher_status)"
          @click="splitVisible = true"
        >
          拆分
        </el-button>
        <el-button
          v-if="canRedeem && ['ACCEPTED', 'ISSUED', 'TRANSFERRED'].includes(detail.voucher.voucher_status)"
          type="warning"
          @click="onRedeem"
        >
          兑付申请
        </el-button>
        <el-button
          v-if="canRedeemExecute && detail.voucher.voucher_status === 'REDEEM_APPROVED'"
          type="primary"
          @click="executeVisible = true"
        >
          兑付执行
        </el-button>
        <el-button v-if="canCancel && ['DRAFT','ACCEPTED'].includes(detail.voucher.voucher_status)" type="danger" @click="onCancel">作废</el-button>
      </div>
    </el-card>

    <el-card v-if="detail" class="tabs-card" shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="流转记录" name="flows">
          <el-table :data="detail.flows ?? []" stripe>
            <el-table-column prop="flow_type" label="类型" width="130" />
            <el-table-column prop="from_holder_id" label="转出方" width="150" />
            <el-table-column prop="to_holder_id" label="转入方" width="150" />
            <el-table-column label="金额" width="140">
              <template #default="{ row }">{{ formatMoney(row.amount, detail?.voucher.currency) }}</template>
            </el-table-column>
            <el-table-column label="操作后余额" width="150">
              <template #default="{ row }">{{ formatMoney(row.after_available_amount, detail?.voucher.currency) }}</template>
            </el-table-column>
            <el-table-column prop="operated_by" label="操作人" width="120" />
            <el-table-column prop="operated_at" label="时间" min-width="180" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="关联融资单" name="finances">
          <el-table :data="detail.related_finances ?? []" stripe>
            <el-table-column prop="finance_no" label="融资编号" width="160" />
            <el-table-column prop="finance_status" label="状态" width="120" />
            <el-table-column prop="product_type" label="产品类型" width="140" />
            <el-table-column label="已放款" width="140">
              <template #default="{ row }">{{ formatMoney(row.disbursed_amount, row.currency) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="还款/清分流水" name="clearing">
          <el-table :data="detail.clearing_records ?? []" stripe>
            <el-table-column prop="repayment_id" label="还款单" width="140" />
            <el-table-column prop="finance_id" label="融资单" width="140" />
            <el-table-column label="还款金额" width="140">
              <template #default="{ row }">{{ formatMoney(row.repayment_amount, detail?.voucher.currency) }}</template>
            </el-table-column>
            <el-table-column label="本金" width="120">
              <template #default="{ row }">{{ formatMoney(row.principal_amount, detail?.voucher.currency) }}</template>
            </el-table-column>
            <el-table-column label="利息" width="120">
              <template #default="{ row }">{{ formatMoney(row.interest_amount, detail?.voucher.currency) }}</template>
            </el-table-column>
            <el-table-column prop="clearing_status" label="清分状态" width="120" />
            <el-table-column prop="created_at" label="时间" min-width="180" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="兑付记录" name="redeem">
          <el-table :data="detail.redeem_records ?? []" stripe>
            <el-table-column prop="flow_type" label="类型" width="130" />
            <el-table-column prop="from_holder_id" label="转出方" width="150" />
            <el-table-column prop="to_holder_id" label="转入方" width="150" />
            <el-table-column label="金额" width="140">
              <template #default="{ row }">{{ formatMoney(row.amount, detail?.voucher.currency) }}</template>
            </el-table-column>
            <el-table-column prop="operated_by" label="操作人" width="120" />
            <el-table-column prop="operated_at" label="时间" min-width="180" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="transferVisible" title="凭证转让" width="460px">
      <el-form label-width="100px">
        <el-form-item label="新持有人"><el-input v-model="transferForm.to_holder_id" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="transferForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" @click="onTransfer">确认转让</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="splitVisible" title="凭证拆分" width="460px">
      <el-form label-width="100px">
        <el-form-item label="拆分金额"><el-input v-model="splitForm.amount" /></el-form-item>
        <el-form-item label="子凭证持有人"><el-input v-model="splitForm.to_holder_id" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="splitForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="splitVisible = false">取消</el-button>
        <el-button type="primary" @click="onSplit">确认拆分</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="executeVisible" title="兑付执行" width="520px">
      <el-alert type="warning" show-icon :closable="false" title="兑付执行需二次确认，将自承兑方账户划付至持有人账户。" />
      <el-form class="execute-form" label-width="110px">
        <el-form-item label="出款账户"><el-input v-model="executeForm.payer_account_id" /></el-form-item>
        <el-form-item label="收款账户"><el-input v-model="executeForm.receiver_account_id" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="executeForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executeVisible = false">取消</el-button>
        <el-button type="primary" @click="onRedeemExecute">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { usePermission } from '../composables/usePermission'
import { apiErrorMessage } from '../utils/apiError'
import { formatMoney } from '../utils/format'
import {
  cancelVoucher,
  getVoucher,
  issueVoucher,
  redeemExecuteVoucher,
  redeemVoucher,
  splitVoucher,
  transferVoucher,
  type VoucherDetail
} from '../api/voucher'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()
const canIssue = hasPermission('VOUCHER_ISSUE')
const canTransfer = hasPermission('VOUCHER_TRANSFER')
const canSplit = hasPermission('VOUCHER_SPLIT')
const canRedeem = hasPermission('VOUCHER_REDEEM') || hasPermission('VOUCHER_REDEEM_APPLY')
const canRedeemExecute = hasPermission('VOUCHER_REDEEM_EXECUTE')
const canCancel = hasPermission('VOUCHER_CANCEL')
const detail = ref<VoucherDetail | null>(null)
const activeTab = ref('flows')
const transferVisible = ref(false)
const splitVisible = ref(false)
const executeVisible = ref(false)
const transferForm = reactive({ to_holder_id: '', remark: '' })
const splitForm = reactive({ amount: '', to_holder_id: '', remark: '' })
const executeForm = reactive({
  payer_account_id: '',
  receiver_account_id: '',
  remark: ''
})

async function load() {
  try {
    const res = await getVoucher(route.params.id as string)
    if (!res.success) throw new Error(res.message || '加载凭证失败')
    detail.value = {
      voucher: res.data.voucher ?? res.data,
      flows: res.data.flows ?? [],
      finance_summary: res.data.finance_summary,
      related_finances: res.data.related_finances ?? [],
      clearing_records: res.data.clearing_records ?? [],
      redeem_records: res.data.redeem_records ?? []
    }
  } catch (e) {
    ElMessage.error(apiErrorMessage(e, '加载凭证失败'))
    detail.value = null
  }
}

async function run(action: () => Promise<any>, success: string) {
  try {
    const res = await action()
    if (!res.success) throw new Error(res.message || success)
    ElMessage.success(success)
    await load()
  } catch (e: any) {
    ElMessage.error(apiErrorMessage(e, '操作失败'))
  }
}

function onIssue() {
  run(() => issueVoucher(route.params.id as string), '凭证已签发')
}

function onTransfer() {
  run(() => transferVoucher(route.params.id as string, transferForm), '凭证已转让')
  transferVisible.value = false
}

function onSplit() {
  run(() => splitVoucher(route.params.id as string, splitForm), '凭证已拆分')
  splitVisible.value = false
}

function onRedeem() {
  run(() => redeemVoucher(route.params.id as string, { remark: '兑付申请' }), '兑付申请已提交，等待审批')
}

async function onRedeemExecute() {
  await ElMessageBox.confirm('确认执行兑付？该操作将触发账户划付。', '二次确认', { type: 'warning' })
  const idempotencyKey = `REDEEM-${route.params.id}-${Date.now()}`
  run(
    () =>
      redeemExecuteVoucher(
        route.params.id as string,
        executeForm,
        { idempotencyKey, secondaryAuthToken: 'MOCK-APPROVED' }
      ),
    '兑付已执行'
  )
  executeVisible.value = false
}

async function onCancel() {
  await ElMessageBox.confirm('确认作废该凭证？', '二次确认', { type: 'warning' })
  run(() => cancelVoucher(route.params.id as string), '凭证已作废')
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.action-row {
  margin-top: 16px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.tabs-card {
  margin-top: 16px;
}
.execute-form {
  margin-top: 16px;
}
</style>
