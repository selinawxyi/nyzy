<template>
  <div class="page">
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="用户名/姓名/电话" clearable style="width: 200px" @keyup.enter="onSearch" />
        <el-select v-model="query.role" placeholder="角色" clearable style="width: 130px">
          <el-option v-for="(v, k) in roleDict" :key="k" :label="v" :value="k" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="onSearch">搜索</el-button>
        <div class="flex-spacer" />
        <el-button type="primary" :icon="Plus" @click="openForm()">新增用户</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="nickname" label="显示名" width="130" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag :type="roleTag[row.role] || 'info'" size="small">{{ roleDict[row.role] || row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="onResetPwd(row)">重置密码</el-button>
            <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="total"
          :current-page="query.page" :page-size="query.size" :page-sizes="[10, 20, 50]"
          @current-change="onPage" @size-change="onSize" />
      </div>
    </el-card>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新增用户'" width="480px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="显示名" prop="nickname"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item v-if="!form.id" label="初始密码">
          <el-input v-model="form.password" placeholder="留空默认 123456" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width:100%">
            <el-option v-for="(v, k) in roleDict" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
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
import { Search, Plus } from '@element-plus/icons-vue'
import { userApi } from '../../api'

const roleDict = { admin: '系统管理员', operator: '运营人员', gridman: '网格员' }
const roleTag = { admin: 'danger', operator: 'primary', gridman: 'success' }

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ keyword: '', role: '', status: '', page: 1, size: 10 })

const load = async () => {
  loading.value = true
  try {
    const data = await userApi.list({
      keyword: query.keyword || undefined, role: query.role || undefined,
      status: query.status === '' ? undefined : query.status, page: query.page, size: query.size
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

const formVisible = ref(false)
const formRef = ref()
const blankForm = () => ({ id: null, username: '', nickname: '', password: '', role: 'operator', phone: '', status: 1 })
const form = reactive(blankForm())
const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入显示名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}
const openForm = (row) => {
  Object.assign(form, blankForm())
  if (row) Object.assign(form, { ...row, password: '' })
  formVisible.value = true
}
const resetForm = () => formRef.value?.clearValidate()
const submitForm = () => {
  formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (form.id) await userApi.update(form.id, form)
      else await userApi.create(form)
      ElMessage.success('保存成功')
      formVisible.value = false
      load()
    } finally {
      saving.value = false
    }
  })
}
const onResetPwd = async (row) => {
  try {
    const { value } = await ElMessageBox.prompt(`为「${row.nickname}」重置密码（留空则重置为 123456）：`, '重置密码',
      { confirmButtonText: '确认重置', cancelButtonText: '取消', inputPlaceholder: '新密码' })
    await userApi.resetPassword(row.id, value || '')
    ElMessage.success('密码已重置')
  } catch (e) { /* cancel */ }
}
const onDelete = async (row) => {
  await ElMessageBox.confirm(`确认删除用户「${row.nickname}」？`, '提示', { type: 'warning' })
  await userApi.remove(row.id)
  ElMessage.success('已删除')
  load()
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
