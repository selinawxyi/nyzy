<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <span class="label">分析年度：</span>
        <el-select v-model="year" placeholder="全部年度" clearable style="width: 140px" @change="loadAll">
          <el-option v-for="y in years" :key="y" :label="`${y}年`" :value="y" />
        </el-select>
        <span class="hint">数据来源：有效种植记录（已排除"无效"与删除）</span>
      </div>
    </el-card>

    <!-- 指标卡 -->
    <div class="stat-row">
      <el-card shadow="never" class="stat-card"><div class="stat-val">{{ overview.totalArea ?? '-' }}</div><div class="stat-label">种植总面积(亩)</div></el-card>
      <el-card shadow="never" class="stat-card"><div class="stat-val">{{ overview.recordCount ?? '-' }}</div><div class="stat-label">种植记录数</div></el-card>
      <el-card shadow="never" class="stat-card"><div class="stat-val">{{ overview.cropCount ?? '-' }}</div><div class="stat-label">作物种类</div></el-card>
      <el-card shadow="never" class="stat-card"><div class="stat-val">{{ landUseMap['在耕'] ?? '-' }} / {{ landUseMap['撂荒'] ?? '-' }}</div><div class="stat-label">在耕 / 撂荒 地块数</div></el-card>
    </div>

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">种植结构（E1.3）</span></template>
          <EChart v-if="structurePie.series" :option="structurePie" />
          <el-alert :title="overview.evaluation" type="warning" :closable="false" show-icon style="margin-top:8px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">耕地利用类型（E1.5）</span></template>
          <EChart v-if="landUsePie.series" :option="landUsePie" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header><span class="card-title">年度变化趋势（E1.2，各作物面积逐年）</span></template>
      <EChart v-if="yearlyLine.series" :option="yearlyLine" height="340px" />
      <div v-if="changes.length" class="changes">
        <el-tag v-for="c in changes" :key="c.crop"
          :type="c.trend === 'UP' ? 'danger' : c.trend === 'DOWN' ? 'success' : 'info'"
          effect="light" style="margin:4px 6px 0 0">
          {{ c.crop }} {{ c.changePct > 0 ? '+' : '' }}{{ c.changePct }}%
          <span v-if="c.trend === 'UP'">↑</span><span v-else-if="c.trend === 'DOWN'">↓</span>
        </el-tag>
        <span class="hint">（首尾年度对比，±20% 视为显著变化）</span>
      </div>
    </el-card>

    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">种植结构流转桑基图（E1.2）</span></template>
          <EChart v-if="sankeyOption.series" :option="sankeyOption" height="320px" />
          <div class="muted" style="text-align:center">{{ sankeyData.fromYear }}年 → {{ sankeyData.toYear }}年 地块作物流转</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span class="card-title">多区域横向对比（按村组堆叠）</span></template>
          <EChart v-if="regionCompareOption.series" :option="regionCompareOption" height="340px" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header><span class="card-title">时间轴动态演变（E1.6，种植结构逐年）</span></template>
      <div class="evolution">
        <div class="evo-left">
          <EChart v-if="evolutionPie.series" :option="evolutionPie" height="260px" />
        </div>
        <div class="evo-right">
          <div class="evo-year">{{ evoYear }}<span class="evo-unit">年</span></div>
          <div class="evo-total">种植总面积 {{ evoTotal }} 亩</div>
          <el-slider v-model="evoIdx" :min="0" :max="Math.max(0, yearlyData.years.length - 1)"
            :format-tooltip="(i) => (yearlyData.years[i] || '') + '年'" @input="stopPlay" />
          <div class="evo-ctrl">
            <el-button :icon="playing ? VideoPause : VideoPlay" size="small" type="primary" @click="togglePlay">
              {{ playing ? '暂停' : '播放' }}
            </el-button>
            <el-button :icon="DArrowLeft" size="small" @click="step(-1)" circle />
            <el-button :icon="DArrowRight" size="small" @click="step(1)" circle />
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span class="card-title">优势产区识别（E1.4）</span>
          <el-select v-model="advCrop" size="small" style="width:120px" @change="loadAdvantage">
            <el-option v-for="c in cropList" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
      </template>
      <el-alert v-if="advantage.recommendation" :title="advantage.recommendation" type="success" :closable="false" show-icon style="margin-bottom:10px" />
      <el-table :data="advantage.zones || []" size="small" max-height="320">
        <el-table-column prop="region" label="村组" width="100" />
        <el-table-column label="优势度" width="180">
          <template #default="{ row }">
            <el-progress :percentage="row.score" :color="advColor(row.score)" />
          </template>
        </el-table-column>
        <el-table-column label="适宜性" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.suitability === '高适宜' ? 'success' : row.suitability === '中适宜' ? 'warning' : 'info'">{{ row.suitability }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="avgYield" label="平均单产" width="100" />
        <el-table-column prop="totalArea" label="规模(亩)" width="100" />
        <el-table-column label="平均地力" width="90">
          <template #default="{ row }">{{ row.avgGrade != null ? row.avgGrade + '等' : '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header><span class="card-title">行政区划分析（E1.1，按村组）</span></template>
      <el-row :gutter="12">
        <el-col :span="14"><EChart v-if="regionBar.series" :option="regionBar" /></el-col>
        <el-col :span="10">
          <el-table :data="regionRows" size="small" max-height="300">
            <el-table-column prop="region" label="村组" />
            <el-table-column prop="totalArea" label="种植面积(亩)" width="120" />
            <el-table-column prop="mainCrop" label="主导作物" width="90" />
          </el-table>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { VideoPlay, VideoPause, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import { analysisApi } from '../../api'
import EChart from '../../components/EChart.vue'

const cropColor = { 水稻: '#409eff', 玉米: '#e6a23c', 大豆: '#67c23a', 小麦: '#b88230', 谷子: '#909399', 马铃薯: '#9b59b6' }

const years = ref([])
const year = ref('')
const overview = reactive({})
const regionRows = ref([])
const landUseRows = ref([])
const changes = ref([])
const yearlyData = reactive({ years: [], series: [] })

const landUseMap = computed(() => Object.fromEntries(landUseRows.value.map((r) => [r.type, r.count])))

const structurePie = computed(() => {
  if (!overview.structure) return {}
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}亩 ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['40%', '65%'], center: ['50%', '45%'],
      data: overview.structure.map((s) => ({ name: s.crop, value: s.area, itemStyle: { color: cropColor[s.crop] } })),
      label: { formatter: '{b}\n{d}%' }
    }]
  }
})

