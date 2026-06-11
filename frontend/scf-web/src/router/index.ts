import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { hasRoutePermission, routeRequiresPermission } from './permissions'

const LoginView = () => import('../views/LoginView.vue')
const DashboardLayout = () => import('../layouts/DashboardLayout.vue')
const WorkbenchView = () => import('../views/WorkbenchView.vue')
const ForbiddenView = () => import('../views/ForbiddenView.vue')

const EnterpriseListView = () => import('../views/EnterpriseListView.vue')
const ProjectListView = () => import('../views/ProjectListView.vue')
const OrderListView = () => import('../views/OrderListView.vue')
const PricingView = () => import('../views/PricingView.vue')
const FinanceListView = () => import('../views/FinanceListView.vue')
const AgencyPurchaseListView = () => import('../views/AgencyPurchaseListView.vue')
const AgencyPurchaseFormView = () => import('../views/AgencyPurchaseFormView.vue')
const AgencyPurchaseDetailView = () => import('../views/AgencyPurchaseDetailView.vue')
const OcrCenterView = () => import('../views/OcrCenterView.vue')
const ExcelImportView = () => import('../views/ExcelImportView.vue')
const WarehouseListView = () => import('../views/WarehouseListView.vue')
const WarehouseDetailView = () => import('../views/WarehouseDetailView.vue')
const InventoryListView = () => import('../views/InventoryListView.vue')
const InventoryDetailView = () => import('../views/InventoryDetailView.vue')
const BankFlowListView = () => import('../views/BankFlowListView.vue')
const ClearingWorkbenchView = () => import('../views/ClearingWorkbenchView.vue')
const ClearingRuleListView = () => import('../views/ClearingRuleListView.vue')
const BiDashboardView = () => import('../views/BiDashboardView.vue')
const RiskAlertCenterView = () => import('../views/RiskAlertCenterView.vue')
const InboxCenterView = () => import('../views/InboxCenterView.vue')
const AuditCenterView = () => import('../views/AuditCenterView.vue')
const SagaOpsCenterView = () => import('../views/SagaOpsCenterView.vue')
const DocumentCenterView = () => import('../views/DocumentCenterView.vue')
const ContractSignConfigView = () => import('../views/ContractSignConfigView.vue')
const PilotClosureView = () => import('../views/PilotClosureView.vue')
const LaunchHubView = () => import('../views/LaunchHubView.vue')
const UatAcceptanceView = () => import('../views/UatAcceptanceView.vue')
const VoucherListView = () => import('../views/VoucherListView.vue')
const VoucherDetailView = () => import('../views/VoucherDetailView.vue')

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
        { path: 'saga/ops', name: 'saga-ops', component: SagaOpsCenterView },
        { path: 'documents/center', name: 'document-center', component: DocumentCenterView },
        { path: 'integrations/contracts/sign-config', name: 'contract-sign-config', component: ContractSignConfigView },
        { path: 'pilot/closure', name: 'pilot-closure', component: PilotClosureView },
        { path: 'launch/hub', name: 'launch-hub', component: LaunchHubView },
        { path: 'uat/acceptance', name: 'uat-acceptance', component: UatAcceptanceView },
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
