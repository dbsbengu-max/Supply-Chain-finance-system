<template>
  <div>
    <h2>工作台</h2>

    <el-card v-if="shortcuts.length" class="shortcuts-card">
      <template #header>快捷入口</template>
      <div class="shortcut-grid">
        <el-button
          v-for="item in shortcuts"
          :key="item.path"
          class="shortcut-btn"
          @click="router.push(item.path)"
        >
          <span class="shortcut-title">{{ item.title }}</span>
          <span class="shortcut-desc">{{ item.desc }}</span>
        </el-button>
      </div>
    </el-card>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header-row">
              <span>统一待办 / 通知</span>
              <el-button v-if="canViewInbox" link type="primary" @click="router.push('/inbox')">进入中心</el-button>
            </div>
          </template>
          <el-empty v-if="!inboxEvents.length" description="暂无待办或通知" />
          <el-table v-else :data="inboxEvents" size="small" @row-click="onInboxRowClick">
            <el-table-column label="来源" width="88">
              <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="72">
              <template #default="{ row }">
                <el-tag :type="row.read ? 'info' : 'danger'" size="small">{{ row.read ? '已读' : '未读' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <p v-if="inboxSummary" class="inbox-hint">
            共 {{ inboxSummary.total }} 条，未读 {{ inboxSummary.unread_count }} 条
          </p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <template #header>当前身份</template>
          <p>运营商：{{ auth.operatorId || '-' }}</p>
          <p>项目：{{ auth.projectId || '-' }}</p>
          <el-select v-model="selectedIdentity" placeholder="切换身份" style="width: 100%; margin-top: 12px" @change="onSwitch">
            <el-option
              v-for="item in auth.identities"
              :key="item.identityId"
              :label="`${item.roleName} (${item.enterpriseId})`"
              :value="item.identityId"
            />
          </el-select>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <template #header>业务闭环</template>
          <ul class="progress-list">
            <li>✅ 试点闭环向导（EA-029）</li>
            <li>✅ Saga 监控 + 人工原因审计</li>
            <li>✅ 银行流水 / 清分 / 规则</li>
            <li>✅ 风险预警 / 经营看板</li>
            <li>✅ EA-049 功能上线收口 / 补偿池 / 签章</li>
            <li>✅ EA-050 UAT 验收入口（M1–M12）</li>
            <li>⏳ UAT 最小集签字（Mock 环境）</li>
            <li>⏸️ 真实 vendor / 生产灰度（EA-048 后置）</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getInboxFeed, INBOX_SOURCE_LABELS, type InboxEvent, type InboxSummary } from '../api/inbox'
import { useAuthStore } from '../stores/auth'
import { usePermission } from '../composables/usePermission'

const router = useRouter()
const auth = useAuthStore()
const { hasPermission } = usePermission()
const inboxEvents = ref<InboxEvent[]>([])
const inboxSummary = ref<InboxSummary | null>(null)
const selectedIdentity = ref<string>('')

const canViewInbox = computed(() => hasPermission('INBOX_VIEW'))

const shortcuts = computed(() => {
  const items: Array<{ title: string; desc: string; path: string }> = [
    { title: '功能上线', desc: '配置、补偿池、签章、BI 统一入口', path: '/launch/hub' },
    { title: 'UAT 验收', desc: 'M1–M12 人工验收与签字包', path: '/uat/acceptance' },
    { title: '试点闭环', desc: '端到端主链路上线向导', path: '/pilot/closure' }
  ]
  if (hasPermission('SAGA_OPS_VIEW')) {
    items.push({ title: '补偿池', desc: 'Saga 补偿与签章回调复核', path: '/saga/ops?tab=compensation' })
  }
  if (hasPermission('DOCUMENT_VIEW')) {
    items.push({ title: '签章中心', desc: '单证上传与发起签署', path: '/documents/center' })
  }
  if (hasPermission('INBOX_VIEW')) {
    items.push({ title: '消息待办', desc: '统一事件通知流', path: '/inbox' })
  }
  if (hasPermission('ACCOUNT_FLOW_VIEW')) {
    items.push({ title: '银行流水', desc: '导入与匹配融资', path: '/accounts/bank-flows' })
  }
  if (hasPermission('CLEARING_VIEW')) {
    items.push({ title: '清分中心', desc: '试算与执行清分', path: '/accounts/clearing' })
    items.push({ title: '清分规则', desc: '维护清分优先级', path: '/accounts/clearing-rules' })
  }
  if (hasPermission('FINANCE_VIEW')) {
    items.push({ title: '融资管理', desc: '查看融资单状态', path: '/finance/applications' })
  }
  if (hasPermission('BI_VIEW')) {
    items.push({ title: '经营看板', desc: '只读经营指标聚合', path: '/bi/dashboard' })
  }
  if (hasPermission('RISK_ALERT_VIEW')) {
    items.push({ title: '风险预警', desc: '告警列表与处理跟踪', path: '/risk/alerts' })
  }
  return items
})

function sourceLabel(source: string) {
  return INBOX_SOURCE_LABELS[source] ?? source
}

function onInboxRowClick(row: InboxEvent) {
  if (row.action_route) {
    router.push(row.action_route)
  } else if (canViewInbox.value) {
    router.push('/inbox')
  }
}

async function loadInboxPreview() {
  if (!canViewInbox.value) {
    inboxEvents.value = []
    inboxSummary.value = null
    return
  }
  try {
    const res = await getInboxFeed({ limit: 8 })
    if (res.success) {
      inboxSummary.value = res.data?.summary ?? null
      inboxEvents.value = res.data?.events ?? []
    }
  } catch {
    inboxEvents.value = []
    inboxSummary.value = null
  }
}

async function onSwitch(identityId: string) {
  try {
    await auth.switchIdentity(identityId)
    ElMessage.success('身份已切换')
    await loadInboxPreview()
  } catch (e: any) {
    ElMessage.error(e.message || '切换失败')
  }
}

onMounted(loadInboxPreview)
</script>

<style scoped>
.shortcuts-card { margin-bottom: 0; }
.shortcut-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.shortcut-btn {
  height: auto;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 160px;
}
.shortcut-title { font-weight: 600; font-size: 14px; }
.shortcut-desc { font-size: 12px; color: #909399; margin-top: 4px; }
.progress-list { padding-left: 18px; line-height: 1.8; }
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.inbox-hint {
  margin: 12px 0 0;
  font-size: 13px;
  color: #909399;
}
</style>
