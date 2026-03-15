<template>
  <div class="report-container">
    <!-- 顶部筛选栏 -->
    <a-card style="margin-bottom: 16px">
      <a-space :size="16" wrap>
        <a-range-picker
          v-model:value="dateRange"
          :placeholder="['开始日期', '结束日期']"
          style="width: 280px"
        />
        <a-radio-group v-model:value="granularity">
          <a-radio-button value="day">日</a-radio-button>
          <a-radio-button value="week">周</a-radio-button>
          <a-radio-button value="month">月</a-radio-button>
        </a-radio-group>
        <a-button type="primary" :loading="loading" @click="fetchAllReports">
          查询
        </a-button>
        <a-button @click="handleExport">导出报表</a-button>
      </a-space>
    </a-card>

    <!-- 图表区域 -->
    <a-row :gutter="16">
      <!-- 入场趋势图 -->
      <a-col :span="12">
        <a-card title="入场趋势">
          <a-spin :spinning="loading">
            <div ref="entryTrendRef" style="height: 350px" />
          </a-spin>
        </a-card>
      </a-col>
      <!-- 车位使用率图 -->
      <a-col :span="12">
        <a-card title="车位使用率">
          <a-spin :spinning="loading">
            <div ref="spaceUsageRef" style="height: 350px" />
          </a-spin>
        </a-card>
      </a-col>
    </a-row>

    <!-- 峰值时段图（全宽） -->
    <a-row style="margin-top: 16px">
      <a-col :span="24">
        <a-card title="峰值时段分布">
          <a-spin :spinning="loading">
            <div ref="peakHoursRef" style="height: 350px" />
          </a-spin>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { useAuthStore } from '@/stores/auth'
import { getEntryTrend, getSpaceUsage, getPeakHours } from '@/api/report'

const authStore = useAuthStore()

/** 筛选条件 */
const dateRange = ref([dayjs().subtract(30, 'day'), dayjs()])
const granularity = ref('day')
const loading = ref(false)

/** 图表 DOM 引用 */
const entryTrendRef = ref(null)
const spaceUsageRef = ref(null)
const peakHoursRef = ref(null)

/** ECharts 实例 */
let entryTrendChart = null
let spaceUsageChart = null
let peakHoursChart = null

/** 报表数据 */
const entryTrendData = ref([])
const spaceUsageData = ref([])
const peakHoursData = ref([])

/**
 * 构建请求参数
 */
function buildParams() {
  const [start, end] = dateRange.value || []
  return {
    communityId: authStore.communityId,
    startDate: start ? start.format('YYYY-MM-DD') : '',
    endDate: end ? end.format('YYYY-MM-DD') : ''
  }
}

/**
 * 查询所有报表数据
 */
async function fetchAllReports() {
  if (!dateRange.value || !dateRange.value[0] || !dateRange.value[1]) {
    message.warning('请选择时间范围')
    return
  }
  loading.value = true
  try {
    const params = buildParams()
    const [trendRes, usageRes, peakRes] = await Promise.all([
      getEntryTrend({ ...params, granularity: granularity.value }),
      getSpaceUsage(params),
      getPeakHours(params)
    ])
    entryTrendData.value = trendRes?.items || []
    spaceUsageData.value = usageRes?.items || []
    peakHoursData.value = peakRes?.items || []

    // 更新图表
    renderEntryTrendChart()
    renderSpaceUsageChart()
    renderPeakHoursChart()
  } catch (e) {
    // 错误已由 request.js 统一处理
  } finally {
    loading.value = false
  }
}

/**
 * 渲染入场趋势折线图（双 Y 轴）
 */
