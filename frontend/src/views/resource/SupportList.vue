<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="设施名称" clearable style="width: 200px" @keyup.enter="onSearch" />
        <el-select v-model="query.categoryId" placeholder="设施分类" clearable style="width: 160px">
          <el-option v-for="o in leaves" :key="o.id" :label="o.name" :value="o.id" />
        </el-select>
        <el-select v-model="query.operateStatus" placeholder="运营状态" clearable style="width: 120px">
          <el-option v-for="o in operateStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.operateSubject" placeholder="运营主体" clearable style="width: 120px">
          <el-option v-for="o in operateSubjectOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Download" @click="onExport">导出 Excel</el-button>
        <ImportButton type="support" template-name="配套设施导入模板.xlsx" @done="load" />
        <div class="flex-spacer" />
        <el-button type="primary" :icon="Plus" @click="openForm()">标注配套设施</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="name" label="设施名称" min-width="160" />
        <el-table-column prop="categoryName" label="分类" width="110" />
        <el-table-column prop="regionPath" label="所在位置" min-width="170" show-overflow-tooltip />
        <el-table-column prop="serviceAbility" label="服务能力" min-width="160" show-overflow-tooltip />
        <el-table-column label="运营状态" width="90">
          <template #default="{ row }">
            <el-tag :type="operateStatusDict[row.operateStatus] || 'info'" size="small" effect="light">{{ row.operateStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operateSubject" label="运营主体" width="90" />
        <el-table-column prop="qualification" label="资质认证" width="120" />
        <el-table-column label="审核" width="84">
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

    <el-dialog v-model="formVisible" :title="form.id ? '编辑配套设施' : '标注配套设施'" width="680px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="设施名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="设施分类" prop="categoryId"><el-select v-model="form.categoryId" style="width:100%"><el-option v-for="o in leaves" :key="o.id" :label="o.name" :value="o.id" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="选择区划"><RegionCascader @change="onRegionChange" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="所在位置"><el-input v-model="form.regionPath" placeholder="可手填，或左侧选择" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="经度"><el-input-number v-model="form.lng" :precision="6" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="纬度"><el-input-number v-model="form.lat" :precision="6" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="服务范围"><el-input v-model="form.serviceRange" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="服务能力"><el-input v-model="form.serviceAbility" placeholder="如 日处理能力120吨, 热风烘干" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="运营状态"><el-select v-model="form.operateStatus" style="width:100%"><el-option v-for="o in operateStatusOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="运营主体"><el-select v-model="form.operateSubject" clearable style="width:100%"><el-option v-for="o in operateSubjectOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="营业时间"><el-input v-model="form.businessHours" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="资质认证"><el-input v-model="form.qualification" placeholder="如 绿色食品认证 / HACCP认证" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
          <el-col :span="24" v-if="form.id"><el-form-item label="附件"><AttachmentPanel biz-type="support" :biz-id="form.id" /></el-form-item></el-col>
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
import { supportApi, categoryApi, exportApi } from '../../api'
import RegionCascader from '../../components/RegionCascader.vue'
import AttachmentPanel from '../../components/AttachmentPanel.vue'
import ImportButton from '../../components/ImportButton.vue'
import { operateStatusDict, operateStatusOptions, operateSubjectOptions, auditStatusDict } from '../../constants/dict'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const leaves = ref([])
const query = reactive({ keyword: '', categoryId: '', operateStatus: '', operateSubject: '', page: 1, size: 10 })

const load = async () => {
  loading.value = true
  try {
    const data = await supportApi.page({
      keyword: query.keyword || undefined, categoryId: query.categoryId || undefined,
      operateStatus: query.operateStatus || undefined, operateSubject: query.operateSubject || undefined,
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
const onExport = () => exportApi.support({
  keyword: query.keyword || undefined, categoryId: query.categoryId || undefined,
  operateStatus: query.operateStatus || undefined, operateSubject: query.operateSubject || undefined
})

const formVisible = ref(false)
const formRef = ref()
const blankForm = () => ({
  id: null, name: '', categoryId: '', regionId: null, regionPath: '', lng: null, lat: null, serviceRange: '',
  serviceAbility: '', operateStatus: '正常', operateSubject: '', phone: '', businessHours: '', qualification: '', remark: ''
})
const form = reactive(blankForm())
const formRules = {
  name: [{ required: true, message: '请输入设施名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择设施分类', trigger: 'change' }]
}
const openForm = (row) => {
  Object.assign(form, blankForm())
  if (row) Object.assign(form, { ...row })
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
      if (form.id) await supportApi.update(form.id, form)
      else await supportApi.create(form)
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
  await supportApi.audit(row.id, pass)
  ElMessage.success(pass ? '已通过' : '已退回')
  load()
}
const onDelete = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt('删除后进入回收站（保留90天），请填写删除原因：', '确认删除',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', inputType: 'textarea',
        inputValidator: (v) => (v && v.trim() ? true : '删除原因不能为空') })
    await supportApi.remove(row.id, value)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* cancel */ }
}

onMounted(async () => {
  leaves.value = await categoryApi.leaves()
  load()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.filter-card :deep(.el-card__body) { padding: 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.flex-spacer { flex: 1; }
.table-card :deep(.el-card__body) { padding: 12px 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
