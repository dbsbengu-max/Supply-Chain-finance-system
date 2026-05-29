<template>
  <div ref="root" class="bi-chart" :style="{ height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { BiStatusBucket } from '../../api/bi'
import { disposeChart, initChart, resizeChart, type ECharts } from '../../utils/biChart'

const props = withDefaults(
  defineProps<{
    buckets: BiStatusBucket[]
    height?: string
  }>(),
  { height: '280px' }
)

const root = ref<HTMLElement | null>(null)
let chart: ECharts | null = null

function render() {
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 笔 ({d}%)' },
    legend: { type: 'scroll', bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['38%', '68%'],
        center: ['50%', '44%'],
        data: props.buckets.map((b) => ({
          name: b.status,
          value: b.count
        })),
        emphasis: {
          itemStyle: { shadowBlur: 8, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.15)' }
        }
      }
    ]
  })
}

function onResize() {
  resizeChart(chart)
}

onMounted(() => {
  if (root.value) {
    chart = initChart(root.value)
    render()
    window.addEventListener('resize', onResize)
  }
})

watch(() => props.buckets, render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeChart(chart)
  chart = null
})
</script>

<style scoped>
.bi-chart { width: 100%; min-height: 220px; }
</style>
