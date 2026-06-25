<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <span class="label">业务类型：</span>
        <el-radio-group v-model="bizType" @change="load">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="abandon">撂荒地块</el-radio-button>
          <el-radio-button value="water">水利设施</el-radio-button>
          <el-radio-button value="support">配套设施</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <span class="hint">汇总各模块「待审核」记录，点击名称查看完整信息后再审批，审批通过后前端用户方可见</span>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag[row.bizType] || 'info'" effect="plain">{{ row.bizTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="160">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="subtitle" label="详情" min-width="180" show-overflow-tooltip />
        <el-table-column prop="submittedBy" label="提交人" width="130" />
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">{{ row.submittedAt }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            <el-button link type="success" size="small" @click="onAudit(row, true)">通过</el-button>
            <el-button link type="danger" size="small" @click="onAudit(row, false)">退回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无待审核记录" />
    </el-card>

    <!-- 详情: 审核前先看完整信息, 而不是只凭一行摘要盲审 -->
    <el-drawer v-model="detailVisible" :title="`待审核详情 · ${detailRow?.bizTypeName || ''}`" size="480px">
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <template v-else-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item v-for="f in detailFields" :key="f.label" :label="f.label">
            {{ f.value ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="detailRow?.bizType === 'abandon' && detail.reasons?.length">
          <el-divider content-position="left">撂荒原因填报</el-divider>
          <div v-for="r in detail.reasons" :key="r.id" class="sub-item">
            <div><b>{{ (r.reasonTypes || '').split(',').map((x) => reasonDict[x] || x).join('、') }}</b></div>
            <div class="muted">{{ r.detail }}</div>
          </div>
        </template>

        <el-divider content-position="left">现场照片 / 附件</el-divider>
        <AttachmentPanel :biz-type="detailRow.bizType" :biz-id="detailRow.bizId" />
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="danger" @click="onAuditFromDetail(false)">退回</el-button>
        <el-button type="success" @click="onAuditFromDetail(true)">通过</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditCenterApi, waterApi, supportApi, abandonApi } from '../../api'
import AttachmentPanel from '../../components/AttachmentPanel.vue'
import { reasonDict, degreeDict, sourceDict } from '../../constants/dict'

const route = useRoute()
const typeTag = { abandon: 'danger', water: 'info', support: 'warning' }

const loading = ref(false)
const rows = ref([])
const bizType = ref(typeof route.query.bizType === 'string' ? route.query.bizType : '')

const load = async () => {
  loading.value = true
  try {
    rows.value = await auditCenterApi.list(bizType.value || undefined)
  } finally {
    loading.value = false
  }
}

// ---- 详情 ----
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailRow = ref(null)
const detail = ref(null)

const openDetail = async (row) => {
  detailRow.value = row
  detail.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    if (row.bizType === 'water') detail.value = await waterApi.get(row.bizId)
    else if (row.bizType === 'support') detail.value = await supportApi.get(row.bizId)
    else if (row.bizType === 'abandon') detail.value = await abandonApi.detail(row.bizId)
  } finally {
    detailLoading.value = false
  }
}

const detailFields = computed(() => {
  if (!detail.value) return []
  const d = detail.value
  if (detailRow.value?.bizType === 'water') {
    return [
      { label: '设施名称', value: d.name }, { label: '设施类型', value: d.type },
      { label: '所在位置', value: d.regionPath }, { label: '经纬度', value: d.lng && d.lat ? `${d.lng}, ${d.lat}` : null },
      { label: '建设年份', value: d.buildYear }, { label: '覆盖面积(亩)', value: d.coverArea },
      { label: '受益村组', value: d.benefitVillages }, { label: '管护责任人', value: d.manager },
      { label: '联系电话', value: d.phone }, { label: '技术参数', value: d.techParams },
      { label: '提交人', value: d.createdBy }, { label: '备注', value: d.remark }
    ]
  }
  if (detailRow.value?.bizType === 'support') {
    return [
      { label: '设施名称', value: d.name }, { label: '设施分类', value: d.categoryName },
      { label: '所在位置', value: d.regionPath }, { label: '经纬度', value: d.lng && d.lat ? `${d.lng}, ${d.lat}` : null },
      { label: '服务范围', value: d.serviceRange }, { label: '服务能力', value: d.serviceAbility },
      { label: '运营主体', value: d.operateSubject }, { label: '资质认证', value: d.qualification },
      { label: '联系电话', value: d.phone }, { label: '营业时间', value: d.businessHours },
      { label: '提交人', value: d.createdBy }, { label: '备注', value: d.remark }
    ]
  }
  if (detailRow.value?.bizType === 'abandon') {
    const p = d.parcel || {}
    return [
      { label: '地块编码', value: p.parcelCode }, { label: '地块名称', value: p.parcelName },
      { label: '坐落位置', value: p.regionPath }, { label: '撂荒年份', value: p.abandonYear },
      { label: '撂荒面积(亩)', value: p.area }, { label: '撂荒程度', value: degreeDict[p.degree] },
      { label: '来源', value: sourceDict[p.source] }, { label: '撂荒原因', value: p.reasonText },
      { label: '发现日期', value: p.foundDate }, { label: '上报人', value: p.reporter },
      { label: '备注', value: p.remark }
    ]
  }
  return []
})

const onAudit = async (row, pass) => {
  try {
    await ElMessageBox.confirm(
      `确认${pass ? '通过' : '退回'}「${row.title}」的审核？`,
      pass ? '审核通过' : '审核退回', { type: pass ? 'success' : 'warning' })
  } catch (e) {
    return false // 用户取消
  }
  await auditCenterApi.audit(row.bizType, row.bizId, pass)
  ElMessage.success(pass ? '已通过' : '已退回')
  load()
  return true
}

const onAuditFromDetail = async (pass) => {
  const ok = await onAudit(detailRow.value, pass)
  if (ok) detailVisible.value = false
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.filter-card :deep(.el-card__body) { padding: 14px 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.label { font-size: 14px; color: #606266; }
.hint { font-size: 12px; color: #909399; margin-left: 8px; }
.table-card :deep(.el-card__body) { padding: 12px 16px; }
.sub-item { padding: 8px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
.muted { color: #909399; font-size: 12px; }
</style>
