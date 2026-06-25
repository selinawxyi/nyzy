import { ElMessageBox } from 'element-plus'

/**
 * 批量修改前的预览确认: 列出将影响的记录数 + 实际会变更的字段, 用户确认后才真正提交。
 * @param {number} count 选中的记录数
 * @param {object} updates 即将提交的字段值(null/''表示不改该字段)
 * @param {object} fieldLabels { 字段名: 中文标签 }
 */
export function confirmBatchUpdate(count, updates, fieldLabels) {
  const changes = Object.entries(updates)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `<div>${fieldLabels[k] || k}: <b>${v}</b></div>`)
    .join('')
  if (!changes) return Promise.reject(new Error('未填写任何要修改的字段'))
  return ElMessageBox.confirm(
    `<div>将对 <b style="color:#e6a23c">${count}</b> 条记录应用以下修改:</div>${changes}`,
    '批量修改预览', { confirmButtonText: '确认修改', cancelButtonText: '取消', dangerouslyUseHTMLString: true })
}
