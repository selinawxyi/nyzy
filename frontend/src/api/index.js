import request from './request'

export const authApi = {
  login: (data) => request.post('/auth/login', data),
  me: () => request.get('/auth/me')
}

export const abandonApi = {
  page: (params) => request.get('/abandon/parcels', { params }),
  detail: (id) => request.get(`/abandon/parcels/${id}`),
  create: (data) => request.post('/abandon/parcels', data),
  update: (id, data) => request.put(`/abandon/parcels/${id}`, data),
  changeStatus: (id, data) => request.post(`/abandon/parcels/${id}/status`, data),
  remove: (id, reason) => request.delete(`/abandon/parcels/${id}`, { params: { reason } }),
  addReason: (id, data) => request.post(`/abandon/parcels/${id}/reasons`, data),
  createTask: (id, data) => request.post(`/abandon/parcels/${id}/tasks`, data),
  taskProgress: (taskId, progress) => request.post(`/abandon/tasks/${taskId}/progress`, { progress }),
  taskAccept: (taskId, data) => request.post(`/abandon/tasks/${taskId}/accept`, data),
  taskPlan: (taskId, plan) => request.post(`/abandon/tasks/${taskId}/plan`, { plan }),
  taskFeedback: (taskId, content) => request.post(`/abandon/tasks/${taskId}/feedback`, { content })
}

export const plantingApi = {
  page: (params) => request.get('/planting/records', { params }),
  history: (parcelCode) => request.get('/planting/history', { params: { parcelCode } }),
  create: (data) => request.post('/planting/records', data),
  update: (id, data) => request.put(`/planting/records/${id}`, data),
  markInvalid: (id) => request.post(`/planting/records/${id}/invalid`),
  remove: (id, reason) => request.delete(`/planting/records/${id}`, { params: { reason } })
}

export const qualityApi = {
  page: (params) => request.get('/quality/records', { params }),
  create: (data) => request.post('/quality/records', data),
  update: (id, data) => request.put(`/quality/records/${id}`, data),
  remove: (id, reason) => request.delete(`/quality/records/${id}`, { params: { reason } }),
  batch: (ids, updates) => request.post('/quality/batch', { ids, updates })
}

export const waterApi = {
  page: (params) => request.get('/water/facilities', { params }),
  create: (data) => request.post('/water/facilities', data),
  update: (id, data) => request.put(`/water/facilities/${id}`, data),
  audit: (id, pass) => request.post(`/water/facilities/${id}/audit`, null, { params: { pass } }),
  remove: (id, reason) => request.delete(`/water/facilities/${id}`, { params: { reason } }),
  batch: (ids, updates) => request.post('/water/facilities/batch', { ids, updates })
}

export const supportApi = {
  page: (params) => request.get('/support/facilities', { params }),
  create: (data) => request.post('/support/facilities', data),
  update: (id, data) => request.put(`/support/facilities/${id}`, data),
  audit: (id, pass) => request.post(`/support/facilities/${id}/audit`, null, { params: { pass } }),
  remove: (id, reason) => request.delete(`/support/facilities/${id}`, { params: { reason } })
}

export const categoryApi = {
  tree: () => request.get('/facility-category/tree'),
  leaves: () => request.get('/facility-category/leaves'),
  create: (data) => request.post('/facility-category', data),
  update: (id, data) => request.put(`/facility-category/${id}`, data),
  remove: (id) => request.delete(`/facility-category/${id}`)
}

export const mapApi = {
  points: () => request.get('/map/points')
}

export const regionApi = {
  tree: () => request.get('/region/tree')
}

