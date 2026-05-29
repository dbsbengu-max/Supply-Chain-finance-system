<template>
  <div ref="root" class="bi-chart" :style="{ height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { BiTrendPoint } from '../../api/bi'
import { disposeChart, initChart, resizeChart, type ECharts } from '../../utils/biChart'

const props = withDefaults(
  defineProps<{
    points: BiTrendPoint[]
    height?: string
  }>(),
  { height: '320px' }
)

const root = ref<HTMLElement | null>(null)
let chart: ECharts | null = null

function render() {
  if (!chart) return
  const periods = props.points.map((p) => p.period)
  const counts = props.points.map((p) => p.order_count)
  const amounts = props.points.map((p) => Number(p.amount_total) || 0)

  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单数', '金额'] },
    grid: { left: 48, right: 48, top: 40, bottom: 32 },
    xAxis: { type: 'category', data: periods },
    yAxis: [
      { type: 'value', name: '笔数' },
      { type: 'value', name: '金额', splitLine: { show: false } }
    ],
    series: [
      {
        name: '订单数',
        type: 'bar',
        data: counts,
        itemStyle: { color: '#409EFF' },
        barMaxWidth: 36
      },
      {
        name: '金额',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: amounts,
        itemStyle: { color: '#67C23A' }
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

watch(() => props.points, render, { deep: true })

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeChart(chart)
  chart = null
})
</script>

<style scoped>
.bi-chart { width: 100%; min-height: 240px; }
</style>
