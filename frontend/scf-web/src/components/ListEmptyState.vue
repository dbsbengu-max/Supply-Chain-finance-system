<template>
  <el-empty :description="description">
    <template v-if="showSeedHint" #default>
      <p class="hint">{{ seedHint }}</p>
      <el-button v-if="actionLabel" type="primary" link @click="$emit('action')">{{ actionLabel }}</el-button>
    </template>
  </el-empty>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    description?: string
    showSeedHint?: boolean
    seedHint?: string
    actionLabel?: string
  }>(),
  {
    description: '暂无数据',
    showSeedHint: true,
    seedHint: '演示环境请执行 deploy/pilot/scripts/apply-seed-profile.ps1 -Profile demo 灌入 Mock 业务数据。',
    actionLabel: ''
  }
)

defineEmits<{ action: [] }>()
</script>

<style scoped>
.hint {
  max-width: 420px;
  margin: 0 auto 8px;
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}
</style>
