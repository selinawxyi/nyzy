<template>
  <div class="page">
    <el-card shadow="never" class="table-card">
      <div class="toolbar">
        <span class="title">配套设施分类（两级体系）</span>
        <el-button type="primary" :icon="Plus" @click="openForm(null, 0)">新增一级分类</el-button>
      </div>
      <el-table :data="tree" v-loading="loading" row-key="id" default-expand-all
        :tree-props="{ children: 'children' }">
        <el-table-column prop="name" label="分类名称" min-width="200" />
        <el-table-column label="图标" width="120">
          <template #default="{ row }">{{ row.icon || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '显示' : '隐藏' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button v-if="!row.parentId" link type="primary" size="small" @click="openForm(null, row.id)">添加子分类</el-button>
            <el-button link type="primary" size="small" @click="openForm(row, row.parentId)">编辑</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="formVisible" :title="dialogTitle" width="480px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="上级分类">
          <span>{{ parentName }}</span>
        </el-form-item>
        <el-form-item label="分类名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="图标"><el-input v-model="form.icon" placeholder="可选，如 Box" /></el-form-item>
        <el-form-item label="排序权重"><el-input-number v-model="form.sort" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="显示" inactive-text="隐藏" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转移设施后删除分类" width="440px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:12px"
        :title="`分类「${transferTarget?.name}」下还有设施未清空，请选择转移目标分类，转移完成后将自动删除该分类`" />
      <el-form label-width="90px">
        <el-form-item label="转移到">
          <el-select v-model="transferToId" placeholder="请选择目标分类（二级分类）" style="width:100%">
            <el-option v-for="o in transferOptions" :key="o.id" :label="o.name" :value="o.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTransferDelete">确认转移并删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { categoryApi } from '../../api'

const loading = ref(false)
const saving = ref(false)
const tree = ref([])

const load = async () => {
  loading.value = true
  try {
    tree.value = await categoryApi.tree()
  } finally {
    loading.value = false
  }
}

const formVisible = ref(false)
const formRef = ref()
const form = reactive({ id: null, parentId: 0, name: '', icon: '', sort: 0, status: 1 })
const parentName = ref('顶级')
const formRules = { name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }] }
const dialogTitle = computed(() => (form.id ? '编辑分类' : '新增分类'))

const findName = (id) => {
  for (const r of tree.value) {
    if (r.id === id) return r.name
    for (const c of r.children || []) if (c.id === id) return c.name
  }
  return '顶级'
}

const openForm = (row, parentId) => {
  if (row) {
    Object.assign(form, { id: row.id, parentId: row.parentId, name: row.name, icon: row.icon, sort: row.sort, status: row.status })
  } else {
    Object.assign(form, { id: null, parentId, name: '', icon: '', sort: 0, status: 1 })
  }
  parentName.value = form.parentId ? findName(form.parentId) : '顶级（一级分类）'
  formVisible.value = true
}
const resetForm = () => formRef.value?.clearValidate()
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) await categoryApi.update(form.id, form)
      else await categoryApi.create(form)
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
    await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '提示', { type: 'warning' })
    await categoryApi.remove(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e?.message?.includes('请先转移或删除')) openTransfer(row)
    /* else: cancel or other error already toasted */
  }
}

// ---------------- 转移设施后删除 ----------------
const transferVisible = ref(false)
const transferTarget = ref(null)
const transferToId = ref(null)
const transferOptions = ref([])
const openTransfer = async (row) => {
  transferTarget.value = row
  transferToId.value = null
  transferOptions.value = (await categoryApi.leaves()).filter((c) => c.id !== row.id)
  transferVisible.value = true
}
const submitTransferDelete = async () => {
  if (!transferToId.value) { ElMessage.error('请选择转移目标分类'); return }
  saving.value = true
  try {
    await categoryApi.transferDelete(transferTarget.value.id, transferToId.value)
    ElMessage.success('已转移设施并删除分类')
    transferVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; }
.table-card :deep(.el-card__body) { padding: 16px; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.title { font-weight: 600; color: #1f2d3d; }
</style>
