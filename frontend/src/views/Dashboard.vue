<template>
  <div class="page">
    <!-- 统计卡片 -->
    <div class="kpi-row">
      <el-card v-for="k in kpis" :key="k.key" shadow="never" class="kpi-card" @click="router.push(k.route)">
        <div class="kpi-icon" :style="{ background: k.color }"><el-icon :size="22" color="#fff"><component :is="k.icon" /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-value">{{ k.value }}</div>
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-sub">{{ k.sub }}</div>
        </div>
      </el-card>
    </div>

    <div class="mid-row">
      <!-- 待办提醒 -->
      <el-card shadow="never" class="todo-card">
        <template #header><span class="card-title">待办提醒</span></template>
        <div class="todo-item" @click="router.push({ name: 'auditCenter' })">
          <el-icon :size="18" color="#e6a23c"><Bell /></el-icon>
          <span class="todo-label">待审核事项</span>
          <el-tag v-if="pendingCount" type="warning" effect="light">{{ pendingCount }} 条待处理</el-tag>
          <el-tag v-else type="success" effect="light">暂无待审核</el-tag>
        </div>
        <div class="todo-item">
          <el-icon :size="18" color="#909399"><ChatDotRound /></el-icon>
          <span class="todo-label">未读通知</span>
          <el-tag v-if="unreadCount" type="danger" effect="light">{{ unreadCount }} 条未读</el-tag>
          <el-tag v-else type="success" effect="light">暂无未读</el-tag>
          <span class="todo-hint">查看右上角铃铛图标</span>
        </div>
      </el-card>

      <!-- 一张图入口 -->
      <el-card shadow="never" class="map-entry-card" @click="router.push({ name: 'map' })">
        <div class="map-entry-icon"><el-icon :size="34" color="#2e9e5b"><Location /></el-icon></div>
        <div class="map-entry-text">
          <div class="map-entry-title">农业资源一张图</div>
          <div class="map-entry-desc">地块 · 设施 · 撂荒地，一图总览，支持搜索定位</div>
        </div>
        <el-button type="primary">打开地图 →</el-button>
      </el-card>
    </div>

    <!-- 常用操作 -->
    <el-card shadow="never">
      <template #header><span class="card-title">常用操作</span></template>
      <div class="shortcut-row">
        <div v-for="s in shortcuts" :key="s.label" class="shortcut-item" @click="router.push(s.route)">
          <el-icon :size="20" :color="s.color"><component :is="s.icon" /></el-icon>
          <span>{{ s.label }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Grid, Warning, Drizzling, OfficeBuilding, Crop, Bell, ChatDotRound, Location } from '@element-plus/icons-vue'
import { mapApi, auditCenterApi, notificationApi } from '../api'

const router = useRouter()
const raw = ref({ parcel: [], water: [], support: [], abandon: [] })
const pendingCount = ref(0)
const unreadCount = ref(0)

const sumArea = (list) => list.reduce((s, p) => s + (Number(p.area) || 0), 0).toFixed(2)
const countException = (list, statusField) => list.filter((p) => p[statusField] && p[statusField] !== '正常').length

const abandonByStatus = computed(() => {
  const m = {}
  ;(raw.value.abandon || []).forEach((p) => { m[p.governStatus] = (m[p.governStatus] || 0) + 1 })
  return m
})

const kpis = computed(() => {
  const parcel = raw.value.parcel || []
  const abandon = raw.value.abandon || []
  const water = raw.value.water || []
  const support = raw.value.support || []
  return [
    {
      key: 'parcel', label: '确权地块', icon: Grid, color: '#409eff',
      value: parcel.length, sub: `总面积 ${sumArea(parcel)} 亩`,
      route: { name: 'parcel' }
    },
    {
      key: 'abandon', label: '撂荒地块', icon: Warning, color: '#f56c6c',
      value: abandon.length,
      sub: `未治理 ${abandonByStatus.value.UNGOVERNED || 0} · 治理中 ${abandonByStatus.value.GOVERNING || 0} · 已治理 ${abandonByStatus.value.GOVERNED || 0}`,
      route: { name: 'abandon' }
    },
    {
      key: 'water', label: '水利设施', icon: Drizzling, color: '#2e9e5b',
      value: water.length,
      sub: countException(water, 'runStatus') ? `${countException(water, 'runStatus')} 处状态异常` : '运行状态均正常',
      route: { name: 'water' }
    },
    {
      key: 'support', label: '配套设施', icon: OfficeBuilding, color: '#e6a23c',
      value: support.length,
      sub: countException(support, 'operateStatus') ? `${countException(support, 'operateStatus')} 处状态异常` : '运营状态均正常',
      route: { name: 'support' }
    }
  ]
})

const shortcuts = [
  { label: '新增确权地块', icon: Grid, color: '#409eff', route: { name: 'parcel' } },
  { label: '新增水利设施', icon: Drizzling, color: '#2e9e5b', route: { name: 'water' } },
  { label: '新增配套设施', icon: OfficeBuilding, color: '#e6a23c', route: { name: 'support' } },
  { label: '新增种植记录', icon: Crop, color: '#67c23a', route: { name: 'planting' } },
  { label: '新增撂荒上报', icon: Warning, color: '#f56c6c', route: { name: 'abandon' } }
]

onMounted(async () => {
  mapApi.points().then((d) => { raw.value = d })
  auditCenterApi.list().then((rows) => { pendingCount.value = rows.length }).catch(() => {})
  notificationApi.unreadCount().then((d) => { unreadCount.value = d.count }).catch(() => {})
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 14px; }
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.kpi-card { cursor: pointer; transition: box-shadow 0.2s; }
.kpi-card:hover { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08); }
.kpi-card :deep(.el-card__body) { display: flex; align-items: center; gap: 14px; padding: 18px; }
.kpi-icon { width: 48px; height: 48px; border-radius: 10px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.kpi-value { font-size: 24px; font-weight: 700; color: #1f2d3d; line-height: 1.2; }
.kpi-label { font-size: 13px; color: #606266; margin-top: 2px; }
.kpi-sub { font-size: 12px; color: #909399; margin-top: 4px; }
.mid-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.card-title { font-weight: 600; color: #1f2d3d; }
.todo-item { display: flex; align-items: center; gap: 8px; padding: 10px 4px; border-bottom: 1px dashed #ebeef5; }
.todo-item:last-child { border-bottom: none; }
.todo-label { font-size: 14px; color: #303133; min-width: 84px; }
.todo-hint { font-size: 12px; color: #c0c4cc; margin-left: auto; }
.map-entry-card { cursor: pointer; transition: box-shadow 0.2s; }
.map-entry-card:hover { box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08); }
.map-entry-card :deep(.el-card__body) { display: flex; align-items: center; gap: 16px; padding: 18px; height: 100%; }
.map-entry-icon { width: 60px; height: 60px; border-radius: 10px; background: #ecf7f0; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.map-entry-text { flex: 1; }
.map-entry-title { font-size: 15px; font-weight: 600; color: #1f2d3d; }
.map-entry-desc { font-size: 12px; color: #909399; margin-top: 4px; }
.shortcut-row { display: flex; gap: 14px; flex-wrap: wrap; }
.shortcut-item {
  display: flex; align-items: center; gap: 8px; padding: 10px 18px; border: 1px solid #ebeef5;
  border-radius: 8px; cursor: pointer; font-size: 14px; color: #303133; transition: all 0.2s;
}
.shortcut-item:hover { border-color: #2e9e5b; color: #2e9e5b; background: #ecf7f0; }
</style>
