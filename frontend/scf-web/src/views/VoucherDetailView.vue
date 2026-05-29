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
        <el-descriptions-item label="票面金额">{{ detail.voucher.amount }}</el-descriptions-item>
        <el-descriptions-item label="可用余额">{{ detail.voucher.available_amount }}</el-descriptions-item>
        <el-descriptions-item label="到期日">{{ detail.voucher.due_date }}</el-descriptions-item>
        <template v-if="detail.finance_summary">
          <el-descriptions-item label="融资占用">{{ detail.finance_summary.finance_occupied_amount }}</el-descriptions-item>
          <el-descriptions-item label="已释放">{{ detail.finance_summary.released_amount }}</el-descriptions-item>
          <el-descriptions-item label="待兑付">{{ detail.finance_summary.pending_redeem_amount }}</el-descriptions-item>
        </template>
        <el-descriptions-item label="签发方">{{ detail.voucher.issuer_id }}</el-descriptions-item>
        <el-descriptions-item label="承兑方">{{ detail.voucher.acceptor_id }}</el-descriptions-item>
        <el-descriptions-item label="持有人">{{ detail.voucher.holder_id }}</el-descriptions-item>
      </el-descriptions>
      <div class="action-row">
        <el-button v-if="canIssue && detail.voucher.voucher_status === 'DRAFT'" type="success" @click="onIssue">签发</el-button>
        <el-button v-if="canTransfer && detail.voucher.voucher_status === 'ACCEPTED'" @click="transferVisible = true">转让</el-button>
        <el-button v-if="canSplit && detail.voucher.voucher_status === 'ACCEPTED'" @click="splitVisible = true">拆分</el-button>
        <el-button v-if="canRedeem && detail.voucher.voucher_status === 'ACCEPTED'" type="warning" @click="onRedeem">兑付申请</el-button>
        <el-button v-if="canCancel && ['DRAFT','ACCEPTED'].includes(detail.voucher.voucher_status)" type="danger" @click="onCancel">作废</el-button>
      </div>
    </el-card>

    <el-card class="flow-card" shadow="never">
      <template #header>流转记录</template>
      <el-table :data="detail?.flows ?? []" stripe>
        <el-table-column prop="flow_type" label="类型" width="130" />
        <el-table-column prop="from_holder_id" label="转出方" width="150" />
        <el-table-column prop="to_holder_id" label="转入方" width="150" />
        <el-table-column prop="amount" label="金额" width="120" />
        <el-table-column prop="after_available_amount" label="操作后余额" width="130" />
        <el-table-column prop="operated_by" label="操作人" width="120" />
        <el-table-column prop="operated_at" label="时间" min-width="180" />
      </el-table>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { usePermission } from '../composables/usePermission'
import {
  cancelVoucher,
  getVoucher,
  issueVoucher,
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
const canRedeem = hasPermission('VOUCHER_REDEEM')
const canCancel = hasPermission('VOUCHER_CANCEL')
const detail = ref<VoucherDetail | null>(null)
const transferVisible = ref(false)
const splitVisible = ref(false)
const transferForm = reactive({ to_holder_id: '', remark: '' })
const splitForm = reactive({ amount: '', to_holder_id: '', remark: '' })

async function load() {
  const res = await getVoucher(route.params.id as string)
  if (res.success) {
    detail.value = {
      voucher: res.data.voucher ?? res.data,
      flows: res.data.flows ?? [],
      finance_summary: res.data.finance_summary
    }
  }
}

async function run(action: () => Promise<any>, success: string) {
  try {
    const res = await action()
    if (!res.success) throw new Error(res.message || success)
    ElMessage.success(success)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '操作失败')
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
  run(() => redeemVoucher(route.params.id as string, { remark: 'Mock 兑付申请' }), '兑付申请已提交')
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
}
.flow-card {
  margin-top: 16px;
}
</style>
