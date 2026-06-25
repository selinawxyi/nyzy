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
      <span class="tip">支持图片/PDF/Word/Excel，单文件≤20MB；图片/PDF 可直接预览</span>
    </el-upload>

    <el-empty v-if="!list.length" description="暂无附件" :image-size="50" />
    <div v-for="a in list" :key="a.id" class="att-item">
      <el-image
        v-if="isImage(a)"
        class="att-thumb"
        :src="previewUrls[a.id]"
        fit="cover"
        :preview-src-list="imagePreviewList"
        :initial-index="imageIndex(a)"
        preview-teleported
      />
      <el-icon v-else class="att-icon"><Document /></el-icon>
      <span class="att-name" :title="a.fileName">{{ a.fileName }}</span>
      <span class="att-size">{{ sizeText(a.fileSize) }}</span>
      <el-button v-if="isPdf(a)" link type="primary" size="small" @click="previewPdf(a)">预览</el-button>
      <el-button link type="primary" size="small" @click="download(a)">下载</el-button>
      <el-button link type="danger" size="small" @click="remove(a)">删除</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
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
// 附件下载接口需要鉴权, <img>/直接跳转拿不到 token, 所以图片要先拉 blob 再转成本地 blob: URL 才能预览
const previewUrls = reactive({})

const isImage = (a) => (a.contentType || '').startsWith('image/')
const isPdf = (a) => a.contentType === 'application/pdf'
const imageList = computed(() => list.value.filter(isImage))
const imagePreviewList = computed(() => imageList.value.map((a) => previewUrls[a.id]).filter(Boolean))
const imageIndex = (a) => imageList.value.findIndex((x) => x.id === a.id)

const refreshUrl = () => {
  uploadUrl.value = props.bizId ? attachmentApi.uploadUrl(props.bizType, props.bizId) : ''
  headers.value = { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }
}

const revokePreviews = () => {
  Object.values(previewUrls).forEach((url) => window.URL.revokeObjectURL(url))
  Object.keys(previewUrls).forEach((k) => delete previewUrls[k])
}

const loadImagePreviews = () => Promise.all(imageList.value.map(async (a) => {
  if (previewUrls[a.id]) return
  const blob = await attachmentApi.download(a.id)
  previewUrls[a.id] = window.URL.createObjectURL(blob)
}))

const load = async () => {
  revokePreviews()
  if (!props.bizId) { list.value = []; return }
  list.value = await attachmentApi.list(props.bizType, props.bizId)
  loadImagePreviews()
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
const previewPdf = async (a) => {
  const blob = await attachmentApi.download(a.id)
  window.open(window.URL.createObjectURL(blob), '_blank')
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
onBeforeUnmount(revokePreviews)
</script>

<style scoped>
.attachment { font-size: 13px; }
.tip { font-size: 12px; color: #909399; margin-left: 8px; }
.att-item { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px dashed #ebeef5; }
.att-thumb { width: 32px; height: 32px; border-radius: 4px; flex-shrink: 0; cursor: pointer; }
.att-icon { flex-shrink: 0; color: #909399; }
.att-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.att-size { color: #909399; font-size: 12px; }
</style>
