<template>
  <div class="attachment">
    <el-upload
      :action="uploadUrl"
      :headers="headers"
      :show-file-list="false"
      :on-success="onSuccess"
      :on-error="onError"
      :before-upload="beforeUpload"
    >
      <el-button size="small" :icon="Upload" :disabled="!bizId">上传附件</el-button>
      <span class="tip">支持图片/PDF/Word/Excel，单文件≤20MB</span>
    </el-upload>

    <el-empty v-if="!list.length" description="暂无附件" :image-size="50" />
    <div v-for="a in list" :key="a.id" class="att-item">
      <el-icon><Document /></el-icon>
      <span class="att-name" :title="a.fileName">{{ a.fileName }}</span>
      <span class="att-size">{{ sizeText(a.fileSize) }}</span>
      <el-button link type="primary" size="small" @click="download(a)">下载</el-button>
      <el-button link type="danger" size="small" @click="remove(a)">删除</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Document } from '@element-plus/icons-vue'
import { attachmentApi } from '../api'

const props = defineProps({
  bizType: { type: String, required: true },
  bizId: { type: [Number, String], default: null }
})

const list = ref([])
const headers = ref({ Authorization: `Bearer ${localStorage.getItem('token') || ''}` })
const uploadUrl = ref('')

const refreshUrl = () => {
  uploadUrl.value = props.bizId ? attachmentApi.uploadUrl(props.bizType, props.bizId) : ''
  headers.value = { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }
}

const load = async () => {
  if (!props.bizId) { list.value = []; return }
  list.value = await attachmentApi.list(props.bizType, props.bizId)
}

const beforeUpload = (file) => {
  if (file.size > 20 * 1024 * 1024) { ElMessage.error('文件不能超过 20MB'); return false }
  return true
}
const onSuccess = (res) => {
  if (res.code === 0) { ElMessage.success('上传成功'); load() }
  else ElMessage.error(res.message || '上传失败')
}
const onError = () => ElMessage.error('上传失败')

const download = async (a) => {
  const blob = await attachmentApi.download(a.id)
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = a.fileName
  link.click()
  window.URL.revokeObjectURL(url)
}
const remove = async (a) => {
  await ElMessageBox.confirm(`确认删除附件「${a.fileName}」？`, '提示', { type: 'warning' })
  await attachmentApi.remove(a.id)
  ElMessage.success('已删除')
  load()
}
const sizeText = (n) => {
  if (!n) return '-'
  if (n < 1024) return n + 'B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + 'KB'
  return (n / 1024 / 1024).toFixed(1) + 'MB'
}

watch(() => props.bizId, () => { refreshUrl(); load() })
onMounted(() => { refreshUrl(); load() })
</script>

<style scoped>
.attachment { font-size: 13px; }
.tip { font-size: 12px; color: #909399; margin-left: 8px; }
.att-item { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px dashed #ebeef5; }
.att-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.att-size { color: #909399; font-size: 12px; }
</style>
