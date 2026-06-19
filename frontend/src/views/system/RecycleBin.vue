<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <span class="label">业务类型：</span>
        <el-select v-model="bizType" placeholder="全部类型" clearable style="width: 160px" @change="load">
          <el-option v-for="(name, key) in typeNames" :key="key" :label="name" :value="key" />
        </el-select>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <span class="hint">软删除记录保留 90 天，到期后系统自动物理删除</span>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="业务类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag[row.bizType] || 'info'" effect="plain">{{ row.bizTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="名称" min-width="150" />
        <el-table-column prop="subtitle" label="标识" min-width="140" />
        <el-table-column prop="deleteReason" label="删除原因" min-width="170" show-overflow-tooltip />
        <el-table-column prop="deletedBy" label="删除人" width="110" />
        <el-table-column label="删除时间" width="170">
          <template #default="{ row }">{{ row.deletedAt }}</template>
        </el-table-column>
        <el-table-column label="剩余保留" width="100">
          <template #default="{ row }">
            <el-tag :type="row.daysLeft <= 7 ? 'danger' : row.daysLeft <= 30 ? 'warning' : 'info'" size="small">
              {{ row.daysLeft }} 天
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="onRestore(row)">恢复</el-button>
            <el-button link type="danger" size="small" @click="onPurge(row)">彻底删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="回收站为空" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { recycleApi } from '../../api'

const typeNames = {
  parcel: '确权地块', abandon: '撂荒地块', planting: '种植记录',
  quality: '耕地质量', water: '水利设施', support: '配套设施'
}
const typeTag = {
  parcel: 'primary', abandon: 'danger', planting: 'success',
  quality: 'warning', water: 'info', support: 'info'
}

const loading = ref(false)
const rows = ref([])
const bizType = ref('')

const load = async () => {
  loading.value = true
  try {
    rows.value = await recycleApi.list(bizType.value || undefined)
  } finally {
    loading.value = false
  }
}

const onRestore = async (row) => {
  await ElMessageBox.confirm(`确认恢复「${row.title}」？恢复后将重新出现在对应列表中。`, '恢复确认', { type: 'success' })
  await recycleApi.restore(row.bizType, row.bizId)
  ElMessage.success('已恢复')
  load()
}

const onPurge = async (row) => {
  await ElMessageBox.confirm(
    `彻底删除「${row.title}」后数据不可恢复，确认继续？（仅管理员可操作）`, '彻底删除',
    { type: 'warning', confirmButtonText: '彻底删除', confirmButtonClass: 'el-button--danger' })
  await recycleApi.purge(row.bizType, row.bizId)
  ElMessage.success('已彻底删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.filter-card :deep(.el-card__body) { padding: 14px 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; }
.label { font-size: 14px; color: #606266; }
.hint { font-size: 12px; color: #909399; margin-left: 8px; }
.table-card :deep(.el-card__body) { padding: 12px 16px; }
</style>
