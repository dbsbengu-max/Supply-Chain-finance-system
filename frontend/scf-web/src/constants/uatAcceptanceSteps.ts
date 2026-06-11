/** EA-049 / EA-050 manual UAT checklist M1–M12 (Mock / pilot, no real vendor). */

export type UatStepStatus = 'pending' | 'pass' | 'fail' | 'skip'

export interface UatAcceptanceStep {
  id: string
  order: number
  module: string
  entryPath: string
  entryLabel: string
  account: string
  accountPassword?: string
  operations: string[]
  passCriteria: string
  defaultNote?: string
}

export const UAT_STORAGE_KEY = 'scf-uat-acceptance-v1'

export const UAT_ACCEPTANCE_STEPS: UatAcceptanceStep[] = [
  {
    id: 'M1',
    order: 1,
    module: '登录 / 身份',
    entryPath: '/login',
    entryLabel: '登录页 → 工作台',
    account: 'platform_admin / Admin@123',
    operations: [
      '打开登录页，输入 platform_admin / Admin@123',
      '登录成功后进入工作台',
      '在「当前身份」下拉切换 identity（若有多个）'
    ],
    passCriteria: '进入工作台，侧边栏菜单完整可见'
  },
  {
    id: 'M2',
    order: 2,
    module: '功能上线',
    entryPath: '/launch/hub',
    entryLabel: '功能上线收口',
    account: 'platform_admin',
    operations: [
      '侧边栏进入「功能上线」或打开 /launch/hub',
      '确认六张能力卡片（配置、供应商、补偿池、签章、BI、试点闭环）',
      '逐一点击「进入」验证路由跳转'
    ],
    passCriteria: '六张卡片均可跳转，无 403'
  },
  {
    id: 'M3',
    order: 3,
    module: '试点闭环',
    entryPath: '/pilot/closure',
    entryLabel: '试点闭环向导',
    account: 'platform_admin',
    operations: [
      '打开试点闭环向导',
      '确认时间轴含仓储货权、合同签章步骤',
      '点击各步骤「前往」进入对应业务页'
    ],
    passCriteria: '向导步骤可进入，仓储与签章节点可见'
  },
  {
    id: 'M4',
    order: 4,
    module: '客户 / KYC',
    entryPath: '/customers',
    entryLabel: '客户 / KYC',
    account: 'platform_admin',
    operations: [
      '打开客户/KYC 列表',
      '有数据时核对企业名称；无数据时查看空态与 seed 提示'
    ],
    passCriteria: '列表正常或空态含演示灌数提示',
    defaultNote: '无数据时执行 apply-seed-profile.ps1 -Profile demo'
  },
  {
    id: 'M5',
    order: 5,
    module: '贸易代采',
    entryPath: '/agency-purchase/applications',
    entryLabel: '代采申请列表',
    account: 'platform_admin',
    operations: [
      '打开代采列表，新建或打开草稿',
      '填写必填项后提交',
      '列表/详情状态变为 SUBMITTED'
    ],
    passCriteria: '代采单状态 SUBMITTED（或列表/空态正常）'
  },
  {
    id: 'M6',
    order: 6,
    module: '仓储货权',
    entryPath: '/warehouse/inventories',
    entryLabel: '库存货权',
    account: 'platform_admin',
    operations: [
      '打开库存货权列表',
      '进入详情核对冻结数量、可用数量字段'
    ],
    passCriteria: '货权列表/详情可打开，冻结数量可核对'
  },
  {
    id: 'M7',
    order: 7,
    module: '融资 / 放款',
    entryPath: '/finance/applications',
    entryLabel: '融资管理',
    account: 'funding_user / Fund@123',
    accountPassword: 'Fund@123',
    operations: [
      '使用 funding_user 登录（或 platform_admin 审批后切换）',
      '打开融资列表，对 TO_DISBURSE 单执行放款前校验与放款',
      '确认状态 DISBURSED'
    ],
    passCriteria: '融资单状态 DISBURSED'
  },
  {
    id: 'M8',
    order: 8,
    module: '清分',
    entryPath: '/accounts/clearing',
    entryLabel: '清分中心',
    account: 'platform_admin',
    operations: [
      '选择已放款融资单',
      '执行试算 → 确认 → 执行清分',
      '清分记录状态 EXECUTED'
    ],
    passCriteria: '清分试算与执行成功，状态 EXECUTED'
  },
  {
    id: 'M9',
    order: 9,
    module: '签章',
    entryPath: '/documents/center',
    entryLabel: '签章中心',
    account: 'platform_admin',
    operations: [
      '登记单证或选择已有单证',
      '发起签署（Mock/HTTP Adapter）',
      '跟踪签署状态；异常时在补偿池复核'
    ],
    passCriteria: 'Mock/HTTP 闭环完成，或补偿池可查签章回调任务'
  },
  {
    id: 'M10',
    order: 10,
    module: '补偿池',
    entryPath: '/saga/ops?tab=compensation',
    entryLabel: '补偿池',
    account: 'platform_admin',
    operations: [
      '打开补偿池 Tab',
      '按 businessType=CONTRACT_SIGN_CALLBACK 筛选（如有）',
      '确认列表或空态正常'
    ],
    passCriteria: '补偿任务列表/空态正常，无页面报错'
  },
  {
    id: 'M11',
    order: 11,
    module: 'BI 看板',
    entryPath: '/bi/dashboard',
    entryLabel: '经营看板',
    account: 'platform_admin',
    operations: [
      '打开经营看板',
      '等待 KPI 卡片与图表加载',
      '确认无 403，指标有值或合理空态'
    ],
    passCriteria: '看板加载无 403，KPI/图表有值或空态'
  },
  {
    id: 'M12',
    order: 12,
    module: '权限',
    entryPath: '/accounts/clearing',
    entryLabel: '清分中心（member）',
    account: 'member_user / Member@123',
    accountPassword: 'Member@123',
    operations: [
      '退出后以 member_user 登录',
      '打开清分中心',
      '确认「执行清分」按钮隐藏或接口返回 403'
    ],
    passCriteria: 'member 无法执行清分（按钮隐藏或 403）'
  }
]

export const UAT_STATUS_LABELS: Record<UatStepStatus, string> = {
  pending: '待验',
  pass: '通过',
  fail: '未通过',
  skip: '跳过'
}

export const UAT_STATUS_TAG: Record<UatStepStatus, 'info' | 'success' | 'danger' | 'warning'> = {
  pending: 'info',
  pass: 'success',
  fail: 'danger',
  skip: 'warning'
}
