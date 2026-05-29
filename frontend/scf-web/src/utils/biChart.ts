import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts, EChartsCoreOption } from 'echarts/core'

echarts.use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent,
  CanvasRenderer
])

export function initChart(el: HTMLElement): ECharts {
  return echarts.init(el)
}

export function disposeChart(chart: ECharts | null | undefined) {
  chart?.dispose()
}

export function resizeChart(chart: ECharts | null | undefined) {
  chart?.resize()
}

export type { ECharts, EChartsCoreOption }
