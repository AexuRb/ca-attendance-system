import { describe, expect, it } from 'vitest'
import {
  canAdvanceExportStep,
  exportPreviewValue,
  selectedExportFieldIds
} from './exportWizard.js'

describe('custom export wizard helpers', () => {
  it('keeps the selected field order for preview and export', () => {
    expect(selectedExportFieldIds([
      { id: 'name', selected: true },
      { id: 'studentNo', selected: false },
      { id: 'role', selected: true }
    ])).toEqual(['name', 'role'])
  })

  it('only advances when the current step is complete', () => {
    expect(canAdvanceExportStep(1, { sourceSelected: false })).toBe(false)
    expect(canAdvanceExportStep(1, { sourceSelected: true })).toBe(true)
    expect(canAdvanceExportStep(3, { selectedFieldCount: 0 })).toBe(false)
    expect(canAdvanceExportStep(3, { selectedFieldCount: 2 })).toBe(true)
    expect(canAdvanceExportStep(4, { previewReady: true })).toBe(true)
  })

  it('formats empty and structured preview values safely', () => {
    expect(exportPreviewValue(null)).toBe('-')
    expect(exportPreviewValue(true)).toBe('是')
    expect(exportPreviewValue({ count: 2 })).toBe('{"count":2}')
  })
})
