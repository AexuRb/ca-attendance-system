export const exportSteps = Object.freeze([
  { id: 1, label: '选择数据源' },
  { id: 2, label: '设置筛选' },
  { id: 3, label: '选择字段' },
  { id: 4, label: '预览数据' },
  { id: 5, label: '导出文件' }
])

export function selectedExportFieldIds(fields = []) {
  return fields.filter(field => field.selected).map(field => field.id)
}

export function canAdvanceExportStep(step, state = {}) {
  if (step === 1 || step === 2) return Boolean(state.sourceSelected)
  if (step === 3) return Number(state.selectedFieldCount || 0) > 0
  if (step === 4) return Boolean(state.previewReady)
  return false
}

export function exportPreviewValue(value) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
