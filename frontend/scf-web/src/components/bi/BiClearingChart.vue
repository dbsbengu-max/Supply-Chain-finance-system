<template>
  <div ref="root" class="bi-chart" :style="{ height }" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { disposeChart, initChart, resizeChart, type ECharts } from '../../utils/biChart'

const props = withDefaults(
  defineProps<{
    principal: string
    interest: string
    fee: string
    currency?: string
    height?: string
  }>(),
  { currency: 'CNY', height: '280px' }
)

const root = ref<HTMLElement | null>(null)
let chart: ECharts | null = null

function num(v: string) {
  const n = Number(v)
  return Number.isNaN(n) ? 0 : n
}

function render() {
  if (!chart) return
  const labels = ['本金', '利息', '费用/罚息']
  const values = [num(props.principal), num(props.interest), num(props.fee)]

  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 48, right: 24, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', name: props.currency },
    series: [
      {
        type: 'bar',
        data: values,
        barMaxWidth: 48,
        itemStyle: {
          color: (params: { dataIndex: number }) =>
            ['#409EFF', '#E6A23C', '#F56C6C'][params.dataIndex] ?? '#909399'
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

watch(
  () => [props.principal, props.interest, props.fee, props.currency],
  render
)

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  disposeChart(chart)
  chart = null
})
</script>

<style scoped>
.bi-chart { width: 100%; min-height: 220px; }
</style>
