<template>
  <div class="page">
    <el-card shadow="never" class="bar-card">
      <div class="bar">
        <span class="title">农业资源一张图</span>
        <el-tag size="small" type="info" effect="plain">底图：{{ providerName }}</el-tag>
        <div class="legend">
          <span v-for="g in legendItems" :key="g.key" class="legend-item">
            <i class="dot" :style="{ background: g.color }"></i>{{ g.name }} {{ g.count }}
          </span>
        </div>
        <div class="spacer" />
        <el-button :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="map-card">
      <MapView :groups="groups" :polygon-groups="polygonGroups" height="660px" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import MapView from '../../components/MapView.vue'
import { mapApi } from '../../api'
import { activeProvider } from '../../config/mapConfig'
import { governStatusDict } from '../../constants/dict'

const providerName = activeProvider().name
const loading = ref(false)
const raw = ref({ parcel: [], water: [], support: [], abandon: [] })

const parcelPopup = (p) =>
  `<b>${p.name}</b><br/>编码：${p.code}<br/>承包方：${p.contractor || '-'}<br/>面积：${p.area ?? '-'} 亩<br/>用途：${p.landUse || '-'}`

const abandonPopup = (p) =>
  `<b>${p.name}</b><br/>编码：${p.code}<br/>原因：${p.reason || '-'}<br/>治理状态：${govLabel(p.governStatus)}<br/>面积：${p.area ?? '-'} 亩`

// 确权地块 / 撂荒地块均绘制为多边形面
const polygonGroups = computed(() => [
  {
    key: 'parcel', name: '确权地块（面）', color: '#409eff',
    features: (raw.value.parcel || [])
      .filter((p) => p.boundary)
      .map((p) => ({ boundary: p.boundary, popup: parcelPopup(p) }))
  },
  {
    key: 'abandon', name: '撂荒地块（按治理状态）', color: '#f56c6c',
    features: (raw.value.abandon || [])
      .filter((p) => p.boundary)
      .map((p) => ({ boundary: p.boundary, color: govColor(p.governStatus), popup: abandonPopup(p) }))
  }
])

const groups = computed(() => [
  {
    key: 'water', name: '水利设施', color: '#2e9e5b',
    points: (raw.value.water || []).map((p) => ({
      ...p, popup: `<b>${p.name}</b><br/>类型：${p.subtype}<br/>运行状态：${p.runStatus}<br/>责任人：${p.manager || '-'}`
    }))
  },
  {
    key: 'support', name: '配套设施', color: '#e6a23c',
    points: (raw.value.support || []).map((p) => ({
      ...p, popup: `<b>${p.name}</b><br/>运营状态：${p.operateStatus}<br/>${p.ability || ''}`
    }))
  }
])

// 撂荒治理状态分色
const govColorMap = {
  PENDING: '#e6a23c',    // 待审核
  UNGOVERNED: '#f56c6c', // 未治理
  GOVERNING: '#909399',  // 治理中
  GOVERNED: '#67c23a',   // 已治理
  REJECTED: '#c0c4cc'    // 已驳回
}
const govColor = (s) => govColorMap[s] || '#f56c6c'

const legendItems = computed(() => {
  const items = [
    { key: 'parcel', name: '确权地块', color: '#409eff', count: (raw.value.parcel || []).length },
    ...groups.value.map((g) => ({ key: g.key, name: g.name, color: g.color, count: g.points.length }))
  ]
  // 撂荒按状态拆分图例
  const abandon = raw.value.abandon || []
  Object.entries(govColorMap).forEach(([status, color]) => {
    const count = abandon.filter((p) => p.governStatus === status).length
    if (count > 0) items.push({ key: 'ab_' + status, name: '撂荒·' + govLabel(status), color, count })
  })
  return items
})

const govLabel = (s) => governStatusDict[s]?.label || s

const load = async () => {
  loading.value = true
  try {
    raw.value = await mapApi.points()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.bar-card :deep(.el-card__body) { padding: 12px 16px; }
.bar { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.title { font-weight: 600; color: #1f2d3d; }
.legend { display: flex; gap: 14px; flex-wrap: wrap; }
.legend-item { font-size: 13px; color: #606266; display: flex; align-items: center; gap: 4px; }
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.spacer { flex: 1; }
.map-card :deep(.el-card__body) { padding: 10px; }
</style>
