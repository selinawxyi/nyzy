<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="地块编码/地块名/承包方" clearable style="width: 220px" @keyup.enter="onSearch" />
        <el-date-picker v-model="query.evalYear" type="year" placeholder="评价年度" value-format="YYYY" style="width: 130px" />
        <el-select v-model="query.soilType" placeholder="土壤类型" clearable style="width: 120px">
          <el-option v-for="o in soilTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.obstacle" placeholder="障碍因素" clearable style="width: 120px">
          <el-option v-for="o in obstacleOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="lowYield" placeholder="快捷筛选" clearable style="width: 150px" @change="onQuickFilter">
          <el-option label="低产田(≥5等)" value="low" />
          <el-option label="优质田(≤3等)" value="high" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Download" @click="onExport">导出 Excel</el-button>
        <ImportButton type="quality" template-name="耕地质量导入模板.xlsx" @done="load" />
        <el-button :disabled="!selected.length" @click="openBatch">批量修改{{ selected.length ? `(${selected.length})` : '' }}</el-button>
        <div class="flex-spacer" />
        <el-button type="primary" :icon="Plus" @click="openForm()">新增评价</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe @selection-change="onSelect">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="parcelCode" label="地块编码" width="120" />
        <el-table-column prop="parcelName" label="地块名称" min-width="140" />
        <el-table-column prop="contractorName" label="承包方" width="90" />
        <el-table-column prop="evalYear" label="评价年度" width="90" />
        <el-table-column label="地力等级" width="100">
          <template #default="{ row }">
            <el-tag :type="gradeTagType(row.grade)" effect="dark">{{ row.grade }} 等</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="综合得分" width="90" />
        <el-table-column prop="soilType" label="土壤类型" width="90" />
        <el-table-column label="有机质" width="90">
          <template #default="{ row }">{{ row.organicMatter ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="ph" label="pH" width="70" />
        <el-table-column label="障碍因素" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.obstacle" type="warning" size="small" effect="plain">{{ row.obstacle }}</el-tag>
            <span v-else class="muted">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="suitableCrops" label="适宜作物" min-width="130" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
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

    <el-dialog v-model="batchVisible" title="批量修改质量评价" width="460px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        :title="`将对选中的 ${selected.length} 条记录统一修改以下填写的字段（留空的不改）`" />
      <el-form :model="batchForm" label-width="90px">
        <el-form-item label="评价年度"><el-date-picker v-model="batchForm.evalYear" type="year" value-format="YYYY" style="width:100%" /></el-form-item>
        <el-form-item label="地力等级"><el-input-number v-model="batchForm.grade" :min="1" :max="10" style="width:100%" controls-position="right" /></el-form-item>
        <el-form-item label="土壤类型"><el-select v-model="batchForm.soilType" clearable style="width:100%"><el-option v-for="o in soilTypeOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item label="障碍因素"><el-select v-model="batchForm.obstacle" clearable style="width:100%"><el-option v-for="o in obstacleOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item label="评价机构"><el-input v-model="batchForm.org" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBatch">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑质量评价' : '新增质量评价'" width="680px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="地块编码" prop="parcelCode"><el-input v-model="form.parcelCode" placeholder="如 JYB-H001" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地块名称"><el-input v-model="form.parcelName" placeholder="留空自动带出" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="评价年度" prop="evalYear"><el-date-picker v-model="form.evalYear" type="year" value-format="YYYY" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地力等级" prop="grade"><el-input-number v-model="form.grade" :min="1" :max="10" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="综合得分"><el-input-number v-model="form.score" :min="0" :max="100" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="土壤类型"><el-select v-model="form.soilType" filterable allow-create style="width:100%"><el-option v-for="o in soilTypeOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="有机质(g/kg)"><el-input-number v-model="form.organicMatter" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="全氮(g/kg)"><el-input-number v-model="form.totalN" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="有效磷(mg/kg)"><el-input-number v-model="form.availP" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="速效钾(mg/kg)"><el-input-number v-model="form.availK" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="pH值"><el-input-number v-model="form.ph" :min="0" :max="14" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="障碍因素"><el-select v-model="form.obstacle" clearable filterable allow-create style="width:100%"><el-option v-for="o in obstacleOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="适宜作物"><el-input v-model="form.suitableCrops" placeholder="如 水稻、玉米" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="评价机构"><el-input v-model="form.org" /></el-form-item></el-col>
          <el-col :span="24" v-if="form.id"><el-form-item label="报告附件"><AttachmentPanel biz-type="quality" :biz-id="form.id" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download } from '@element-plus/icons-vue'
