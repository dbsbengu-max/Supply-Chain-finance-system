<template>
  <div class="launch-hub">
    <div class="page-header">
      <div>
        <h2>功能上线收口</h2>
        <p class="subtitle">EA-049：系统配置、集成供应商、补偿池、签章与 BI 的统一入口；Mock/HTTP Adapter 可演示，真实 vendor 联调后置。</p>
      </div>
      <el-button type="primary" @click="router.push('/pilot/closure')">试点闭环向导</el-button>
      <el-button @click="router.push('/uat/acceptance')">UAT 验收 M1–M12</el-button>
    </div>

    <el-alert
      type="success"
      show-icon
      :closable="false"
      title="当前阶段目标"
      description="可用 · 可测 · 可演示 · 可交付。真实供应商 Sandbox Go/No-Go（EA-048）已登记为后置待办，不阻塞本页功能。"
      class="banner"
    />

    <el-row :gutter="16">
      <el-col v-for="card in visibleCards" :key="card.key" :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="hub-card" @click="go(card.path)">
          <div class="card-icon">{{ card.icon }}</div>
          <h3>{{ card.title }}</h3>
          <p>{{ card.desc }}</p>
          <el-tag v-if="card.tag" size="small" :type="card.tagType">{{ card.tag }}</el-tag>
          <el-button type="primary" link class="enter">进入 →</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="demo-card">
      <template #header>演示 Mock Data</template>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="灌数脚本">deploy/pilot/scripts/apply-seed-profile.ps1 -Profile demo</el-descriptions-item>
        <el-descriptions-item label="演示账号">platform_admin / Admin@123（全链路）；funding_user / Fund@123（放款）</el-descriptions-item>
        <el-descriptions-item label="签章 quasi-sandbox">deploy/pilot/scripts/run-ea046-local-sandbox.ps1</el-descriptions-item>
        <el-descriptions-item label="UAT 最小集">侧边栏「UAT 验收」或 /uat/acceptance</el-descriptions-item>
        <el-descriptions-item label="UAT 手册">deploy/pilot/uat/UAT_OPERATION_MANUAL.md</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { usePermission } from '../composables/usePermission'

const router = useRouter()
const { hasPermission } = usePermission()

interface HubCard {
  key: string
  title: string
  desc: string
  path: string
  icon: string
  tag?: string
  tagType?: 'success' | 'info' | 'warning'
  permission?: string
}

const cards: HubCard[] = [
  {
    key: 'config',
    title: '系统配置',
    desc: '项目、运营商与试点范围；后续扩展系统级参数',
    path: '/projects',
    icon: '⚙️',
    tag: '项目 CRUD'
  },
  {
    key: 'vendor',
    title: '供应商配置',
    desc: '签章 HTTP 连接、回调验签与 rollout 只读视图',
    path: '/integrations/contracts/sign-config',
    icon: '🔌',
    tag: 'Mock/HTTP',
    permission: 'CONTRACT_SIGN_CONFIG_VIEW'
  },
  {
    key: 'compensation',
    title: '补偿池',
    desc: 'Saga 补偿任务、签章回调复核、人工重试与查单',
    path: '/saga/ops?tab=compensation',
    icon: '🔄',
    permission: 'SAGA_OPS_VIEW'
  },
  {
    key: 'sign',
    title: '签章中心',
    desc: '单证上传、OCR 复核、发起签署与状态跟踪',
    path: '/documents/center',
    icon: '✍️',
    permission: 'DOCUMENT_VIEW'
  },
  {
    key: 'bi',
    title: 'BI 看板',
    desc: '放款、在贷、逾期与试点指标核对',
    path: '/bi/dashboard?from=launch',
    icon: '📊',
    permission: 'BI_VIEW'
  },
  {
    key: 'uat',
    title: 'UAT 验收',
    desc: 'M1–M12 人工验收步骤、状态标记与签字包导出',
    path: '/uat/acceptance',
    icon: '✅',
    tag: 'EA-050'
  },
  {
    key: 'pilot',
    title: '试点闭环',
    desc: '客户 → 代采 → 仓储 → 融资 → 清分 → 签章 → BI',
    path: '/pilot/closure',
    icon: '🔗'
  }
]

const visibleCards = computed(() =>
  cards.filter((c) => !c.permission || hasPermission(c.permission))
)

function go(path: string) {
  router.push(path)
}
</script>

<style scoped>
.launch-hub {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}
.subtitle {
  margin: 4px 0 0;
  color: #666;
  font-size: 13px;
  max-width: 720px;
}
.banner {
  margin-bottom: 0;
}
.hub-card {
  cursor: pointer;
  min-height: 180px;
  margin-bottom: 16px;
  transition: border-color 0.2s;
}
.hub-card:hover {
  border-color: #409eff;
}
.card-icon {
  font-size: 28px;
  margin-bottom: 8px;
}
.hub-card h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
.hub-card p {
  margin: 0 0 8px;
  color: #666;
  font-size: 13px;
  min-height: 40px;
}
.enter {
  margin-top: 4px;
}
.demo-card {
  margin-top: 8px;
}
</style>