const landUsePie = computed(() => {
  if (!landUseRows.value.length) return {}
  const colors = { 在耕: '#67c23a', 撂荒: '#f56c6c', 季节性闲置: '#e6a23c', 非农化: '#909399' }
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 块' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: '60%', center: ['50%', '45%'],
      data: landUseRows.value.map((r) => ({ name: r.type, value: r.count, itemStyle: { color: colors[r.type] } }))
    }]
  }
})

const yearlyLine = computed(() => {
  if (!yearlyData.series.length) return {}
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 50, right: 20, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: yearlyData.years.map((y) => `${y}年`) },
    yAxis: { type: 'value', name: '亩' },
    series: yearlyData.series.map((s) => ({
      name: s.crop, type: 'line', smooth: true, data: s.data,
      itemStyle: { color: cropColor[s.crop] }, lineStyle: { color: cropColor[s.crop] }
    }))
  }
})

const regionBar = computed(() => {
  if (!regionRows.value.length) return {}
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 70, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'value', name: '亩' },
    yAxis: { type: 'category', data: regionRows.value.map((r) => r.region).reverse() },
    series: [{
      type: 'bar', data: regionRows.value.map((r) => r.totalArea).reverse(),
      itemStyle: { color: '#2e9e5b' }, barWidth: '55%',
      label: { show: true, position: 'right', formatter: '{c}' }
    }]
  }
})

// E1.2 桑基图
const sankeyData = reactive({ fromYear: '', toYear: '', nodes: [], links: [] })
const sankeyOption = computed(() => {
  if (!sankeyData.nodes.length) return {}
  return {
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [{
      type: 'sankey', left: 10, right: 120, top: 10, bottom: 10,
      data: sankeyData.nodes, links: sankeyData.links,
      label: { fontSize: 11 },
      lineStyle: { color: 'gradient', curveness: 0.5 }
    }]
  }
})

// 多区域横向对比
const regionCompareData = reactive({ regions: [], series: [] })
const regionCompareOption = computed(() => {
  if (!regionCompareData.series.length) return {}
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0 },
    grid: { left: 50, right: 20, top: 20, bottom: 50 },
    xAxis: { type: 'category', data: regionCompareData.regions },
    yAxis: { type: 'value', name: '亩' },
    series: regionCompareData.series.map((s) => ({
      name: s.crop, type: 'bar', stack: 'total', data: s.data,
      itemStyle: { color: cropColor[s.crop] }
    }))
  }
})

