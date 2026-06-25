<template>
  <div class="page">
    <el-card shadow="never" class="bar-card">
      <div class="bar">
        <span class="title">农业资源一张图</span>
        <el-tag size="small" type="info" effect="plain">底图：{{ providerName }}</el-tag>
        <div class="legend">
          <span v-for="g in legendItems" :key="g.key" class="legend-item"
                :class="{ clickable: g.toggleable, sub: !g.toggleable, dim: g.toggleable && !visibleLayers[g.layerKey] }"
                :title="g.toggleable ? '点击显示/隐藏该图层' : ''"
                @click="g.toggleable && onToggleLegend(g.layerKey)">
            <i class="dot" :style="{ background: g.color }"></i>{{ g.name }} {{ g.count }}
          </span>
        </div>
        <div class="spacer" />
        <el-button :icon="Refresh" @click="load" :loading="loading">刷新</el-button>
      </div>
      <div class="bar filter-row">
        <el-autocomplete
          v-model="searchKw"
          :fetch-suggestions="querySearch"
          placeholder="搜索地块/设施名称或编码，回车或选中定位"
          clearable
          style="width: 260px"
          @select="onSearchSelect"
        >
          <template #default="{ item }">
            <span class="suggest-type">{{ item.item.type }}</span> {{ item.item.name }}
            <span v-if="item.item.code" class="suggest-code">（{{ item.item.code }}）</span>
          </template>
        </el-autocomplete>
        <el-select v-model="waterStatus" placeholder="水利运行状态" clearable style="width: 130px">
          <el-option v-for="o in runStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="supportStatus" placeholder="配套运营状态" clearable style="width: 130px">
          <el-option v-for="o in operateStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="abandonStatus" placeholder="撂荒治理状态" clearable style="width: 130px">
          <el-option v-for="o in governStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="landUseFilter" placeholder="地块用途" clearable style="width: 130px">
          <el-option v-for="o in landUseOptions" :key="o" :label="o" :value="o" />
        </el-select>
        <el-button v-if="hasActiveFilter" link @click="resetFilters">清除筛选</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="map-card">
      <MapView ref="mapRef" :groups="groups" :polygon-groups="polygonGroups" :hidden-layers="hiddenLayerKeys" height="660px" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import MapView from '../../components/MapView.vue'
import { mapApi } from '../../api'
import { activeProvider } from '../../config/mapConfig'
import { governStatusDict, governStatusOptions, runStatusOptions, operateStatusOptions } from '../../constants/dict'

const router = useRouter()
const providerName = activeProvider().name
const loading = ref(false)
const raw = ref({ parcel: [], water: [], support: [], abandon: [] })
const mapRef = ref()

// ---------------- 筛选 ----------------
const waterStatus = ref('')
const supportStatus = ref('')
const abandonStatus = ref('')
const landUseFilter = ref('')
const landUseOptions = ['基本农田', '一般耕地', '设施农用地', '园地']
const hasActiveFilter = computed(() => waterStatus.value || supportStatus.value || abandonStatus.value || landUseFilter.value)
const resetFilters = () => { waterStatus.value = ''; supportStatus.value = ''; abandonStatus.value = ''; landUseFilter.value = '' }

const filteredParcel = computed(() => (raw.value.parcel || []).filter((p) => !landUseFilter.value || p.landUse === landUseFilter.value))
const filteredWater = computed(() => (raw.value.water || []).filter((p) => !waterStatus.value || p.runStatus === waterStatus.value))
const filteredSupport = computed(() => (raw.value.support || []).filter((p) => !supportStatus.value || p.operateStatus === supportStatus.value))
const filteredAbandon = computed(() => (raw.value.abandon || []).filter((p) => !abandonStatus.value || p.governStatus === abandonStatus.value))

// ---------------- 弹窗内容(带"在列表中查看"跳转链接, 点开后不止能看, 还能直接去管理) ----------------
const parcelPopup = (p) =>
  `<b>${p.name}</b><br/>编码：${p.code}<br/>承包方：${p.contractor || '-'}<br/>面积：${p.area ?? '-'} 亩<br/>用途：${p.landUse || '-'}` +
  detailLink('parcel', p.code)

const abandonPopup = (p) =>
  `<b>${p.name}</b><br/>编码：${p.code}<br/>原因：${p.reason || '-'}<br/>治理状态：${govLabel(p.governStatus)}<br/>面积：${p.area ?? '-'} 亩` +
  detailLink('abandon', p.code)

const detailLink = (type, keyword) =>
  `<div style="margin-top:6px"><a href="#" data-jump="${type}" data-kw="${keyword || ''}" class="popup-jump-link">在列表中查看 →</a></div>`

// 弹窗是纯 HTML 字符串(Leaflet popup), 用事件委托捕获里面"在列表中查看"链接的点击
const onPopupLinkClick = (e) => {
  const link = e.target?.closest?.('.popup-jump-link')
  if (!link) return
  e.preventDefault()
  const type = link.getAttribute('data-jump')
  const kw = link.getAttribute('data-kw')
  const routeMap = { parcel: '/parcel', water: '/water', support: '/support', abandon: '/abandon' }
  if (routeMap[type]) router.push({ path: routeMap[type], query: kw ? { keyword: kw } : {} })
}
onMounted(() => document.addEventListener('click', onPopupLinkClick))
onBeforeUnmount(() => document.removeEventListener('click', onPopupLinkClick))

