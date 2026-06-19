<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="地块编码/名称/承包方" clearable style="width: 220px" @keyup.enter="onSearch" />
        <el-select v-model="query.landUse" placeholder="地块用途" clearable style="width: 130px">
          <el-option v-for="o in landUseOptions" :key="o" :label="o" :value="o" />
        </el-select>
        <el-input-number v-model="query.areaMin" placeholder="面积≥" :min="0" :controls="false" style="width: 100px" />
        <span class="sep">~</span>
        <el-input-number v-model="query.areaMax" placeholder="面积≤" :min="0" :controls="false" style="width: 100px" />
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <el-button :icon="Download" @click="onExport">导出 Excel</el-button>
        <ImportButton type="parcel" template-name="确权地块导入模板.xlsx" @done="load" />
        <ImportButton type="parcel-update" template-name="确权地块批量更新模板.xlsx" @done="load" />
        <div class="flex-spacer" />
        <el-button type="primary" :icon="Plus" @click="openForm()">新增地块</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="parcelCode" label="地块编码" width="120" />
        <el-table-column label="地块名称" min-width="150">
          <template #default="{ row }"><el-link type="primary" @click="openDetail(row)">{{ row.name }}</el-link></template>
        </el-table-column>
        <el-table-column prop="regionPath" label="坐落位置" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contractorName" label="承包方" width="90" />
        <el-table-column label="确权面积(亩)" width="110">
          <template #default="{ row }">{{ row.area ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="landUse" label="地块用途" width="100" />
        <el-table-column label="承包期限" width="180">
          <template #default="{ row }">{{ row.contractStart }} ~ {{ row.contractEnd }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
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
    <el-dialog v-model="formVisible" :title="form.id ? '编辑确权地块' : '新增确权地块'" width="700px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="地块编码" prop="parcelCode"><el-input v-model="form.parcelCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地块名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="选择区划"><RegionCascader @change="onRegionChange" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="坐落位置"><el-input v-model="form.regionPath" placeholder="可手填，或左侧选择后自动带出" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="承包方姓名"><el-input v-model="form.contractorName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="承包方编码"><el-input v-model="form.contractorCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="确权面积(亩)"><el-input-number v-model="form.area" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地块用途"><el-select v-model="form.landUse" filterable allow-create style="width:100%"><el-option v-for="o in landUseOptions" :key="o" :label="o" :value="o" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="承包起始"><el-date-picker v-model="form.contractStart" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="承包终止"><el-date-picker v-model="form.contractEnd" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="东至"><el-input v-model="form.boundEast" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="南至" label-width="50px"><el-input v-model="form.boundSouth" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="西至" label-width="50px"><el-input v-model="form.boundWest" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="北至" label-width="50px"><el-input v-model="form.boundNorth" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉: 信息卡 + 版本历史 + 标注 -->
    <el-drawer v-model="detailVisible" :title="detail?.name || '地块详情'" size="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="地块编码">{{ detail.parcelCode }}</el-descriptions-item>
          <el-descriptions-item label="承包方">{{ detail.contractorName }}</el-descriptions-item>
          <el-descriptions-item label="确权面积">{{ detail.area }} 亩</el-descriptions-item>
          <el-descriptions-item label="地块用途">{{ detail.landUse }}</el-descriptions-item>
          <el-descriptions-item label="坐落位置" :span="2">{{ detail.regionPath }}</el-descriptions-item>
          <el-descriptions-item label="承包期限" :span="2">{{ detail.contractStart }} ~ {{ detail.contractEnd }}</el-descriptions-item>
          <el-descriptions-item label="四至" :span="2">
            东至{{ detail.boundEast || '-' }}，南至{{ detail.boundSouth || '-' }}，西至{{ detail.boundWest || '-' }}，北至{{ detail.boundNorth || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">
          地块标注
          <el-button link type="primary" size="small" @click="openAnno">+ 添加</el-button>
        </el-divider>
        <el-empty v-if="!annotations.length" description="暂无标注" :image-size="50" />
        <div v-for="a in annotations" :key="a.id" class="anno-item">
          <span class="anno-dot" :style="{ background: colorMap[a.color] || '#909399' }"></span>
          <div class="anno-body">
            <div v-if="a.content">{{ a.content }}</div>
            <div v-if="a.tags" class="anno-tags">
              <el-tag v-for="t in a.tags.split(',')" :key="t" size="small" effect="plain" style="margin-right:4px">{{ t }}</el-tag>
            </div>
            <div class="muted">{{ a.ownerName }} · {{ a.visibleScope === 'ALL' ? '所有人可见' : '仅自己可见' }}</div>
          </div>
          <el-button link type="danger" size="small" @click="removeAnno(a)">删除</el-button>
        </div>

        <el-divider content-position="left">地块附件（测绘/合同/照片）</el-divider>
        <AttachmentPanel biz-type="parcel" :biz-id="detail.id" />

        <el-divider content-position="left">
          变更历史（勾选两个版本对比）
          <el-button link type="primary" size="small" :disabled="compareSel.length !== 2" @click="doCompare">对比所选</el-button>
        </el-divider>
        <el-empty v-if="!history.length" description="暂无变更记录" :image-size="50" />
        <el-checkbox-group v-else v-model="compareSel" :max="2">
          <el-timeline>
            <el-timeline-item v-for="h in history" :key="h.id"
              :timestamp="h.createdAt" placement="top"
              :type="h.changeType === 'CREATE' ? 'success' : h.changeType === 'DELETE' ? 'danger' : 'primary'">
              <el-checkbox :value="h.id" style="margin-right:6px"><b>v{{ h.version }}</b></el-checkbox>
              · {{ changeTypeText[h.changeType] }} · {{ h.changeFields }}
              <div class="muted">操作人：{{ h.operator }} · 原因：{{ h.reason }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-checkbox-group>
      </template>
    </el-drawer>

    <!-- 版本对比 -->
    <el-dialog v-model="compareVisible" title="版本对比" width="560px">
      <el-table :data="compareRows" size="small" border>
        <el-table-column prop="field" label="字段" width="120" />
        <el-table-column :label="`v${compareData.v1?.version ?? ''}`">
          <template #default="{ row }">
            <span :class="{ diff: row.changed }">{{ row.a }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="`v${compareData.v2?.version ?? ''}`">
          <template #default="{ row }">
            <span :class="{ diff: row.changed }">{{ row.b }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="muted" style="margin-top:8px">红色高亮为发生变化的字段</div>
    </el-dialog>

    <!-- 添加标注 -->
    <el-dialog v-model="annoVisible" title="添加地块标注" width="460px">
      <el-form :model="annoForm" label-width="90px">
        <el-form-item label="标注类型">
          <el-radio-group v-model="annoForm.type">
            <el-radio-button value="TEXT">文字</el-radio-button>
            <el-radio-button value="TAG">标签</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="annoForm.type === 'TEXT'" label="文字内容">
          <el-input v-model="annoForm.content" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item v-else label="标签">
          <el-input v-model="annoForm.tags" placeholder="多个标签用逗号分隔，如 高产田,流转田" />
        </el-form-item>
        <el-form-item label="颜色标记">
          <el-select v-model="annoForm.color" style="width:100%">
            <el-option v-for="(v, k) in colorMap" :key="k" :label="colorLabel[k]" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见范围">
          <el-radio-group v-model="annoForm.visibleScope">
            <el-radio-button value="SELF">仅自己</el-radio-button>
            <el-radio-button value="ALL">所有人</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="annoVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAnno">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download } from '@element-plus/icons-vue'
import { parcelApi, exportApi } from '../../api'
import AttachmentPanel from '../../components/AttachmentPanel.vue'
import RegionCascader from '../../components/RegionCascader.vue'
import ImportButton from '../../components/ImportButton.vue'

const landUseOptions = ['基本农田', '一般耕地', '设施农用地', '园地']
const colorMap = { red: '#f56c6c', yellow: '#e6a23c', green: '#67c23a', blue: '#409eff' }
const colorLabel = { red: '红-高风险', yellow: '黄-需关注', green: '绿-正常', blue: '蓝-已处理' }
const changeTypeText = { CREATE: '建档', UPDATE: '修改', DELETE: '删除' }

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ keyword: '', landUse: '', areaMin: undefined, areaMax: undefined, page: 1, size: 10 })

const load = async () => {
  loading.value = true
  try {
    const data = await parcelApi.page({
      keyword: query.keyword || undefined, landUse: query.landUse || undefined,
      areaMin: query.areaMin, areaMax: query.areaMax, page: query.page, size: query.size
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
const onExport = () => exportApi.parcel({
  keyword: query.keyword || undefined, landUse: query.landUse || undefined,
  areaMin: query.areaMin, areaMax: query.areaMax
})

// ---- 表单 ----
const formVisible = ref(false)
const formRef = ref()
const blankForm = () => ({
  id: null, parcelCode: '', name: '', regionId: null, regionPath: '', contractorName: '', contractorCode: '',
  area: null, landUse: '基本农田', contractStart: '', contractEnd: '',
  boundEast: '', boundSouth: '', boundWest: '', boundNorth: '', remark: '',
  _origCode: '', _origContractorCode: ''
})
const form = reactive(blankForm())
const formRules = {
  parcelCode: [{ required: true, message: '请输入地块编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入地块名称', trigger: 'blur' }]
}
const openForm = (row) => {
  Object.assign(form, blankForm())
  if (row) Object.assign(form, { ...row, _origCode: row.parcelCode, _origContractorCode: row.contractorCode })
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
    // 关键字段变更需填写原因
    const keyChanged = form.id && (form.parcelCode !== form._origCode || form.contractorCode !== form._origContractorCode)
    let reason
    if (keyChanged) {
      try {
        const r = await ElMessageBox.prompt('您修改了地块编码或承包方编码（关键字段），请填写变更原因：', '关键变更确认',
          { confirmButtonText: '提交', cancelButtonText: '取消', inputType: 'textarea',
            inputValidator: (v) => (v && v.trim() ? true : '变更原因不能为空') })
        reason = r.value
      } catch (e) { return }
    }
    saving.value = true
    try {
      if (form.id) await parcelApi.update(form.id, form, reason)
      else await parcelApi.create(form)
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
    const { value } = await ElMessageBox.prompt('确权地块为数据底座，删除前将校验关联业务数据。请填写删除原因：', '确认删除',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', inputType: 'textarea',
        inputValidator: (v) => (v && v.trim() ? true : '删除原因不能为空') })
    await parcelApi.remove(row.id, value)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* cancel */ }
}

// ---- 详情 / 历史 / 标注 ----
const detailVisible = ref(false)
const detail = ref(null)
const history = ref([])
const annotations = ref([])
const openDetail = async (row) => {
  compareSel.value = []
  detail.value = await parcelApi.get(row.id)
  ;[history.value, annotations.value] = await Promise.all([
    parcelApi.history(row.id), parcelApi.annotations(row.id)
  ])
  detailVisible.value = true
}

// ---- 版本对比 ----
const compareSel = ref([])
const compareVisible = ref(false)
const compareData = reactive({ v1: null, v2: null })
const compareRows = ref([])
const fieldLabels = {
  parcelCode: '地块编码', name: '地块名称', contractorName: '承包方姓名',
  contractorCode: '承包方编码', area: '确权面积', landUse: '地块用途'
}
const doCompare = async () => {
  const [a, b] = compareSel.value
  const res = await parcelApi.compare(a, b)
  compareData.v1 = res.v1
  compareData.v2 = res.v2
  let sa = {}, sb = {}
  try { sa = JSON.parse(res.v1.snapshot || '{}') } catch (e) {}
  try { sb = JSON.parse(res.v2.snapshot || '{}') } catch (e) {}
  compareRows.value = Object.entries(fieldLabels).map(([key, label]) => ({
    field: label,
    a: sa[key] ?? '-',
    b: sb[key] ?? '-',
    changed: String(sa[key] ?? '') !== String(sb[key] ?? '')
  }))
  compareVisible.value = true
}

const annoVisible = ref(false)
const annoForm = reactive({ type: 'TEXT', content: '', tags: '', color: 'green', visibleScope: 'SELF' })
const openAnno = () => {
  Object.assign(annoForm, { type: 'TEXT', content: '', tags: '', color: 'green', visibleScope: 'SELF' })
  annoVisible.value = true
}
const submitAnno = async () => {
  await parcelApi.addAnnotation(detail.value.id, { ...annoForm, parcelCode: detail.value.parcelCode })
  ElMessage.success('已添加标注')
  annoVisible.value = false
  annotations.value = await parcelApi.annotations(detail.value.id)
}
const removeAnno = async (a) => {
  await ElMessageBox.confirm('确认删除该标注？', '提示', { type: 'warning' })
  await parcelApi.removeAnnotation(a.id)
  ElMessage.success('已删除')
  annotations.value = await parcelApi.annotations(detail.value.id)
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.filter-card :deep(.el-card__body) { padding: 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.sep { color: #909399; }
.flex-spacer { flex: 1; }
.table-card :deep(.el-card__body) { padding: 12px 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.muted { color: #909399; font-size: 12px; margin-top: 2px; }
.anno-item { display: flex; align-items: flex-start; gap: 8px; padding: 10px 0; border-bottom: 1px dashed #ebeef5; }
.anno-dot { width: 10px; height: 10px; border-radius: 50%; margin-top: 5px; flex-shrink: 0; }
.anno-body { flex: 1; font-size: 13px; }
.anno-tags { margin-top: 4px; }
.diff { color: #f56c6c; font-weight: 600; }
</style>