// 导出：拉取 blob 并触发下载
async function downloadExcel(path, params, filename) {
  const blob = await request.get(path, { params, responseType: 'blob' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}
export const exportApi = {
  abandon: (params) => downloadExcel('/export/abandon', params, '撂荒地块台账.xlsx'),
  abandonTask: (params) => downloadExcel('/export/abandon-task', params, '撂荒治理任务台账.xlsx'),
  parcel: (params) => downloadExcel('/export/parcel', params, '确权地块台账.xlsx'),
  water: (params) => downloadExcel('/export/water', params, '水利设施台账.xlsx'),
  support: (params) => downloadExcel('/export/support', params, '配套设施台账.xlsx'),
  planting: (params) => downloadExcel('/export/planting', params, '种植记录台账.xlsx'),
  quality: (params) => downloadExcel('/export/quality', params, '耕地质量台账.xlsx')
}

export const importApi = {
  uploadUrl: (type) => `/api/import/${type}`,
  template: (type, name) => downloadExcel(`/import/${type}/template`, {}, name)
}

export const attachmentApi = {
  list: (bizType, bizId) => request.get('/attachment/list', { params: { bizType, bizId } }),
  uploadUrl: (bizType, bizId) => `/api/attachment/upload?bizType=${bizType}&bizId=${bizId}`,
  download: (id) => request.get(`/attachment/download/${id}`, { responseType: 'blob' }),
  remove: (id) => request.delete(`/attachment/${id}`)
}

export const userApi = {
  list: (params) => request.get('/user/list', { params }),
  create: (data) => request.post('/user', data),
  update: (id, data) => request.put(`/user/${id}`, data),
  resetPassword: (id, password) => request.post(`/user/${id}/reset-password`, { password }),
  remove: (id) => request.delete(`/user/${id}`)
}

export const auditCenterApi = {
  list: (bizType) => request.get('/audit-center/items', { params: { bizType } }),
  audit: (bizType, id, pass) => request.post('/audit-center/audit', null, { params: { bizType, id, pass } })
}

export const auditLogApi = {
  list: (params) => request.get('/audit-log/list', { params })
}

export const notificationApi = {
  unreadCount: () => request.get('/notification/unread-count'),
  list: (params) => request.get('/notification/list', { params }),
  read: (id) => request.post(`/notification/${id}/read`),
  readAll: () => request.post('/notification/read-all')
}

export const recycleApi = {
  list: (bizType) => request.get('/recycle/items', { params: { bizType } }),
  restore: (bizType, id) => request.post('/recycle/restore', null, { params: { bizType, id } }),
  purge: (bizType, id) => request.delete('/recycle/purge', { params: { bizType, id } })
}

export const analysisApi = {
  years: () => request.get('/analysis/years'),
  overview: (year) => request.get('/analysis/overview', { params: { year } }),
  yearly: () => request.get('/analysis/yearly'),
  region: (year) => request.get('/analysis/region', { params: { year } }),
  landUse: (year) => request.get('/analysis/land-use', { params: { year } }),
  advantageZones: (crop) => request.get('/analysis/advantage-zones', { params: { crop } }),
  sankey: (fromYear, toYear) => request.get('/analysis/sankey', { params: { fromYear, toYear } }),
  regionCompare: (year) => request.get('/analysis/region-compare', { params: { year } })
}

export const parcelApi = {
  page: (params) => request.get('/parcel/parcels', { params }),
  get: (id) => request.get(`/parcel/parcels/${id}`),
  create: (data) => request.post('/parcel/parcels', data),
  update: (id, data, reason) => request.put(`/parcel/parcels/${id}`, data, { params: { reason } }),
  remove: (id, reason) => request.delete(`/parcel/parcels/${id}`, { params: { reason } }),
  history: (id) => request.get(`/parcel/parcels/${id}/history`),
  compare: (v1, v2) => request.get('/parcel/history/compare', { params: { v1, v2 } }),
  annotations: (id) => request.get(`/parcel/parcels/${id}/annotations`),
  addAnnotation: (id, data) => request.post(`/parcel/parcels/${id}/annotations`, data),
  removeAnnotation: (id) => request.delete(`/parcel/annotations/${id}`)
}
