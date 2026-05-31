<template>
  <el-timeline class="closure-timeline">
    <el-timeline-item
      v-for="node in nodes"
      :key="node.key"
      :type="node.type"
      :timestamp="node.timestamp ? formatTime(node.timestamp) : undefined"
      placement="top"
    >
      <div class="node-head">
        <span class="node-title">{{ node.title }}</span>
        <el-tag v-if="node.type !== 'primary'" :type="tagType(node.type)" size="small" effect="plain">
          {{ typeHint(node.type) }}
        </el-tag>
      </div>
      <p class="node-desc">{{ node.description }}</p>
      <el-button v-if="node.route && node.routeLabel" link type="primary" @click="router.push(node.route!)">
        {{ node.routeLabel }} →
      </el-button>
    </el-timeline-item>
  </el-timeline>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import type { ClosureTimelineNode, ClosureTimelineType } from '../utils/pilotClosureTimeline'

defineProps<{
  nodes: ClosureTimelineNode[]
}>()

const router = useRouter()

function formatTime(value: string) {
  return value.replace('T', ' ').slice(0, 19)
}

function tagType(type: ClosureTimelineType): 'success' | 'warning' | 'danger' | 'info' {
  if (type === 'success') return 'success'
  if (type === 'warning') return 'warning'
  if (type === 'danger') return 'danger'
  return 'info'
}

function typeHint(type: ClosureTimelineType) {
  if (type === 'success') return '已完成'
  if (type === 'warning') return '进行中'
  if (type === 'danger') return '异常'
  return '待开始'
}
</script>

<style scoped>
.closure-timeline {
  margin: 8px 0 0;
  padding-left: 4px;
}
.node-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.node-title {
  font-weight: 600;
  font-size: 14px;
}
.node-desc {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
