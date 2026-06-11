<template>
  <div class="contract-sign-config">
    <div class="page-header">
      <div>
        <h2>合同签章配置</h2>
        <p class="subtitle">查看签章供应商注册表、回调鉴权模式与 EA-041 硬化路线图</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="config && config.callback_verification_mode === 'TOKEN'"
      type="warning"
      :closable="false"
      show-icon
      title="当前回调鉴权为固定 Token 模式。生产环境建议切换为 TIMESTAMP_NONCE_SIGNATURE。"
      style="margin-bottom: 16px"
    />

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card v-loading="loading" shadow="never">
          <template #header>全局配置（只读 · application.yml）</template>
          <el-descriptions v-if="config" :column="1" border size="small">
            <el-descriptions-item label="默认供应商">{{ config.default_provider }}</el-descriptions-item>
            <el-descriptions-item label="最大重试次数">{{ config.max_retry_count }}</el-descriptions-item>
            <el-descriptions-item label="回调鉴权模式">
              <el-tag size="small">{{ config.callback_verification_mode }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="签名时间窗（秒）">{{ config.callback_signature_window_seconds }}</el-descriptions-item>
            <el-descriptions-item label="回调 Token（脱敏）">{{ config.callback_token_masked }}</el-descriptions-item>
            <el-descriptions-item label="回调路径">{{ config.callback_path }}</el-descriptions-item>
            <el-descriptions-item label="当前请求头">{{ config.callback_headers.join(' · ') }}</el-descriptions-item>
            <el-descriptions-item label="EA-041 计划请求头">{{ config.planned_callback_headers.join(' · ') }}</el-descriptions-item>
            <el-descriptions-item label="补偿池">
              <el-tag :type="config.compensation_pool_enabled ? 'success' : 'info'" size="small">
                {{ config.compensation_pool_enabled ? '已启用' : '未启用' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card v-loading="loading" shadow="never">
          <template #header>已注册供应商</template>
          <el-table :data="providers" stripe size="small">
            <el-table-column prop="provider_code" label="代码" width="100" />
            <el-table-column prop="display_name" label="名称" min-width="140" />
            <el-table-column label="状态查询" width="90">
              <template #default="{ row }">{{ row.supports_status_query ? '支持' : '—' }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="200" show-overflow-tooltip />
          </el-table>
          <p class="hint">新增真实供应商：实现 `ContractSignProvider` 并注册 Spring Bean，详见 `docs/ESIGN_PROVIDER_TEMPLATE.md`。</p>
        </el-card>
      </el-col>
    </el-row>

    <el-card v-if="config?.provider_connections?.length" shadow="never" style="margin-top: 16px">
      <template #header>HTTP 供应商连接（只读 · 环境变量 / application.yml）</template>
      <el-table :data="config.provider_connections" stripe size="small">
        <el-table-column prop="provider_code" label="代码" width="120" />
        <el-table-column prop="outbound_auth_mode" label="出站鉴权" width="110" />
        <el-table-column prop="platform_trace_header" label="Trace 头" width="120" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="已配置" width="90">
          <template #default="{ row }">
            <el-tag :type="row.configured ? 'success' : 'warning'" size="small">{{ row.configured ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="base_url" label="Base URL" min-width="200" show-overflow-tooltip />
        <el-table-column prop="app_id" label="App ID" width="140" show-overflow-tooltip />
        <el-table-column prop="app_secret_masked" label="App Secret（脱敏）" width="160" />
      </el-table>
      <p class="hint">字段映射与 RSA/SM2 配置见 <code>docs/ESIGN_VENDOR_FIELD_MAP.md</code>；联调步骤见 <code>docs/EA-045_真实签章供应商联调Checklist.md</code>。</p>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>回调接入说明</template>
      <el-steps :active="2" align-center finish-status="success">
        <el-step title="发起签署" description="POST /documents/center/{id}/sign" />
        <el-step title="供应商处理" description="external_sign_ref 异步签署" />
        <el-step title="回调通知" description="POST /integrations/contracts/sign-callback" />
        <el-step title="单证闭环" description="sign_status → SIGNED" />
      </el-steps>
      <ul class="roadmap">
        <li><strong>EA-041 验签升级：</strong>HMAC-SHA256(timestamp + nonce + body)，防重放窗口可配置。</li>
        <li><strong>EA-041 补偿池：</strong>验签通过但业务处理失败的回调进入 Saga 补偿池，运营可在监控台复核。</li>
      </ul>
      <div class="ops-link">
        <el-button type="primary" link @click="goSagaSignReview">前往 Saga 运营中心 · 签章回调复核 →</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getContractSignConfig,
  listContractSignProviders,
  type ContractSignConfig,
  type ContractSignProviderInfo
} from '../api/contractSign'

const router = useRouter()
const loading = ref(false)
const config = ref<ContractSignConfig | null>(null)
const providers = ref<ContractSignProviderInfo[]>([])

async function load() {
  loading.value = true
  try {
    const [cfg, prov] = await Promise.all([getContractSignConfig(), listContractSignProviders()])
    config.value = cfg.data.data
    providers.value = prov.data.data
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载配置失败')
  } finally {
    loading.value = false
  }
}

function goSagaSignReview() {
  router.push({
    path: '/saga/ops',
    query: {
      tab: 'compensation',
      compensation_type: 'CONTRACT_SIGN_CALLBACK_REVIEW',
      business_type: 'CONTRACT_SIGN_CALLBACK',
      compensation_status: 'MANUAL_REQUIRED'
    }
  })
}

onMounted(load)
</script>

<style scoped>
.contract-sign-config { padding: 0 4px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.subtitle { color: #666; margin: 4px 0 0; font-size: 13px; }
.hint { margin-top: 12px; color: #888; font-size: 12px; }
.roadmap { margin: 16px 0 0; padding-left: 20px; color: #555; line-height: 1.8; }
.ops-link { margin-top: 12px; }
</style>
