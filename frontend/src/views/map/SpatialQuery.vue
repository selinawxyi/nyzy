<template>
  <div class="page">
    <el-card shadow="never" class="bar-card">
      <div class="bar">
        <span class="title">空间范围查询</span>
        <el-select v-model="targetType" style="width: 160px" @change="onTargetChange">
          <el-option v-for="t in targetTypes" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <div class="spacer" />
        <el-button :icon="RefreshLeft" @click="clearAll">清除重画</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="map-card">
      <MapView ref="mapRef" :groups="resultGroups" :polygon-groups="resultPolygonGroups"
               draw-mode="shape" height="560px" @draw-created="onDrawCreated" @exit-draw="clearAll" />
    </el-card>

    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="result-header">
          <span>查询结果（{{ results.length }}）</span>
          <el-button v-if="targetType === 'parcel' && results.length" type="primary" plain size="small" @click="openBatchEdit">
            批量修改这 {{ results.length }} 个地块
          </el-button>
        </div>
      </template>
      <el-table :data="results" size="small" max-height="320" empty-text="请先在地图上画一个范围">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column v-if="targetType==='parcel'||targetType==='planting'||targetType==='quality'||targetType==='abandon'"
                          prop="code" label="地块编码" width="140" />
        <el-table-column prop="lng" label="经度" width="110" />
        <el-table-column prop="lat" label="纬度" width="110" />
        <el-table-column label="其他信息" min-width="220">
          <template #default="{ row }">
            <span v-for="(v, k) in extraFields(row)" :key="k" style="margin-right:12px">{{ k }}: {{ v }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="batchEditVisible" title="空间框选批量修改地块属性" width="480px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        :title="`将对框选范围内的 ${results.length} 个地块统一修改以下字段（留空不改）`" />
      <el-form :model="batchEditForm" label-width="100px">
        <el-form-item label="承包方姓名"><el-input v-model="batchEditForm.contractorName" /></el-form-item>
        <el-form-item label="承包方编码"><el-input v-model="batchEditForm.contractorCode" /></el-form-item>
        <el-form-item label="地块用途">
          <el-select v-model="batchEditForm.landUse" clearable style="width:100%">
            <el-option v-for="o in landUseOptions" :key="o" :label="o" :value="o" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBatchEdit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshLeft } from '@element-plus/icons-vue'
import MapView from '../../components/MapView.vue'
import { gisApi, parcelApi } from '../../api'
import { confirmBatchUpdate } from '../../utils/batchPreview'

const targetTypes = [
  { value: 'water', label: '水利设施' },
  { value: 'support', label: '配套设施' },
  { value: 'parcel', label: '确权地块' },
  { value: 'planting', label: '种植记录' },
  { value: 'quality', label: '耕地质量' },
  { value: 'abandon', label: '撂荒地块' }
]
const colorMap = { water: '#2e9e5b', support: '#e6a23c', parcel: '#409eff', planting: '#67c23a', quality: '#909399', abandon: '#f56c6c' }

const targetType = ref('water')
const results = ref([])
const lastShape = ref(null)
const loading = ref(false)
const mapRef = ref()

const KNOWN_KEYS = ['id', 'name', 'lng', 'lat', 'code', 'boundary']
const extraFields = (row) => {
  const m = {}
  Object.keys(row).forEach((k) => { if (!KNOWN_KEYS.includes(k) && row[k] != null) m[k] = row[k] })
  return m
}

const resultGroups = computed(() => [{
  key: 'result', name: '查询结果', color: colorMap[targetType.value],
  points: results.value.map((r) => ({ lng: r.lng, lat: r.lat, name: r.name, popup: r.name }))
}])

const resultPolygonGroups = computed(() => [{
  key: 'result-poly', name: '查询结果(面)', color: colorMap[targetType.value],
  features: results.value.filter((r) => r.boundary).map((r) => ({ boundary: r.boundary, popup: r.name }))
}])

const onDrawCreated = async (shape) => {
  lastShape.value = shape
  await runQuery()
}

const onTargetChange = () => {
  if (lastShape.value) runQuery()
}

const runQuery = async () => {
  if (!lastShape.value) return
  loading.value = true
  try {
    results.value = await gisApi.spatialQuery(targetType.value, lastShape.value)
  } catch (e) {
    ElMessage.error('查询失败')
  } finally {
    loading.value = false
  }
}

const clearAll = () => {
  mapRef.value?.clearDrawn()
  lastShape.value = null
  results.value = []
}

// ---------------- 批量修改地块属性 ----------------
const landUseOptions = ['基本农田', '一般耕地', '设施农用地', '园地']
const batchEditVisible = ref(false)
const batchEditForm = reactive({ contractorName: '', contractorCode: '', landUse: '' })
const batchEditFieldLabels = { contractorName: '承包方姓名', contractorCode: '承包方编码', landUse: '地块用途' }
const saving = ref(false)
const openBatchEdit = () => {
  Object.assign(batchEditForm, { contractorName: '', contractorCode: '', landUse: '' })
  batchEditVisible.value = true
}
const submitBatchEdit = async () => {
  const updates = {
    contractorName: batchEditForm.contractorName || null,
    contractorCode: batchEditForm.contractorCode || null,
    landUse: batchEditForm.landUse || null
  }
  try {
    await confirmBatchUpdate(results.value.length, updates, batchEditFieldLabels)
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
    return
  }
  // 承包方编码/地块用途属于关键字段, 二次确认并要求填写变更原因(与单条编辑规则一致)
  let reason = ''
  if (updates.contractorCode || updates.landUse) {
    try {
      const { value } = await ElMessageBox.prompt(
        '修改承包方编码或地块用途属于关键变更，请填写变更原因：', '关键字段变更二次确认',
        { confirmButtonText: '确认修改', cancelButtonText: '取消', inputType: 'textarea', type: 'warning',
          inputValidator: (v) => (v && v.trim() ? true : '变更原因不能为空') })
      reason = value
    } catch (e) { return /* cancel */ }
  }
  saving.value = true
  try {
    const n = await parcelApi.batch(results.value.map((r) => r.id), updates, reason)
    ElMessage.success(`已修改 ${n} 个地块`)
    batchEditVisible.value = false
    runQuery()
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.bar-card :deep(.el-card__body) { padding: 12px 16px; }
.bar { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.title { font-weight: 600; color: #1f2d3d; }
.spacer { flex: 1; }
.map-card :deep(.el-card__body) { padding: 10px; }
.result-header { display: flex; align-items: center; justify-content: space-between; }
</style>
