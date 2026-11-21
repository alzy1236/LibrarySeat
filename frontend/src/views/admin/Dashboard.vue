<template>
  <div class="page dashboard-page">
    <div class="page-head">
      <div>
        <h1>数据概览</h1>
        <div class="sub">
          统计日期 {{ overview.today || '—' }} · 实时 · 最近更新 {{ lastUpdatedText }}
        </div>
      </div>
      <div class="head-tools">
        <el-radio-group v-model="days" size="default" @change="loadAll">
          <el-radio-button :value="7">7 天</el-radio-button>
          <el-radio-button :value="14">14 天</el-radio-button>
          <el-radio-button :value="30">30 天</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="board">
      <section class="card">
        <div class="card-head">
          <h3><span class="ico"><el-icon><TrendCharts /></el-icon></span>近 {{ days }} 天预约 / 签到 / 签退</h3>
          <span class="desc">单位：人 · 次</span>
        </div>
        <EChart :option="trendOption" height="268px" :loading="chartsLoading" />
      </section>

      <section class="card">
        <div class="card-head">
          <h3><span class="ico"><el-icon><Histogram /></el-icon></span>今日各时段签到人数</h3>
          <span class="desc">按签到小时统计</span>
        </div>
        <EChart :option="hourlyOption" height="268px" :loading="chartsLoading" />
      </section>

      <section class="card">
        <div class="card-head">
          <h3><span class="ico"><el-icon><PieChart /></el-icon></span>今日座位状态分布</h3>
          <span class="desc">共 {{ distributionTotal }} 条</span>
        </div>
        <EChart :option="distributionOption" height="252px" :loading="chartsLoading" />
      </section>

      <section class="card">
        <div class="card-head">
          <h3><span class="ico"><el-icon><OfficeBuilding /></el-icon></span>各楼层座位使用率</h3>
          <span class="desc">今日</span>
        </div>
        <EChart :option="roomUsageOption" height="196px" :loading="chartsLoading" />
        <div class="usage-list">
          <div v-for="item in roomUsageList" :key="item.roomName" class="usage-row">
            <span class="ur-name">{{ item.roomName }}</span>
            <span class="ur-num">{{ num(item.reserved) }} / {{ num(item.capacity) }}</span>
            <span class="ur-rate" :class="{ high: pct(item.reservedRate) >= 60 }">{{ pct(item.reservedRate) }}%</span>
          </div>
          <div v-if="!roomUsageList.length" class="empty-inline">暂无楼层数据</div>
        </div>
      </section>

      <section class="card wide">
        <div class="card-head">
          <h3><span class="ico"><el-icon><DataLine /></el-icon></span>各楼层近 {{ days }} 天签到趋势</h3>
          <span class="desc">单位：人</span>
        </div>
        <EChart :option="roomTrendOption" height="300px" :loading="chartsLoading" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
// 图表组件异步加载：ECharts 约 400KB，若同步引入会导致进入数据概览时整页等待下载；
// 异步后页面与数据先渲染，图表随后补上（骨架占位）
const EChart = defineAsyncComponent(() => import('@/components/EChart.vue'))
import { adminOverview, adminCharts, adminDistribution, adminRoomTrend } from '@/api'

const COLORS = {
  indigo: '#2563eb',
  violet: '#8b5cf6',
  emerald: '#10b981',
  amber: '#f59e0b',
  rose: '#dc2626',
  sky: '#0ea5e9',
  coral: '#fb7185',
  axis: '#9ca3af',
  split: '#f9fafb'
}

function num(v) {
  const n = Number(v)
  return Number.isFinite(n) ? n : 0
}
function pct(v) {
  return Math.round(num(v) * 10) / 10
}

const loading = ref(false)
const chartsLoading = ref(false)
const days = ref(7)
const lastUpdated = ref('')

