import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import DashboardLayout from '../layouts/DashboardLayout.vue'
import WorkbenchView from '../views/WorkbenchView.vue'

import EnterpriseListView from '../views/EnterpriseListView.vue'
import ProjectListView from '../views/ProjectListView.vue'
import OrderListView from '../views/OrderListView.vue'
import PricingView from '../views/PricingView.vue'
import FinanceListView from '../views/FinanceListView.vue'
import AgencyPurchaseListView from '../views/AgencyPurchaseListView.vue'
import AgencyPurchaseFormView from '../views/AgencyPurchaseFormView.vue'
import AgencyPurchaseDetailView from '../views/AgencyPurchaseDetailView.vue'
import OcrCenterView from '../views/OcrCenterView.vue'
import ExcelImportView from '../views/ExcelImportView.vue'
import WarehouseListView from '../views/WarehouseListView.vue'
import WarehouseDetailView from '../views/WarehouseDetailView.vue'
import InventoryListView from '../views/InventoryListView.vue'
import InventoryDetailView from '../views/InventoryDetailView.vue'
import BankFlowListView from '../views/BankFlowListView.vue'
import ClearingWorkbenchView from '../views/ClearingWorkbenchView.vue'
import ClearingRuleListView from '../views/ClearingRuleListView.vue'
import BiDashboardView from '../views/BiDashboardView.vue'
import RiskAlertCenterView from '../views/RiskAlertCenterView.vue'
import InboxCenterView from '../views/InboxCenterView.vue'
import AuditCenterView from '../views/AuditCenterView.vue'
import VoucherListView from '../views/VoucherListView.vue'
import VoucherDetailView from '../views/VoucherDetailView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import { hasRoutePermission, routeRequiresPermission } from './permissions'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/',
      component: DashboardLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'workbench', component: WorkbenchView },
        { path: 'customers', name: 'customers', component: EnterpriseListView },
        { path: 'projects', name: 'projects', component: ProjectListView },
        { path: 'trade/orders', name: 'trade-orders', component: OrderListView },
        { path: 'agency-purchase/applications', name: 'agency-purchase-list', component: AgencyPurchaseListView },
        { path: 'agency-purchase/applications/new', name: 'agency-purchase-new', component: AgencyPurchaseFormView },
        { path: 'agency-purchase/applications/:id/edit', name: 'agency-purchase-edit', component: AgencyPurchaseFormView },
        { path: 'agency-purchase/applications/:id', name: 'agency-purchase-detail', component: AgencyPurchaseDetailView },
        { path: 'finance/applications', name: 'finance-applications', component: FinanceListView },
        { path: 'vouchers', name: 'voucher-list', component: VoucherListView },
        { path: 'vouchers/:id', name: 'voucher-detail', component: VoucherDetailView },
        { path: 'accounts/bank-flows', name: 'bank-flow-list', component: BankFlowListView },
        { path: 'accounts/clearing', name: 'clearing-workbench', component: ClearingWorkbenchView },
        { path: 'accounts/clearing-rules', name: 'clearing-rule-list', component: ClearingRuleListView },
        { path: 'bi/dashboard', name: 'bi-dashboard', component: BiDashboardView },
        { path: 'risk/alerts', name: 'risk-alerts', component: RiskAlertCenterView },
        { path: 'inbox', name: 'inbox-center', component: InboxCenterView },
        { path: 'audit/logs', name: 'audit-center', component: AuditCenterView },
        { path: 'forbidden', name: 'forbidden', component: ForbiddenView },
        { path: 'pricing', name: 'pricing', component: PricingView },
        { path: 'ai/ocr', name: 'ai-ocr', component: OcrCenterView },
        { path: 'imports/excel', name: 'excel-import', component: ExcelImportView },
        { path: 'warehouse/warehouses', name: 'warehouse-list', component: WarehouseListView },
        { path: 'warehouse/warehouses/:id', name: 'warehouse-detail', component: WarehouseDetailView },
        { path: 'warehouse/inventories', name: 'inventory-list', component: InventoryListView },
        { path: 'warehouse/inventories/:id', name: 'inventory-detail', component: InventoryDetailView }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.token) {
    return '/login'
  }
  if (to.path === '/login' && auth.token) {
    return '/'
  }
  if (to.name === 'forbidden') {
    return true
  }
  const required = routeRequiresPermission(to.name)
  if (required && auth.token && !hasRoutePermission(auth.permissions, required)) {
    const perm = Array.isArray(required) ? required.join(',') : required
    return { name: 'forbidden', query: { perm, from: to.fullPath } }
  }
})

export default router
