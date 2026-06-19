// 治理状态: 文案 + Element Plus tag 类型 (对照参考 UI 配色)
export const governStatusDict = {
  PENDING: { label: '待审核', type: 'warning' },
  UNGOVERNED: { label: '未治理', type: 'danger' },
  GOVERNING: { label: '治理中', type: 'info' },
  GOVERNED: { label: '已治理', type: 'success' },
  REJECTED: { label: '已驳回', type: 'info' }
}

export const governStatusOptions = Object.entries(governStatusDict)
  .map(([value, v]) => ({ value, label: v.label }))

export const sourceDict = {
  REMOTE: '遥感监测',
  PATROL: '网格员巡查',
  REPORT: '群众举报'
}
export const sourceOptions = Object.entries(sourceDict).map(([value, label]) => ({ value, label }))

export const degreeDict = { FULL: '完全撂荒', PARTIAL: '部分撂荒' }
export const degreeOptions = Object.entries(degreeDict).map(([value, label]) => ({ value, label }))

// 撂荒原因大类 (文档分类体系 A-G)
export const reasonDict = {
  LABOR: '劳动力因素',
  ECON: '经济因素',
  INFRA: '基础设施因素',
  SOIL: '土壤因素',
  DISASTER: '自然灾害因素',
  TRANSFER: '土地流转因素',
  OTHER: '其他原因'
}
export const reasonOptions = Object.entries(reasonDict).map(([value, label]) => ({ value, label }))

// 任务状态
export const taskStatusDict = {
  ISSUED: { label: '已下发', type: 'warning' },
  HANDLING: { label: '办理中', type: 'primary' },
  ACCEPTING: { label: '待验收', type: 'info' },
  DONE: { label: '验收通过', type: 'success' },
  RETURNED: { label: '已退回', type: 'danger' }
}

// ===== 耕地利用：种植 / 质量 =====
export const seasonDict = { SPRING: '春季', SUMMER: '夏季', AUTUMN: '秋季' }
export const seasonOptions = Object.entries(seasonDict).map(([value, label]) => ({ value, label }))

export const plantingSourceDict = {
  REMOTE: '遥感解译', STAT: '统计上报', FARMER: '农户填报', PATROL: '网格员巡查'
}
export const plantingSourceOptions = Object.entries(plantingSourceDict).map(([value, label]) => ({ value, label }))

export const plantingStatusDict = {
  VALID: { label: '有效', type: 'success' },
  INVALID: { label: '已无效', type: 'info' }
}

export const cropOptions = ['水稻', '玉米', '大豆', '小麦', '谷子', '马铃薯'].map((c) => ({ value: c, label: c }))

export const soilTypeOptions = ['黑土', '棕壤', '潮土', '砂土', '白浆土'].map((c) => ({ value: c, label: c }))
export const obstacleOptions = ['盐碱', '瘠薄', '渍涝', '沙化', '污染'].map((c) => ({ value: c, label: c }))

// 地力等级 1-10 → 颜色 (1-3绿 4-6黄/橙 7-10红)
export function gradeTagType(grade) {
  if (grade <= 3) return 'success'
  if (grade <= 6) return 'warning'
  return 'danger'
}

// ===== 资源管理：水利设施 / 配套设施 =====
export const auditStatusDict = {
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已退回', type: 'danger' }
}

export const waterTypeOptions = ['机井', '泵站', '水闸', '渠道', '滴灌系统', '喷灌系统', '蓄水池']
  .map((c) => ({ value: c, label: c }))

export const runStatusDict = {
  正常: 'success', 维修中: 'warning', 废弃: 'info', 待改造: 'primary'
}
export const runStatusOptions = Object.keys(runStatusDict).map((c) => ({ value: c, label: c }))

export const operateStatusDict = { 正常: 'success', 停业: 'info', 建设中: 'warning' }
export const operateStatusOptions = Object.keys(operateStatusDict).map((c) => ({ value: c, label: c }))

export const operateSubjectOptions = ['企业', '合作社', '个体户'].map((c) => ({ value: c, label: c }))
