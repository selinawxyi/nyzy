<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-select v-model="query.bizType" placeholder="业务类型" clearable style="width: 150px">
          <el-option v-for="(v, k) in bizDict" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-model="query.action" placeholder="操作类型" clearable style="width: 140px">
          <el-option v-for="(v, k) in actionDict" :key="k" :label="v" :value="k" />
        </el-select>
        <el-input v-model="query.operator" placeholder="操作人" clearable style="width: 150px" @keyup.enter="onSearch" />
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="业务类型" width="120">
          <template #default="{ row }">{{ bizDict[row.bizType] || row.bizType }}</template>
        </el-table-column>
        <el-table-column prop="bizId" label="记录ID" width="90" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="actionTag[row.action] || 'info'">{{ actionDict[row.action] || row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="120" />
        <el-table-column prop="createdAt" label="时间" width="180" />
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
          :current-page="query.page" :page-size="query.size" @current-change="onPage" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { auditLogApi } from '../../api'

const bizDict = { abandon: '撂荒地块', abandon_task: '撂荒任务', parcel: '确权地块', water: '水利设施', support: '配套设施' }
const actionDict = { CREATE: '新增', UPDATE: '修改', DELETE: '删除', STATUS: '状态变更', REASON: '原因填报', PROGRESS: '进度填报', ACCEPT: '验收' }
const actionTag = { CREATE: 'success', UPDATE: 'primary', DELETE: 'danger', STATUS: 'warning', ACCEPT: 'success' }

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ bizType: '', action: '', operator: '', page: 1, size: 15 })

const load = async () => {
  loading.value = true
  try {
    const data = await auditLogApi.list({
      bizType: query.bizType || undefined, action: query.action || undefined,
      operator: query.operator || undefined, page: query.page, size: query.size
    })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const onSearch = () => { query.page = 1; load() }
const onPage = (p) => { query.page = p; load() }

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.filter-card :deep(.el-card__body) { padding: 16px; }
.filter-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.table-card :deep(.el-card__body) { padding: 12px 16px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
