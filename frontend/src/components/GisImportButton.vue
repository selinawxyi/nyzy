<template>
  <span class="gis-import-btn">
    <el-button :icon="QuestionFilled" @click="showHelp">字段说明</el-button>
    <el-upload
      :action="uploadUrl"
      :headers="headers"
      :show-file-list="false"
      :on-success="onSuccess"
      :on-error="onError"
      :before-upload="beforeUpload"
      :accept="accept"
      style="display:inline-block; margin-left: 8px"
    >
      <el-button type="success" :icon="Upload">{{ label }}</el-button>
    </el-upload>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled, Upload } from '@element-plus/icons-vue'
import request from '../api/request'

const props = defineProps({
  type: { type: String, required: true },     // parcel-shapefile / parcel-kml
  accept: { type: String, required: true },   // .zip / .kml
  label: { type: String, default: '导入' }
})
const emit = defineEmits(['done'])

const uploadUrl = computed(() => `/api/import/${props.type}`)
const headers = computed(() => ({ Authorization: `Bearer ${localStorage.getItem('token') || ''}` }))

const showHelp = async () => {
  const res = await request.get(`/import/${props.type}/help`)
  const aliases = Object.entries(res.fieldAliases)
    .map(([k, v]) => `<div><b>${k}</b>: ${v.join(' / ')}</div>`).join('')
  ElMessageBox.alert(
    `<div>${res.desc}</div><div style="margin-top:8px">字段别名(任选一个表头/字段名即可匹配):</div>${aliases}`,
    '字段说明', { dangerouslyUseHTMLString: true })
}

const beforeUpload = (file) => {
  const ext = props.accept.replace('.', '')
  const ok = new RegExp(`\\.${ext}$`, 'i').test(file.name)
  if (!ok) ElMessage.error(`请上传 ${props.accept} 文件`)
  return ok
}
const onSuccess = (res) => {
  if (res.code !== 0) { ElMessage.error(res.message || '导入失败'); return }
  const r = res.data
  const errHtml = r.errors && r.errors.length
    ? '<div style="max-height:160px;overflow:auto;margin-top:8px;color:#f56c6c;font-size:12px">'
        + r.errors.map((e) => `<div>${e}</div>`).join('') + '</div>'
    : ''
  ElMessageBox.alert(
    `<div>共 ${r.total} 个图形，成功 <b style="color:#67c23a">${r.success}</b>，失败 <b style="color:#f56c6c">${r.failed}</b></div>${errHtml}`,
    '导入结果', { dangerouslyUseHTMLString: true })
  emit('done')
}
const onError = () => ElMessage.error('导入失败，请检查文件格式')
</script>

<style scoped>
.gis-import-btn { display: inline-flex; align-items: center; }
</style>