import { qualityApi, exportApi } from '../../api'
import ImportButton from '../../components/ImportButton.vue'
import AttachmentPanel from '../../components/AttachmentPanel.vue'
import { soilTypeOptions, obstacleOptions, gradeTagType } from '../../constants/dict'
import { confirmBatchUpdate } from '../../utils/batchPreview'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const lowYield = ref('')
const query = reactive({ keyword: '', evalYear: '', soilType: '', obstacle: '', gradeMin: undefined, gradeMax: undefined, page: 1, size: 10 })

const load = async () => {
  loading.value = true
  try {
    const data = await qualityApi.page({
      keyword: query.keyword || undefined,
      evalYear: query.evalYear || undefined,
      soilType: query.soilType || undefined,
      obstacle: query.obstacle || undefined,
      gradeMin: query.gradeMin, gradeMax: query.gradeMax,
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
const onQuickFilter = (v) => {
  query.gradeMin = v === 'low' ? 5 : undefined
  query.gradeMax = v === 'high' ? 3 : undefined
  onSearch()
}
const selected = ref([])
const onSelect = (rows) => { selected.value = rows }
const batchVisible = ref(false)
const batchForm = reactive({ evalYear: '', grade: null, soilType: '', obstacle: '', org: '' })
const openBatch = () => {
  Object.assign(batchForm, { evalYear: '', grade: null, soilType: '', obstacle: '', org: '' })
  batchVisible.value = true
}
const batchFieldLabels = { evalYear: '评价年度', grade: '地力等级', soilType: '土壤类型', obstacle: '障碍因素', org: '评价机构' }
const submitBatch = async () => {
  const updates = {
    evalYear: batchForm.evalYear ? Number(batchForm.evalYear) : null,
    grade: batchForm.grade, soilType: batchForm.soilType || null,
    obstacle: batchForm.obstacle || null, org: batchForm.org || null
  }
  try {
    await confirmBatchUpdate(selected.value.length, updates, batchFieldLabels)
  } catch (e) {
    if (e?.message) ElMessage.error(e.message)
    return
  }
  saving.value = true
  try {
    const n = await qualityApi.batch(selected.value.map((r) => r.id), updates)
    ElMessage.success(`已修改 ${n} 条`)
    batchVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}
const onExport = () => exportApi.quality({
  keyword: query.keyword || undefined, evalYear: query.evalYear || undefined,
  soilType: query.soilType || undefined, obstacle: query.obstacle || undefined,
  gradeMin: query.gradeMin, gradeMax: query.gradeMax
})

const formVisible = ref(false)
const formRef = ref()
const blankForm = () => ({
  id: null, parcelCode: '', parcelName: '', evalYear: String(new Date().getFullYear()),
  grade: 3, score: null, soilType: '', organicMatter: null, totalN: null, availP: null,
  availK: null, ph: null, obstacle: '', suitableCrops: '', org: '延边州耕地质量监测站'
})
const form = reactive(blankForm())
const formRules = {
  parcelCode: [{ required: true, message: '请输入地块编码', trigger: 'blur' }],
  evalYear: [{ required: true, message: '请选择评价年度', trigger: 'change' }],
  grade: [{ required: true, message: '请输入地力等级', trigger: 'blur' }]
}
const openForm = (row) => {
  Object.assign(form, blankForm())
  if (row) Object.assign(form, { ...row, evalYear: String(row.evalYear ?? '') })
  formVisible.value = true
}
const resetForm = () => formRef.value?.clearValidate()
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, evalYear: form.evalYear ? Number(form.evalYear) : null }
      if (form.id) await qualityApi.update(form.id, payload)
      else await qualityApi.create(payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}
const onDelete = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt(
      '质量评价为历史重要数据，仅管理员可删除。请填写删除原因：', '确认删除',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', inputType: 'textarea',
        inputValidator: (v) => (v && v.trim() ? true : '删除原因不能为空') }
    )
    await qualityApi.remove(row.id, value)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* cancel */ }
}

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
</style>