const overview = reactive({ today: '' })
const trend = reactive({ labels: [], reserved: [], checkedIn: [], checkedOut: [] })
const hourly = reactive({ labels: [], counts: [] })
const distribution = ref([])
const roomUsageList = ref([])
const roomTrend = reactive({ labels: [], series: [] })

const lastUpdatedText = computed(() => lastUpdated.value || '—')
const distributionTotal = computed(() => distribution.value.reduce((s, i) => s + num(i.value), 0))

/* ---------- 图表 ---------- */
const trendOption = computed(() => ({
  color: [COLORS.indigo, COLORS.emerald, COLORS.amber],
  tooltip: { trigger: 'axis' },
  legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 11, itemHeight: 7, textStyle: { color: COLORS.axis, fontSize: 11 } },
  grid: { left: 6, right: 12, top: 38, bottom: 4, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: trend.labels,
    axisLine: { lineStyle: { color: COLORS.split } },
    axisTick: { show: false },
    axisLabel: { color: COLORS.axis, fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: COLORS.axis, fontSize: 11 },
    splitLine: { lineStyle: { color: COLORS.split } }
  },
  series: [
    {
      name: '预约',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2.4 },
      areaStyle: { opacity: 0.08 },
      data: trend.reserved
    },
    { name: '签到', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { width: 2.4 }, areaStyle: { opacity: 0.08 }, data: trend.checkedIn },
    { name: '签退', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { width: 2.4 }, areaStyle: { opacity: 0.08 }, data: trend.checkedOut }
  ]
}))

const hourlyOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 6, right: 12, top: 18, bottom: 4, containLabel: true },
  xAxis: {
    type: 'category',
    data: hourly.labels,
    axisLine: { lineStyle: { color: COLORS.split } },
    axisTick: { show: false },
    axisLabel: { color: COLORS.axis, interval: 1, fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: COLORS.axis, fontSize: 11 },
    splitLine: { lineStyle: { color: COLORS.split } }
  },
  series: [
    {
      name: '签到人数',
      type: 'bar',
      barMaxWidth: 18,
      itemStyle: {
        borderRadius: [5, 5, 0, 0],
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [
            { offset: 0, color: '#93c5fd' },
            { offset: 1, color: COLORS.indigo }
          ]
        }
      },
      data: hourly.counts
    }
  ]
}))

const distributionColorMap = {
  WAITING: COLORS.amber,
  SEATED: COLORS.emerald,
  LEFT: '#94a3b8',
  CANCELLED: '#cbd5e1',
  TIMEOUT: COLORS.rose
}

const distributionOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}：{c} 条（{d}%）' },
  legend: { bottom: 0, icon: 'circle', itemWidth: 9, itemHeight: 9, textStyle: { color: COLORS.axis, fontSize: 11 } },
  series: [
    {
      name: '座位状态',
      type: 'pie',
      radius: ['46%', '70%'],
      center: ['50%', '43%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 6 },
      label: { show: false },
      labelLine: { show: false },
      data: distribution.value.map((item) => ({
        name: item.name,
        value: num(item.value),
        itemStyle: { color: distributionColorMap[item.status] || COLORS.indigo }
      }))
    }
  ]
}))

const roomUsageOption = computed(() => {
  const list = roomUsageList.value
  return {
    color: [COLORS.indigo, COLORS.amber],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v) => `${num(v)}%` },
    legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 11, itemHeight: 7, textStyle: { color: COLORS.axis, fontSize: 11 } },
    grid: { left: 4, right: 16, top: 30, bottom: 2, containLabel: true },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: { color: COLORS.axis, fontSize: 11, formatter: '{value}%' },
      splitLine: { lineStyle: { color: COLORS.split } }
    },
    yAxis: {
      type: 'category',
      data: list.map((i) => i.roomName).slice().reverse(),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: COLORS.axis, fontSize: 11, width: 96, overflow: 'truncate' }
    },
    series: [
      { name: '在座率', type: 'bar', barMaxWidth: 11, itemStyle: { borderRadius: [0, 4, 4, 0] }, data: list.map((i) => pct(i.seatedRate)).slice().reverse() },
      { name: '预约率', type: 'bar', barMaxWidth: 11, itemStyle: { borderRadius: [0, 4, 4, 0] }, data: list.map((i) => pct(i.reservedRate)).slice().reverse() }
    ]
  }
})

