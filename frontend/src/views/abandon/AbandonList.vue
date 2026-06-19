<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="地块编码/地块名/上报人"
          clearable
          style="width: 240px"
          @keyup.enter="onSearch"
        />
        <el-select v-model="query.governStatus" placeholder="治理状态" clearable style="width: 140px">
          <el-option v-for="o in governStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker
          v-model="query.abandonYear"
          type="year"
          placeholder="撂荒年份"
          value-format="YYYY"
          style="width: 140px"
        />
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Download" @click="onExport">导出 Excel</el-button>
        <el-button :icon="DataAnalysis" @click="onStats">统计分析</el-button>
        <el-button :icon="Download" @click="onExportTask">治理台账</el-button>
        <div class="flex-spacer" />
        <el-button type="primary" :icon="Plus" @click="openForm()">新增上报</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column type="selection" width="46" />
        <el-table-column prop="parcelCode" label="地块编码" width="120" />
        <el-table-column label="地块名称" min-width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.parcelName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="abandonYear" label="年份" width="80" />
        <el-table-column label="面积(亩)" width="100">
          <template #default="{ row }">{{ row.area ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="reasonText" label="撂荒原因" width="130" />
        <el-table-column prop="reporter" label="上报人" width="130" />
        <el-table-column prop="foundDate" label="发现日期" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="govStatus(row.governStatus).type" effect="light">
              {{ govStatus(row.governStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openTask(row)">任务</el-button>
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="query.page"
          :page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onPage"
          @size-change="onSize"
        />
      </div>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="formVisible" :title="formTitle" width="640px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="地块编码" prop="parcelCode">
              <el-input v-model="form.parcelCode" placeholder="如 JYA-F002" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地块名称" prop="parcelName">
              <el-input v-model="form.parcelName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="撂荒年份" prop="abandonYear">
              <el-date-picker v-model="form.abandonYear" type="year" value-format="YYYY" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="撂荒面积(亩)">
              <el-input-number v-model="form.area" :min="0" :precision="2" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="撂荒程度">
              <el-select v-model="form.degree" style="width:100%">
                <el-option v-for="o in degreeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来源">
              <el-select v-model="form.source" style="width:100%">
                <el-option v-for="o in sourceOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="撂荒原因">
              <el-select v-model="form.reasonType" style="width:100%" @change="onReasonChange">
                <el-option v-for="o in reasonOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原因说明">
              <el-input v-model="form.reasonText" placeholder="如 土地贫瘠" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发现日期">
              <el-date-picker v-model="form.foundDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上报人">
              <el-input v-model="form.reporter" />
            </el-form-item>
          </el-col>
          <el-col :span="12" v-if="form.id">
            <el-form-item label="治理状态">
              <el-tag :type="govStatus(form.governStatus).type">{{ govStatus(form.governStatus).label }}</el-tag>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 下发治理任务 -->
    <el-dialog v-model="taskVisible" title="下发治理任务" width="560px">
      <el-form ref="taskRef" :model="taskForm" :rules="taskRules" label-width="110px">
        <el-form-item label="撂荒地块">
          <span>{{ taskForm._parcel }}</span>
        </el-form-item>
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="taskForm.name" />
        </el-form-item>
        <el-form-item label="责任单位" prop="respUnit">
          <el-input v-model="taskForm.respUnit" placeholder="如 太平镇人民政府" />
        </el-form-item>
        <el-form-item label="责任人" prop="respPerson">
          <el-input v-model="taskForm.respPerson" />
        </el-form-item>
        <el-form-item label="治理面积目标">
          <el-input-number v-model="taskForm.targetArea" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="治理标准">
          <el-input v-model="taskForm.standard" placeholder="如 恢复耕种, 种植一季粮食作物" />
        </el-form-item>
        <el-form-item label="完成时限">
          <el-date-picker v-model="taskForm.deadline" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTask">下发</el-button>
      </template>
    </el-dialog>

    <!-- 治理验收 -->
    <el-dialog v-model="acceptVisible" title="治理任务验收" width="480px">
      <el-form :model="acceptForm" label-width="110px">
        <el-form-item label="验收结论">
          <el-radio-group v-model="acceptForm.pass">
            <el-radio-button :value="true">通过</el-radio-button>
            <el-radio-button :value="false">退回整改</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="acceptForm.pass">
          <el-alert type="success" :closable="false" show-icon style="margin-bottom:12px"
            title="验收通过后，地块将转为「已治理」，并可回写治理后的种植记录" />
          <el-form-item label="治理后作物">
            <el-select v-model="acceptForm.crop" clearable filterable allow-create style="width:100%" placeholder="选填，填写则回写种植">
              <el-option v-for="o in cropOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="种植面积(亩)">
            <el-input-number v-model="acceptForm.area" :min="0" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="种植年度">
            <el-date-picker v-model="acceptForm.year" type="year" value-format="YYYY" style="width:100%" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="acceptVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAccept">提交验收</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detail.parcel?.parcelName || '撂荒详情'" size="520px">
      <template v-if="detail.parcel">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="地块编码">{{ detail.parcel.parcelCode }}</el-descriptions-item>
          <el-descriptions-item label="撂荒年份">{{ detail.parcel.abandonYear }}</el-descriptions-item>
          <el-descriptions-item label="撂荒面积">{{ detail.parcel.area }} 亩</el-descriptions-item>
          <el-descriptions-item label="撂荒程度">{{ degreeDict[detail.parcel.degree] }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceDict[detail.parcel.source] }}</el-descriptions-item>
          <el-descriptions-item label="治理状态">
            <el-tag :type="govStatus(detail.parcel.governStatus).type">{{ govStatus(detail.parcel.governStatus).label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="坐落位置" :span="2">{{ detail.parcel.regionPath }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.parcel.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="drawer-actions">
          <span class="drawer-label">治理状态流转：</span>
          <el-button
            v-for="t in nextStatuses(detail.parcel.governStatus)"
            :key="t"
            size="small"
            @click="changeStatus(detail.parcel.id, t)"
          >{{ govStatus(t).label }}</el-button>
          <span v-if="!nextStatuses(detail.parcel.governStatus).length" class="muted">（无可流转状态）</span>
        </div>

        <el-divider content-position="left">撂荒原因填报</el-divider>
        <el-empty v-if="!detail.reasons?.length" description="暂无原因填报" :image-size="60" />
        <div v-for="r in detail.reasons" :key="r.id" class="sub-item">
          <div><b>{{ (r.reasonTypes || '').split(',').map(x => reasonDict[x] || x).join('、') }}</b></div>
          <div class="muted">{{ r.detail }}</div>
          <div class="muted">建议：{{ r.suggestion }}</div>
        </div>

        <el-divider content-position="left">现场照片 / 附件</el-divider>
        <AttachmentPanel biz-type="abandon" :biz-id="detail.parcel.id" />

        <el-divider content-position="left">治理任务</el-divider>
        <el-empty v-if="!detail.tasks?.length" description="暂无治理任务" :image-size="60" />
        <div v-for="t in detail.tasks" :key="t.id" class="sub-item">
          <div>
            <b>{{ t.name }}</b>
            <el-tag size="small" :type="taskStatusDict[t.taskStatus]?.type" style="margin-left:8px">
              {{ taskStatusDict[t.taskStatus]?.label }}
            </el-tag>
          </div>
          <div class="muted">责任单位：{{ t.respUnit }} · 责任人：{{ t.respPerson }}</div>
          <div v-if="t.plan" class="muted">治理方案：{{ t.plan }}</div>
          <el-progress :percentage="t.progress" :stroke-width="10" style="margin-top:6px" />
          <div class="task-ops" v-if="t.taskStatus !== 'DONE'">
            <el-button link type="primary" size="small" @click="onPlan(t)">{{ t.plan ? '修改方案' : '填写方案' }}</el-button>
            <el-button link type="primary" size="small" @click="onProgress(t)">填报进度</el-button>
            <el-button link type="warning" size="small" @click="onFeedback(t)">问题反馈</el-button>
            <el-button link type="success" size="small" @click="openAccept(t)">验收</el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download, DataAnalysis, Plus } from '@element-plus/icons-vue'
import { abandonApi, exportApi } from '../../api'
import AttachmentPanel from '../../components/AttachmentPanel.vue'
import {
  governStatusDict, governStatusOptions, sourceDict, sourceOptions,
  degreeDict, degreeOptions, reasonDict, reasonOptions, taskStatusDict, cropOptions
} from '../../constants/dict'

const TRANSITIONS = {
  PENDING: ['UNGOVERNED', 'REJECTED'],
  UNGOVERNED: ['GOVERNING'],
  GOVERNING: ['GOVERNED', 'UNGOVERNED'],
  GOVERNED: ['GOVERNING'],
  REJECTED: []
}
const nextStatuses = (s) => TRANSITIONS[s] || []
const govStatus = (s) => governStatusDict[s] || { label: s, type: 'info' }

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ keyword: '', governStatus: '', abandonYear: '', page: 1, size: 10 })

const load = async () => {
  loading.value = true
  try {
    const data = await abandonApi.page({
      keyword: query.keyword || undefined,
      governStatus: query.governStatus || undefined,
      abandonYear: query.abandonYear || undefined,
      page: query.page,
      size: query.size
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
const onExport = () => exportApi.abandon({
  keyword: query.keyword || undefined,
  governStatus: query.governStatus || undefined,
  abandonYear: query.abandonYear || undefined
})
const onStats = () => ElMessage.info('统计分析：见「分析」菜单的种植动态分析')
const onExportTask = () => exportApi.abandonTask({})

// ---- 新增/编辑 ----
const formVisible = ref(false)
const formRef = ref()
const blankForm = () => ({
  id: null, parcelCode: '', parcelName: '', abandonYear: String(new Date().getFullYear()),
  area: null, degree: 'FULL', source: 'PATROL', reasonType: '', reasonText: '',
  foundDate: new Date().toISOString().slice(0, 10), reporter: '', remark: '', governStatus: 'UNGOVERNED'
})
const form = reactive(blankForm())
const formTitle = computed(() => (form.id ? '编辑撂荒地块' : '新增撂荒上报'))
const formRules = {
  parcelCode: [{ required: true, message: '请输入地块编码', trigger: 'blur' }],
  parcelName: [{ required: true, message: '请输入地块名称', trigger: 'blur' }],
  abandonYear: [{ required: true, message: '请选择撂荒年份', trigger: 'change' }]
}
const openForm = (row) => {
  Object.assign(form, blankForm())
  if (row) Object.assign(form, { ...row, abandonYear: String(row.abandonYear ?? '') })
  formVisible.value = true
}
const resetForm = () => { formRef.value?.clearValidate() }
const onReasonChange = (v) => { if (!form.reasonText) form.reasonText = reasonDict[v] }
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, abandonYear: form.abandonYear ? Number(form.abandonYear) : null }
      if (form.id) await abandonApi.update(form.id, payload)
      else await abandonApi.create(payload)
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

// ---- 删除（必填原因，软删除）----
const onDelete = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt(
      '删除后数据进入回收站（保留90天），请填写删除原因：', '确认删除',
      {
        confirmButtonText: '确认删除', cancelButtonText: '取消', inputType: 'textarea',
        inputPlaceholder: '如：误判 / 重复录入 / 关联错误',
        inputValidator: (v) => (v && v.trim() ? true : '删除原因不能为空')
      }
    )
    await abandonApi.remove(row.id, value)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* 取消 */ }
}

// ---- 治理任务 ----
const taskVisible = ref(false)
const taskRef = ref()
const taskForm = reactive({})
const taskRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  respUnit: [{ required: true, message: '请输入责任单位', trigger: 'blur' }],
  respPerson: [{ required: true, message: '请输入责任人', trigger: 'blur' }]
}
const openTask = (row) => {
  Object.assign(taskForm, {
    _abandonId: row.id, _parcel: `${row.parcelCode} ${row.parcelName}`,
    name: `${row.parcelName}撂荒地治理任务${new Date().toISOString().slice(0, 10).replace(/-/g, '')}`,
    respUnit: '', respPerson: row.manager || '', targetArea: row.area, standard: '恢复耕种, 种植一季粮食作物', deadline: ''
  })
  taskVisible.value = true
}
const submitTask = () => {
  taskRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await abandonApi.createTask(taskForm._abandonId, {
        name: taskForm.name, respUnit: taskForm.respUnit, respPerson: taskForm.respPerson,
        targetArea: taskForm.targetArea, standard: taskForm.standard, deadline: taskForm.deadline || null
      })
      ElMessage.success('任务已下发')
      taskVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}

// ---- 详情 ----
const detailVisible = ref(false)
const detail = reactive({ parcel: null, reasons: [], tasks: [] })
const openDetail = async (row) => {
  const d = await abandonApi.detail(row.id)
  Object.assign(detail, d)
  detailVisible.value = true
}
const changeStatus = async (id, target) => {
  await abandonApi.changeStatus(id, { governStatus: target })
  ElMessage.success('状态已更新')
  const d = await abandonApi.detail(id)
  Object.assign(detail, d)
  load()
}

const refreshDetail = async () => {
  const d = await abandonApi.detail(detail.parcel.id)
  Object.assign(detail, d)
  load()
}
const onPlan = async (t) => {
  try {
    const { value } = await ElMessageBox.prompt('填写治理方案（思路/措施/步骤）：', '治理方案',
      { confirmButtonText: '提交', cancelButtonText: '取消', inputType: 'textarea', inputValue: t.plan || '',
        inputValidator: (v) => (v && v.trim() ? true : '方案不能为空') })
    await abandonApi.taskPlan(t.id, value)
    ElMessage.success('方案已保存')
    refreshDetail()
  } catch (e) { /* cancel */ }
}
const onFeedback = async (t) => {
  try {
    const { value } = await ElMessageBox.prompt('反馈办理中遇到的问题（将通知上级协调）：', '问题反馈',
      { confirmButtonText: '提交', cancelButtonText: '取消', inputType: 'textarea',
        inputValidator: (v) => (v && v.trim() ? true : '反馈内容不能为空') })
    await abandonApi.taskFeedback(t.id, value)
    ElMessage.success('反馈已提交')
  } catch (e) { /* cancel */ }
}
const onProgress = async (t) => {
  try {
    const { value } = await ElMessageBox.prompt('填报治理进度百分比（0-100）：', '进度填报',
      { confirmButtonText: '提交', cancelButtonText: '取消', inputValue: String(t.progress),
        inputValidator: (v) => (/^\d+$/.test(v) && +v >= 0 && +v <= 100 ? true : '请输入 0-100 的整数') })
    await abandonApi.taskProgress(t.id, Number(value))
    ElMessage.success('进度已更新')
    refreshDetail()
  } catch (e) { /* cancel */ }
}
const acceptVisible = ref(false)
const acceptForm = reactive({ taskId: null, pass: true, crop: '', area: null, year: String(new Date().getFullYear()) })
const openAccept = (t) => {
  Object.assign(acceptForm, { taskId: t.id, pass: true, crop: '', area: t.targetArea, year: String(new Date().getFullYear()) })
  acceptVisible.value = true
}
const submitAccept = async () => {
  saving.value = true
  try {
    await abandonApi.taskAccept(acceptForm.taskId, {
      pass: acceptForm.pass,
      crop: acceptForm.pass ? (acceptForm.crop || null) : null,
      area: acceptForm.pass ? acceptForm.area : null,
      year: acceptForm.pass && acceptForm.year ? Number(acceptForm.year) : null
    })
    ElMessage.success('验收已提交')
    acceptVisible.value = false
    refreshDetail()
  } finally {
    saving.value = false
  }
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
.drawer-actions { margin: 16px 0 4px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.drawer-label { font-size: 13px; color: #606266; }
.sub-item { padding: 10px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
.task-ops { margin-top: 6px; }
.muted { color: #909399; font-size: 12px; margin-top: 2px; }
</style>
