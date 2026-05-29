<template>
  <el-result icon="warning" title="无权访问" :sub-title="message">
    <template #extra>
      <el-button type="primary" @click="router.push('/')">返回工作台</el-button>
    </template>
  </el-result>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const message = computed(() => {
  const perm = (route.query.perm as string) || (route.meta.permission as string | undefined)
  if (perm) return `当前身份缺少权限：${perm}`
  return '您没有访问该页面的权限'
})
</script>
