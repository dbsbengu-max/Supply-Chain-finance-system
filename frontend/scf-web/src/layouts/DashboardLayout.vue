<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">SCF 平台</div>
      <el-menu :default-active="active" router>
        <el-menu-item index="/">工作台</el-menu-item>
        <el-menu-item index="/pilot/closure">试点闭环</el-menu-item>
        <el-menu-item v-if="canViewInbox" index="/inbox">消息待办</el-menu-item>
        <el-menu-item v-if="canViewAudit" index="/audit/logs">审计日志</el-menu-item>
        <el-menu-item v-if="canViewSagaOps" index="/saga/ops">Saga 监控</el-menu-item>
        <el-menu-item index="/customers">客户/KYC</el-menu-item>
        <el-menu-item index="/projects">项目配置</el-menu-item>
        <el-menu-item index="/trade/orders">订单贸易</el-menu-item>
        <el-menu-item index="/agency-purchase/applications">贸易代采</el-menu-item>
        <el-menu-item index="/warehouse/warehouses">仓库管理</el-menu-item>
        <el-menu-item index="/warehouse/inventories">库存货权</el-menu-item>
        <el-menu-item index="/finance/applications">融资管理</el-menu-item>
        <el-menu-item v-if="canViewVouchers" index="/vouchers">数字凭证</el-menu-item>
        <el-menu-item v-if="canViewBankFlows" index="/accounts/bank-flows">银行流水</el-menu-item>
        <el-menu-item v-if="canViewClearing" index="/accounts/clearing">清分中心</el-menu-item>
        <el-menu-item v-if="canViewClearingRules" index="/accounts/clearing-rules">清分规则</el-menu-item>
        <el-menu-item v-if="canViewBi" index="/bi/dashboard">经营看板</el-menu-item>
        <el-menu-item v-if="canViewRiskAlerts" index="/risk/alerts">风险预警</el-menu-item>
        <el-menu-item index="/pricing">价格管理</el-menu-item>
        <el-menu-item index="/ai/ocr">OCR 识别</el-menu-item>
        <el-menu-item index="/imports/excel">Excel 导入</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>{{ auth.userName }}</span>
        <el-button link type="danger" @click="logout">退出</el-button>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { usePermission } from '../composables/usePermission'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { hasPermission } = usePermission()
const active = computed(() => route.path)
const canViewBankFlows = computed(() => hasPermission('ACCOUNT_FLOW_VIEW'))
const canViewClearing = computed(() => hasPermission('CLEARING_VIEW'))
const canViewClearingRules = computed(() => hasPermission('CLEARING_RULE_LIST'))
const canViewBi = computed(() => hasPermission('BI_VIEW'))
const canViewRiskAlerts = computed(() => hasPermission('RISK_ALERT_VIEW'))
const canViewInbox = computed(() => hasPermission('INBOX_VIEW'))
const canViewAudit = computed(() => hasPermission('AUDIT_VIEW'))
const canViewSagaOps = computed(() => hasPermission('SAGA_OPS_VIEW'))
const canViewVouchers = computed(() => hasPermission('VOUCHER_VIEW'))

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside { background: #001529; color: #fff; }
.brand { padding: 20px; font-weight: 600; color: #fff; }
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eee;
}
</style>
