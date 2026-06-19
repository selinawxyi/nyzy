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
        <span class="hint">汇总各模块「待审核」记录，审批通过后前端用户方可见</span>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag[row.bizType] || 'info'" effect="plain">{{ row.bizTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="名称" min-width="160" />
        <el-table-column prop="subtitle" label="详情" min-width="180" show-overflow-tooltip />
        <el-table-column prop="submittedBy" label="提交人" width="130" />
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">{{ row.submittedAt }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" size="small" @click="onAudit(row, true)">通过</el-button>
            <el-button link type="danger" size="small" @click="onAudit(row, false)">退回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="暂无待审核记录" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditCenterApi } from '../../api'

const typeTag = { abandon: 'danger', water: 'info', support: 'warning' }

const loading = ref(false)
const rows = ref([])
const bizType = ref('')

const load = async () => {
  loading.value = true
  try {
    rows.value = await auditCenterApi.list(bizType.value || undefined)
  } finally {
    loading.value = false
  }
}

const onAudit = async (row, pass) => {
  await ElMessageBox.confirm(
    `确认${pass ? '通过' : '退回'}「${row.title}」的审核？`,
    pass ? '审核通过' : '审核退回', { type: pass ? 'success' : 'warning' })
  await auditCenterApi.audit(row.bizType, row.bizId, pass)
  ElMessage.success(pass ? '已通过' : '已退回')
  load()
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
</style>