const roomPalette = [COLORS.indigo, COLORS.emerald, COLORS.amber, COLORS.sky, COLORS.violet, COLORS.rose, '#06b6d4', '#d946ef']
const roomTrendOption = computed(() => ({
  color: roomPalette,
  tooltip: { trigger: 'axis' },
  legend: { type: 'scroll', top: 0, left: 0, icon: 'roundRect', itemWidth: 11, itemHeight: 7, textStyle: { color: COLORS.axis, fontSize: 11 } },
  grid: { left: 6, right: 12, top: 42, bottom: 4, containLabel: true },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: roomTrend.labels,
    axisLine: { lineStyle: { color: COLORS.split } },
    axisTick: { show: false },
    axisLabel: { color: COLORS.axis, fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    axisLabel: { color: COLORS.axis, fontSize: 11 },
    splitLine: { lineStyle: { color: COLORS.split } }
  },
  series: roomTrend.series.map((s) => ({
    name: s.name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 5,
    lineStyle: { width: 2 },
    emphasis: { focus: 'series' },
    data: (s.data || []).map((v) => num(v))
  }))
}))

/* ---------- 数据 ---------- */
async function loadOverview() {
  try {
    const res = await adminOverview()
    overview.today = res?.data?.today || ''
  } catch (e) {}
}

async function loadCharts() {
  chartsLoading.value = true
  try {
    const res = await adminCharts('all', days.value)
    const d = res?.data || {}
    const t = d.trend || {}
    trend.labels = t.labels || []
    trend.reserved = (t.reserved || []).map((v) => num(v))
    trend.checkedIn = (t.checkedIn || []).map((v) => num(v))
    trend.checkedOut = (t.checkedOut || []).map((v) => num(v))
    const h = d.hourly || {}
    hourly.labels = h.labels || []
    hourly.counts = (h.counts || []).map((v) => num(v))
    roomUsageList.value = Array.isArray(d.roomUsage) ? d.roomUsage : []
  } catch (e) {
  } finally {
    chartsLoading.value = false
  }
}

async function loadDistribution() {
  try {
    const res = await adminDistribution()
    const list = res?.data?.distribution
    distribution.value = Array.isArray(list) ? list : []
  } catch (e) {}
}

async function loadRoomTrend() {
  try {
    const res = await adminRoomTrend(days.value)
    const d = res?.data || {}
    roomTrend.labels = d.labels || []
    roomTrend.series = Array.isArray(d.series) ? d.series : []
  } catch (e) {}
}

async function loadAll() {
  loading.value = true
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  lastUpdated.value = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
  try {
    await Promise.all([loadOverview(), loadCharts(), loadDistribution(), loadRoomTrend()])
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.head-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.board {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
}

.board .card {
  margin-top: 0;
}

.board .wide {
  grid-column: 1 / -1;
}

/* 楼层使用率明细 */
.usage-list {
  margin-top: 10px;
  border-top: 1px dashed var(--c-line);
  padding-top: 8px;
}

.usage-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 2px;
  font-size: 12.5px;
}

.ur-name {
  flex: 1;
  min-width: 0;
  color: var(--c-text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ur-num {
  color: var(--c-text-3);
  font-variant-numeric: tabular-nums;
}

.ur-rate {
  font-weight: 800;
  color: var(--brand);
  min-width: 52px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.ur-rate.high {
  color: var(--c-accent);
}

@media (max-width: 1180px) {
  .board {
    grid-template-columns: 1fr;
  }
}
</style>