<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="设施名称/管护责任人" clearable style="width: 220px" @keyup.enter="onSearch" />
        <el-select v-model="query.type" placeholder="设施类型" clearable style="width: 130px">
          <el-option v-for="o in waterTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.runStatus" placeholder="运行状态" clearable style="width: 120px">
          <el-option v-for="o in runStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.auditStatus" placeholder="审核状态" clearable style="width: 120px">
          <el-option v-for="(v, k) in auditStatusDict" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Download" @click="onExport">导出 Excel</el-button>
        <ImportButton type="water" template-name="水利设施导入模板.xlsx" @done="load" />
        <el-button :disabled="!selected.length" @click="openBatch">批量修改{{ selected.length ? `(${selected.length})` : '' }}</el-button>
        <div class="flex-spacer" />
        <el-button type="primary" :icon="Plus" @click="openForm()">标注水利设施</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe @selection-change="onSelect">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="name" label="设施名称" min-width="150" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="regionPath" label="所在位置" min-width="180" show-overflow-tooltip />
        <el-table-column prop="buildYear" label="建设年份" width="90" />
        <el-table-column label="覆盖面积(亩)" width="110">
          <template #default="{ row }">{{ row.coverArea ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="运行状态" width="90">
          <template #default="{ row }">
            <el-tag :type="runStatusDict[row.runStatus] || 'info'" size="small" effect="light">{{ row.runStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="manager" label="管护责任人" width="100" />
        <el-table-column label="审核" width="90">
          <template #default="{ row }">
            <el-tag :type="auditStatusDict[row.auditStatus]?.type" size="small">{{ auditStatusDict[row.auditStatus]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.auditStatus === 'PENDING'" link type="success" size="small" @click="onAudit(row, true)">通过</el-button>
            <el-button v-if="row.auditStatus === 'PENDING'" link type="warning" size="small" @click="onAudit(row, false)">退回</el-button>
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

    <el-dialog v-model="batchVisible" title="批量修改水利设施" width="440px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        :title="`将对选中的 ${selected.length} 个设施统一修改以下字段（留空不改）`" />
      <el-form :model="batchForm" label-width="90px">
        <el-form-item label="运行状态"><el-select v-model="batchForm.runStatus" clearable style="width:100%"><el-option v-for="o in runStatusOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
        <el-form-item label="管护责任人"><el-input v-model="batchForm.manager" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="batchForm.phone" /></el-form-item>
        <el-form-item label="最近维护"><el-date-picker v-model="batchForm.lastMaintainDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBatch">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑水利设施' : '标注水利设施'" width="680px" @closed="resetForm">
      <el-alert v-if="!form.id" type="info" :closable="false" show-icon style="margin-bottom:14px"
        title="新标注的设施需经运营人员审核通过后，前端用户方可见" />
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="设施名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="设施类型" prop="type"><el-select v-model="form.type" style="width:100%"><el-option v-for="o in waterTypeOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="选择区划"><RegionCascader @change="onRegionChange" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="所在位置"><el-input v-model="form.regionPath" placeholder="可手填，或左侧选择" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="经度"><el-input-number v-model="form.lng" :precision="6" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="纬度"><el-input-number v-model="form.lat" :precision="6" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="建设年份"><el-date-picker v-model="form.buildYear" type="year" value-format="YYYY" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="覆盖面积(亩)"><el-input-number v-model="form.coverArea" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="运行状态"><el-select v-model="form.runStatus" style="width:100%"><el-option v-for="o in runStatusOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="受益村组"><el-input v-model="form.benefitVillages" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="管护责任人"><el-input v-model="form.manager" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="技术参数"><el-input v-model="form.techParams" placeholder="如 井深85米, 出水量50m³/h" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24" v-if="form.id"><el-form-item label="附件"><AttachmentPanel biz-type="water" :biz-id="form.id" /></el-form-item></el-col>
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
import { waterApi, exportApi } from '../../api'
import RegionCascader from '../../components/RegionCascader.vue'
import ImportButton from '../../components/ImportButton.vue'
import AttachmentPanel from '../../components/AttachmentPanel.vue'
import { waterTypeOptions, runStatusDict, runStatusOptions, auditStatusDict } from '../../constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ keyword: '', type: '', runStatus: '', auditStatus: '', page: 1, size: 10 })

const load = async () => {
  loading.value = true
  try {
    const data = await waterApi.page({
      keyword: query.keyword || undefined, type: query.type || undefined,
      runStatus: query.runStatus || undefined, auditStatus: query.auditStatus || undefined,
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
const onExport = () => exportApi.water({
  keyword: query.keyword || undefined, type: query.type || undefined,
  runStatus: query.runStatus || undefined, auditStatus: query.auditStatus || undefined
})

const selected = ref([])
const onSelect = (rows) => { selected.value = rows }
const batchVisible = ref(false)
const batchForm = reactive({ runStatus: '', manager: '', phone: '', lastMaintainDate: '' })
const openBatch = () => {
  Object.assign(batchForm, { runStatus: '', manager: '', phone: '', lastMaintainDate: '' })
  batchVisible.value = true
}
const submitBatch = async () => {
  saving.value = true
  try {
    const updates = {
      runStatus: batchForm.runStatus || null, manager: batchForm.manager || null,
      phone: batchForm.phone || null, lastMaintainDate: batchForm.lastMaintainDate || null
    }
    const n = await waterApi.batch(selected.value.map((r) => r.id), updates)
    ElMessage.success(`已修改 ${n} 个设施`)
    batchVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const formVisible = ref(false)
const formRef = ref()
const blankForm = () => ({
  id: null, name: '', type: '', regionId: null, regionPath: '', lng: null, lat: null, buildYear: '',
  coverArea: null, runStatus: '正常', benefitVillages: '', manager: '', phone: '', techParams: '', remark: ''
})
const form = reactive(blankForm())
const formRules = {
  name: [{ required: true, message: '请输入设施名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择设施类型', trigger: 'change' }]
}
const openForm = (row) => {
  Object.assign(form, blankForm())
  if (row) Object.assign(form, { ...row, buildYear: row.buildYear ? String(row.buildYear) : '' })
  formVisible.value = true
}
const resetForm = () => formRef.value?.clearValidate()
const onRegionChange = ({ regionId, regionPath }) => {
  form.regionId = regionId
  if (regionPath) form.regionPath = regionPath
}
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, buildYear: form.buildYear ? Number(form.buildYear) : null }
      if (form.id) await waterApi.update(form.id, payload)
      else await waterApi.create(payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}
const onAudit = async (row, pass) => {
  await ElMessageBox.confirm(`确认${pass ? '通过' : '退回'}该设施的标注审核？`, '审核', { type: pass ? 'success' : 'warning' })
  await waterApi.audit(row.id, pass)
  ElMessage.success(pass ? '已通过' : '已退回')
  load()
}
const onDelete = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('删除后进入回收站（保留90天），请填写删除原因：', '确认删除',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', inputType: 'textarea',
        inputPlaceholder: '如：设施已拆除 / 数据重复录入 / 位置信息错误',
        inputValidator: (v) => (v && v.trim() ? true : '删除原因不能为空') })
    await waterApi.remove(row.id, value)
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
</style>
