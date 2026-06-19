<template>
  <span class="import-btn">
    <el-button :icon="Download" @click="downloadTpl">下载模板</el-button>
    <el-upload
      :action="uploadUrl"
      :headers="headers"
      :show-file-list="false"
      :on-success="onSuccess"
      :on-error="onError"
      :before-upload="beforeUpload"
      accept=".xlsx,.xls"
      style="display:inline-block; margin-left: 8px"
    >
      <el-button type="success" :icon="Upload">导入 Excel</el-button>
    </el-upload>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Upload } from '@element-plus/icons-vue'
import { importApi } from '../api'

const props = defineProps({
  type: { type: String, required: true },      // planting / quality / water
  templateName: { type: String, default: '导入模板.xlsx' }
})
const emit = defineEmits(['done'])

const uploadUrl = computed(() => importApi.uploadUrl(props.type))
const headers = computed(() => ({ Authorization: `Bearer ${localStorage.getItem('token') || ''}` }))

const downloadTpl = () => importApi.template(props.type, props.templateName)

const beforeUpload = (file) => {
  const ok = /\.(xlsx|xls)$/i.test(file.name)
  if (!ok) ElMessage.error('请上传 .xlsx/.xls 文件')
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
    `<div>共 ${r.total} 行，成功 <b style="color:#67c23a">${r.success}</b>，失败 <b style="color:#f56c6c">${r.failed}</b></div>${errHtml}`,
    '导入结果', { dangerouslyUseHTMLString: true })
  emit('done')
}
const onError = () => ElMessage.error('导入失败，请检查文件格式')
</script>

<style scoped>
.import-btn { display: inline-flex; align-items: center; }
</style>