// 确权地块 / 撂荒地块均绘制为多边形面
const polygonGroups = computed(() => [
  {
    key: 'parcel', name: '确权地块（面）', color: '#409eff',
    features: filteredParcel.value
      .filter((p) => p.boundary)
      .map((p) => ({ boundary: p.boundary, popup: parcelPopup(p) }))
  },
  {
    key: 'abandon', name: '撂荒地块（按治理状态）', color: '#f56c6c',
    features: filteredAbandon.value
      .filter((p) => p.boundary)
      .map((p) => ({ boundary: p.boundary, color: govColor(p.governStatus), popup: abandonPopup(p) }))
  }
])

const groups = computed(() => [
  {
    key: 'water', name: '水利设施', color: '#2e9e5b',
    points: filteredWater.value.map((p) => ({
      ...p, popup: `<b>${p.name}</b><br/>类型：${p.subtype}<br/>运行状态：${p.runStatus}<br/>责任人：${p.manager || '-'}` + detailLink('water', p.name)
    }))
  },
  {
    key: 'support', name: '配套设施', color: '#e6a23c',
    points: filteredSupport.value.map((p) => ({
      ...p, popup: `<b>${p.name}</b><br/>运营状态：${p.operateStatus}<br/>${p.ability || ''}` + detailLink('support', p.name)
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

// 图例同时承担"图层开关"角色, 不再用 Leaflet 右上角原生勾选框(两套控制容易让人疑惑该去哪关图层)
// key 对应 MapView 内部 overlayLayers 的 key: 多边形图层是 'poly_<key>', 点位图层是 '<key>'
const visibleLayers = reactive({ poly_parcel: true, water: true, support: true, poly_abandon: true })
const hiddenLayerKeys = computed(() => Object.entries(visibleLayers).filter(([, v]) => !v).map(([k]) => k))
const onToggleLegend = (layerKey) => {
  if (!layerKey) return
  visibleLayers[layerKey] = !visibleLayers[layerKey]
  mapRef.value?.toggleLayer(layerKey, visibleLayers[layerKey])
}

const legendItems = computed(() => {
  const items = [
    { key: 'parcel', name: '确权地块', color: '#409eff', count: filteredParcel.value.length, layerKey: 'poly_parcel', toggleable: true },
    ...groups.value.map((g) => ({ key: g.key, name: g.name, color: g.color, count: g.points.length, layerKey: g.key, toggleable: true })),
    { key: 'abandon', name: '撂荒地块', color: '#f56c6c', count: filteredAbandon.value.length, layerKey: 'poly_abandon', toggleable: true }
  ]
  // 撂荒按治理状态拆的色块仅作图例说明(同一图层内不同颜色的面, 不是独立图层, 不可单独开关)
  Object.entries(govColorMap).forEach(([status, color]) => {
    const count = filteredAbandon.value.filter((p) => p.governStatus === status).length
    if (count > 0) items.push({ key: 'ab_' + status, name: '撂荒·' + govLabel(status), color, count, toggleable: false })
  })
  return items
})

const govLabel = (s) => governStatusDict[s]?.label || s

// ---------------- 搜索定位 ----------------
const searchKw = ref('')
const searchableItems = computed(() => {
  const items = []
  ;(raw.value.parcel || []).forEach((p) => items.push({ type: '确权地块', name: p.name, code: p.code, lng: p.lng, lat: p.lat, boundary: p.boundary }))
  ;(raw.value.water || []).forEach((p) => items.push({ type: '水利设施', name: p.name, code: '', lng: p.lng, lat: p.lat, boundary: null }))
  ;(raw.value.support || []).forEach((p) => items.push({ type: '配套设施', name: p.name, code: '', lng: p.lng, lat: p.lat, boundary: null }))
  ;(raw.value.abandon || []).forEach((p) => items.push({ type: '撂荒地块', name: p.name, code: p.code, lng: p.lng, lat: p.lat, boundary: p.boundary }))
  return items
})
const querySearch = (queryStr, cb) => {
  const kw = (queryStr || '').trim()
  if (!kw) { cb([]); return }
  const results = searchableItems.value
    .filter((it) => (it.name && it.name.includes(kw)) || (it.code && it.code.includes(kw)))
    .slice(0, 20)
    .map((it) => ({ value: `${it.name}${it.code ? ' · ' + it.code : ''}`, item: it }))
  cb(results)
}
const onSearchSelect = (sel) => {
  mapRef.value?.focusFeature(sel.item)
}

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
.filter-row { margin-top: 10px; }
.title { font-weight: 600; color: #1f2d3d; }
.legend { display: flex; gap: 14px; flex-wrap: wrap; }
.legend-item { font-size: 13px; color: #606266; display: flex; align-items: center; gap: 4px; }
.legend-item.clickable { cursor: pointer; user-select: none; }
.legend-item.clickable:hover { color: #2e9e5b; }
.legend-item.dim { opacity: 0.45; }
.legend-item.sub { font-size: 12px; color: #909399; }
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.spacer { flex: 1; }
.map-card :deep(.el-card__body) { padding: 10px; }
.suggest-type { color: #909399; font-size: 12px; margin-right: 4px; }
.suggest-code { color: #909399; font-size: 12px; }
</style>