// E1.6 时间轴动态演变（复用 yearly 数据，逐年结构）
const evoIdx = ref(0)
const playing = ref(false)
let timer = null
const evoYear = computed(() => yearlyData.years[evoIdx.value] ?? '-')
const yearStructure = (idx) => yearlyData.series
  .map((s) => ({ crop: s.crop, area: s.data[idx] || 0 }))
  .filter((x) => x.area > 0)
const evoTotal = computed(() => yearStructure(evoIdx.value).reduce((a, b) => a + b.area, 0).toFixed(2))
const evolutionPie = computed(() => {
  const items = yearStructure(evoIdx.value)
  if (!items.length) return { title: { text: '该年无数据', left: 'center', top: 'center', textStyle: { fontSize: 13, color: '#909399' } } }
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}亩 ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['35%', '62%'], center: ['50%', '45%'],
      data: items.map((s) => ({ name: s.crop, value: s.area, itemStyle: { color: cropColor[s.crop] } })),
      label: { formatter: '{b}\n{d}%' }
    }]
  }
})
const stopPlay = () => { playing.value = false; if (timer) { clearInterval(timer); timer = null } }
const togglePlay = () => {
  if (playing.value) { stopPlay(); return }
  if (!yearlyData.years.length) return
  playing.value = true
  timer = setInterval(() => {
    if (evoIdx.value >= yearlyData.years.length - 1) evoIdx.value = 0
    else evoIdx.value++
  }, 1200)
}
const step = (d) => {
  stopPlay()
  const n = yearlyData.years.length
  evoIdx.value = (evoIdx.value + d + n) % n
}
onBeforeUnmount(stopPlay)

// E1.4 优势产区
const cropList = ['水稻', '玉米', '大豆', '小麦']
const advCrop = ref('水稻')
const advantage = reactive({ zones: [], recommendation: '' })
const advColor = (s) => (s >= 75 ? '#67c23a' : s >= 50 ? '#e6a23c' : '#909399')
const loadAdvantage = async () => {
  const d = await analysisApi.advantageZones(advCrop.value)
  advantage.zones = d.zones
  advantage.recommendation = d.recommendation
}

const loadAll = async () => {
  const y = year.value || undefined
  const [ov, yl, rg, lu] = await Promise.all([
    analysisApi.overview(y), analysisApi.yearly(), analysisApi.region(y), analysisApi.landUse(y)
  ])
  Object.assign(overview, ov)
  yearlyData.years = yl.years
  yearlyData.series = yl.series
  changes.value = yl.changes || []
  regionRows.value = rg
  landUseRows.value = lu
  evoIdx.value = Math.max(0, yearlyData.years.length - 1)
  loadAdvantage()
  loadSankeyAndCompare()
}
const loadSankeyAndCompare = async () => {
  const [sk, rc] = await Promise.all([analysisApi.sankey(), analysisApi.regionCompare(year.value || undefined)])
  Object.assign(sankeyData, sk)
  regionCompareData.regions = rc.regions
  regionCompareData.series = rc.series
}

onMounted(async () => {
  years.value = await analysisApi.years()
  loadAll()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.filter-card :deep(.el-card__body) { padding: 14px 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; }
.label { font-size: 14px; color: #606266; }
.hint { font-size: 12px; color: #909399; margin-left: 8px; }
.card-title { font-weight: 600; color: #1f2d3d; }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.evolution { display: flex; gap: 20px; align-items: center; }
.evo-left { flex: 1; }
.evo-right { width: 280px; }
.evo-year { font-size: 40px; font-weight: 700; color: #2e9e5b; line-height: 1; }
.evo-unit { font-size: 16px; margin-left: 4px; }
.evo-total { color: #606266; margin: 6px 0 18px; }
.evo-ctrl { display: flex; gap: 8px; align-items: center; margin-top: 10px; }
.stat-row { display: flex; gap: 12px; }
.stat-card { flex: 1; text-align: center; }
.stat-card :deep(.el-card__body) { padding: 16px; }
.stat-val { font-size: 26px; font-weight: 700; color: #2e9e5b; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.changes { margin-top: 10px; }
</style>
