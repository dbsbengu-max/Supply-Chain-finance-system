<template>
  <div class="uat-page">
    <div class="page-header">
      <div>
        <h2>UAT 验收入口</h2>
        <p class="subtitle">
          EA-050：基于 EA-049 最小集 M1–M12，Mock/试点环境人工验收与签字包；不接真实 vendor / 银行 / 生产灰度。
        </p>
      </div>
      <div class="header-actions">
        <el-button @click="router.push('/launch/hub')">功能上线</el-button>
        <el-button @click="onExport">导出签字包</el-button>
        <el-button type="warning" plain @click="onReset">重置进度</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-statistic title="已通过" :value="summary.pass">
          <template #suffix>/ {{ summary.total }}</template>
        </el-statistic>
      </el-col>
      <el-col :span="6">
        <el-statistic title="待验" :value="summary.pending" />
      </el-col>
      <el-col :span="6">
        <el-statistic title="未通过" :value="summary.fail" />
      </el-col>
      <el-col :span="6">
        <el-tag v-if="allPassed" type="success" size="large">可签字交付</el-tag>
        <el-tag v-else type="info" size="large">验收进行中</el-tag>
      </el-col>
    </el-row>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="seed-banner"
      title="演示灌数"
      description="deploy/pilot/scripts/apply-seed-profile.ps1 -Profile demo · 自动化门禁：后端 171/171 · smoke 9/9 · health UP"
    />

    <el-table :data="steps" stripe class="steps-table" row-key="id">
      <el-table-column prop="id" label="#" width="56" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column label="入口" min-width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="goEntry(row.entryPath)">{{ row.entryLabel }}</el-button>
          <div class="path-hint">{{ row.entryPath }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="account" label="账号" width="200" />
      <el-table-column label="操作步骤" min-width="220">
        <template #default="{ row }">
          <ol class="op-list">
            <li v-for="(op, i) in row.operations" :key="i">{{ op }}</li>
          </ol>
        </template>
      </el-table-column>
      <el-table-column prop="passCriteria" label="通过标准" min-width="180" />
      <el-table-column label="状态" width="120" fixed="right">
        <template #default="{ row }">
          <el-select
            :model-value="recordFor(row.id).status"
            size="small"
            @update:model-value="(v: UatStepStatus) => setStatus(row.id, v)"
          >
            <el-option
              v-for="(label, key) in UAT_STATUS_LABELS"
              :key="key"
              :label="label"
              :value="key"
            />
          </el-select>
          <el-tag
            class="status-tag"
            size="small"
            :type="UAT_STATUS_TAG[recordFor(row.id).status]"
          >
            {{ UAT_STATUS_LABELS[recordFor(row.id).status] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" width="200" fixed="right">
        <template #default="{ row }">
          <el-input
            :model-value="recordFor(row.id).note"
            type="textarea"
            :rows="2"
            :placeholder="row.defaultNote || '验收备注'"
            @update:model-value="(v: string) => setNote(row.id, v)"
          />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUatAcceptance } from '../composables/useUatAcceptance'
import {
  UAT_STATUS_LABELS,
  UAT_STATUS_TAG,
  type UatStepStatus
} from '../constants/uatAcceptanceSteps'

const router = useRouter()
const { steps, recordFor, setStatus, setNote, resetAll, summary, allPassed, exportMarkdown } =
  useUatAcceptance()

function goEntry(path: string) {
  router.push(path)
}

async function onReset() {
  await ElMessageBox.confirm('将清空本地 M1–M12 验收进度与备注，是否继续？', '重置', { type: 'warning' })
  resetAll()
  ElMessage.success('已重置')
}

function onExport() {
  const md = exportMarkdown()
  const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `EA-050-UAT-signoff-${new Date().toISOString().slice(0, 10)}.md`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('签字包已导出')
}
</script>

<style scoped>
.uat-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
.subtitle {
  margin: 4px 0 0;
  color: #666;
  font-size: 13px;
  max-width: 640px;
}
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.summary-row {
  margin-top: 0;
}
.seed-banner {
  margin: 0;
}
.steps-table {
  width: 100%;
}
.path-hint {
  font-size: 11px;
  color: #909399;
}
.op-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.6;
}
.status-tag {
  margin-top: 6px;
}
</style>