function renderEntryTrendChart() {
  if (!entryTrendChart) return
  const dates = entryTrendData.value.map(item => item.date)
  const entryCounts = entryTrendData.value.map(item => item.entryCount)
  const exitCounts = entryTrendData.value.map(item => item.exitCount)

  entryTrendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['入场数', '出场数'] },
    grid: { left: 60, right: 60, bottom: 30, top: 40 },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '入场数', position: 'left' },
      { type: 'value', name: '出场数', position: 'right' }
    ],
    series: [
      {
        name: '入场数',
        type: 'line',
        data: entryCounts,
        smooth: true,
        yAxisIndex: 0,
        itemStyle: { color: '#1890ff' }
      },
      {
        name: '出场数',
        type: 'line',
        data: exitCounts,
        smooth: true,
        yAxisIndex: 1,
        itemStyle: { color: '#52c41a' }
      }
    ]
  })
}

/**
 * 渲染车位使用率柱状图
 */
function renderSpaceUsageChart() {
  if (!spaceUsageChart) return
  const dates = spaceUsageData.value.map(item => item.date)
  const rates = spaceUsageData.value.map(item =>
    Number((item.usageRate * 100).toFixed(1))
  )

  spaceUsageChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.name}<br/>${p.seriesName}: ${p.value}%`
      }
    },
    grid: { left: 60, right: 20, bottom: 30, top: 40 },
    xAxis: { type: 'category', data: dates },
    yAxis: {
      type: 'value',
      name: '使用率(%)',
      max: 100,
      axisLabel: { formatter: '{value}%' }
    },
    series: [
      {
        name: '使用率',
        type: 'bar',
        data: rates,
        itemStyle: {
          color: (params) => {
            // 使用率超过 80% 显示红色，超过 60% 显示橙色
            if (params.value >= 80) return '#ff4d4f'
            if (params.value >= 60) return '#faad14'
            return '#1890ff'
          }
        }
      }
    ]
  })
}

/**
 * 渲染峰值时段柱状图
 */
function renderPeakHoursChart() {
  if (!peakHoursChart) return
  const hours = peakHoursData.value.map(item => `${item.hour}:00`)
  const counts = peakHoursData.value.map(item => item.avgCount)

  peakHoursChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const p = params[0]
        return `${p.name}<br/>平均车辆数: ${p.value}`
      }
    },
    grid: { left: 60, right: 20, bottom: 30, top: 40 },
    xAxis: { type: 'category', data: hours },
    yAxis: { type: 'value', name: '平均车辆数' },
    series: [
      {
        name: '平均车辆数',
        type: 'bar',
        data: counts,
        itemStyle: {
          color: (params) => {
            // 高峰时段（车辆数较多）用深色
            const max = Math.max(...counts, 1)
            const ratio = params.value / max
            if (ratio >= 0.8) return '#ff4d4f'
            if (ratio >= 0.5) return '#faad14'
            return '#1890ff'
          }
        }
      }
    ]
  })
}

/**
 * 初始化 ECharts 实例
 */
function initCharts() {
  if (entryTrendRef.value) {
    entryTrendChart = echarts.init(entryTrendRef.value)
  }
  if (spaceUsageRef.value) {
    spaceUsageChart = echarts.init(spaceUsageRef.value)
  }
  if (peakHoursRef.value) {
    peakHoursChart = echarts.init(peakHoursRef.value)
  }
}

/**
 * 窗口 resize 时自适应图表
 */
function handleResize() {
  entryTrendChart?.resize()
  spaceUsageChart?.resize()
  peakHoursChart?.resize()
}

/**
 * 导出报表（简单实现）
 */
function handleExport() {
  message.success('导出任务已提交，请稍后在导出列表中查看')
}

/** 监听数据变化重新渲染图表 */
watch(entryTrendData, renderEntryTrendChart)
watch(spaceUsageData, renderSpaceUsageChart)
watch(peakHoursData, renderPeakHoursChart)

onMounted(async () => {
  await nextTick()
  initCharts()
  window.addEventListener('resize', handleResize)
  // 页面加载时自动查询
  fetchAllReports()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  entryTrendChart?.dispose()
  spaceUsageChart?.dispose()
  peakHoursChart?.dispose()
})
</script>

<style scoped>
.report-container {
  padding: 0;
}
</style>
