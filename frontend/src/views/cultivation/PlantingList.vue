<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="地块编码/地块名/填报人" clearable style="width: 220px" @keyup.enter="onSearch" />
        <el-date-picker v-model="query.plantYear" type="year" placeholder="种植年度" value-format="YYYY" style="width: 130px" />
        <el-select v-model="query.crop" placeholder="作物" clearable style="width: 120px">
          <el-option v-for="o in cropOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.dataSource" placeholder="数据来源" clearable style="width: 130px">
          <el-option v-for="o in plantingSourceOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Download" @click="onExport">导出 Excel</el-button>
        <ImportButton type="planting" template-name="种植数据导入模板.xlsx" @done="load" />
        <div class="flex-spacer" />
        <el-button type="primary" :icon="Plus" @click="openForm()">新增种植记录</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="parcelCode" label="地块编码" width="120" />
        <el-table-column label="地块名称" min-width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="openHistory(row)">{{ row.parcelName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="plantYear" label="年度" width="74" />
        <el-table-column label="季节" width="70">
          <template #default="{ row }">{{ seasonDict[row.season] }}</template>
        </el-table-column>
        <el-table-column prop="crop" label="作物" width="80" />
        <el-table-column prop="variety" label="品种" width="100" />
        <el-table-column label="面积(亩)" width="90">
          <template #default="{ row }">{{ row.area ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="产量(kg/亩)" width="100">
          <template #default="{ row }">
            <span v-if="row.yieldPerMu">{{ row.yieldPerMu }}</span>
            <span v-else class="muted">未收获</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">{{ plantingSourceDict[row.dataSource] }}</template>
        </el-table-column>
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag :type="plantingStatusDict[row.status]?.type" effect="light" size="small">
              {{ plantingStatusDict[row.status]?.label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button v-if="row.status === 'VALID'" link type="warning" size="small" @click="onInvalid(row)">标记无效</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
          :current-page="query.page" :page-size="query.size" :page-sizes="[10, 20, 50, 100]"
          @current-change="onPage" @size-change="onSize" />
      </div>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑种植记录' : '新增种植记录'" width="640px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="地块编码" prop="parcelCode"><el-input v-model="form.parcelCode" placeholder="如 JYB-T001" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地块名称"><el-input v-model="form.parcelName" placeholder="留空自动带出" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="种植年度" prop="plantYear"><el-date-picker v-model="form.plantYear" type="year" value-format="YYYY" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="季节"><el-select v-model="form.season" style="width:100%"><el-option v-for="o in seasonOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="作物" prop="crop"><el-select v-model="form.crop" filterable allow-create style="width:100%"><el-option v-for="o in cropOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="品种"><el-input v-model="form.variety" placeholder="如 吉粳88" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="种植面积(亩)"><el-input-number v-model="form.area" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="数据来源"><el-select v-model="form.dataSource" style="width:100%"><el-option v-for="o in plantingSourceOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="播种日期"><el-date-picker v-model="form.sowDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="实际收获日期"><el-date-picker v-model="form.actualHarvestDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="产量(kg/亩)">
              <el-input-number v-model="form.yieldPerMu" :min="0" :precision="2" style="width:100%" :disabled="harvestedLock" />
              <div v-if="harvestedLock" class="muted">已收获记录产量不可修改</div>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="填报人"><el-input v-model="form.reporter" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24" v-if="form.id"><el-form-item label="附件"><AttachmentPanel biz-type="planting" :biz-id="form.id" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 种植历史 -->
    <el-drawer v-model="historyVisible" :title="`种植历史 · ${historyParcel}`" size="560px">
      <el-empty v-if="!historyRows.length" description="暂无种植记录" />
      <template v-else>
        <el-divider content-position="left">轮作模式</el-divider>
        <div class="rotation">
          <template v-for="(r, i) in historyRows" :key="r.id">
            <el-tag :color="cropColor(r.crop)" effect="dark" style="border:none;color:#fff">{{ r.plantYear }} {{ r.crop }}</el-tag>
            <el-icon v-if="i < historyRows.length - 1" class="arrow"><Right /></el-icon>
          </template>
        </div>
        <div class="muted rotation-tip">{{ rotationTip }}</div>

        <el-divider content-position="left">产量趋势 (kg/亩)</el-divider>
        <div class="trend">
          <div v-for="r in yieldRows" :key="r.id" class="trend-bar">
            <div class="bar" :style="{ height: barHeight(r.yieldPerMu) + 'px' }">
              <span class="bar-val">{{ r.yieldPerMu }}</span>
            </div>
            <div class="bar-label">{{ r.plantYear }}<br />{{ r.crop }}</div>
          </div>
          <el-empty v-if="!yieldRows.length" description="暂无产量数据" :image-size="50" />
        </div>

        <el-divider content-position="left">年度明细</el-divider>
        <el-timeline>
          <el-timeline-item v-for="r in [...historyRows].reverse()" :key="r.id" :timestamp="`${r.plantYear}年 ${seasonDict[r.season]}`" placement="top">
            <div><b>{{ r.crop }}</b> {{ r.variety ? `(${r.variety})` : '' }}
              <el-tag v-if="r.status === 'INVALID'" type="info" size="small" style="margin-left:6px">已无效</el-tag>
            </div>
            <div class="muted">面积 {{ r.area ?? '-' }} 亩 · 产量 {{ r.yieldPerMu ?? '未收获' }} · 来源 {{ plantingSourceDict[r.dataSource] }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Right, Download } from '@element-plus/icons-vue'
import { plantingApi, exportApi } from '../../api'
import ImportButton from '../../components/ImportButton.vue'
import AttachmentPanel from '../../components/AttachmentPanel.vue'
import {
  seasonDict, seasonOptions, plantingSourceDict, plantingSourceOptions,
  plantingStatusDict, cropOptions
} from '../../constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ keyword: '', plantYear: '', crop: '', dataSource: '', page: 1, size: 10 })

const load = async () => {
  loading.value = true
  try {
    const data = await plantingApi.page({
      keyword: query.keyword || undefined,
      plantYear: query.plantYear || undefined,
      crop: query.crop || undefined,
      dataSource: query.dataSource || undefined,
      page: query.page, size: query.size
    })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.page = 1; load() }
const onPage = (p) => { query.page = p; load() }
const onSize = (s) => { query.size = s; query.page = 1; load() }
const onExport = () => exportApi.planting({
  keyword: query.keyword || undefined, plantYear: query.plantYear || undefined,
  crop: query.crop || undefined, dataSource: query.dataSource || undefined
})

// ---- 表单 ----
const formVisible = ref(false)
const formRef = ref()
const blankForm = () => ({
  id: null, parcelCode: '', parcelName: '', plantYear: String(new Date().getFullYear()),
  season: 'SPRING', crop: '', variety: '', area: null, sowDate: '', actualHarvestDate: '',
  yieldPerMu: null, dataSource: 'PATROL', reporter: '', remark: ''
})
const form = reactive(blankForm())
const harvestedLock = ref(false)
const formRules = {
  parcelCode: [{ required: true, message: '请输入地块编码', trigger: 'blur' }],
  plantYear: [{ required: true, message: '请选择种植年度', trigger: 'change' }],
  crop: [{ required: true, message: '请选择作物', trigger: 'change' }]
}
const openForm = (row) => {
  Object.assign(form, blankForm())
  harvestedLock.value = false
  if (row) {
    Object.assign(form, { ...row, plantYear: String(row.plantYear ?? '') })
    harvestedLock.value = !!row.actualHarvestDate   // 已收获锁定产量
  }
  formVisible.value = true
}
const resetForm = () => formRef.value?.clearValidate()
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, plantYear: form.plantYear ? Number(form.plantYear) : null }
      if (form.id) await plantingApi.update(form.id, payload)
      else await plantingApi.create(payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

const onInvalid = async (row) => {
  await ElMessageBox.confirm('标记为无效后将不参与统计，但保留在历史中。是否继续？', '提示', { type: 'warning' })
  await plantingApi.markInvalid(row.id)
  ElMessage.success('已标记为无效')
  load()
}
const onDelete = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt(
      '删除将影响该年度种植统计，建议优先“标记为无效”。请填写删除原因：', '确认删除',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', inputType: 'textarea',
        inputValidator: (v) => (v && v.trim() ? true : '删除原因不能为空') }
    )
    await plantingApi.remove(row.id, value)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* cancel */ }
}

// ---- 种植历史 ----
const historyVisible = ref(false)
const historyParcel = ref('')
const historyRows = ref([])
const openHistory = async (row) => {
  historyParcel.value = `${row.parcelCode} ${row.parcelName}`
  historyRows.value = await plantingApi.history(row.parcelCode)
  historyVisible.value = true
}
const yieldRows = computed(() => historyRows.value.filter((r) => r.yieldPerMu))
const maxYield = computed(() => Math.max(1, ...yieldRows.value.map((r) => Number(r.yieldPerMu))))
const barHeight = (y) => Math.round((Number(y) / maxYield.value) * 110) + 10
const rotationTip = computed(() => {
  const crops = historyRows.value.map((r) => r.crop)
  const uniq = new Set(crops)
  if (crops.length <= 1) return ''
  return uniq.size === 1 ? `连作（${crops.length}年同种 ${crops[0]}），注意土传病害风险` : '轮作，耕地用养结合较好'
})
const cropPalette = { 水稻: '#409eff', 玉米: '#e6a23c', 大豆: '#67c23a', 小麦: '#b88230', 谷子: '#909399' }
const cropColor = (c) => cropPalette[c] || '#2e9e5b'

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.filter-card :deep(.el-card__body) { padding: 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.flex-spacer { flex: 1; }
.table-card :deep(.el-card__body) { padding: 12px 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.muted { color: #909399; font-size: 12px; }
.rotation { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.rotation .arrow { color: #c0c4cc; }
.rotation-tip { margin-top: 8px; }
.trend { display: flex; align-items: flex-end; gap: 18px; padding: 10px 0 0; min-height: 150px; }
.trend-bar { display: flex; flex-direction: column; align-items: center; }
.bar { width: 36px; background: linear-gradient(#67c23a, #2e9e5b); border-radius: 4px 4px 0 0; position: relative; display: flex; justify-content: center; }
.bar-val { position: absolute; top: -18px; font-size: 11px; color: #606266; }
.bar-label { margin-top: 6px; font-size: 11px; color: #909399; text-align: center; }
</style>
