import type { RepairCase } from "../repairs/repairTypes";

export interface ExportField {
  id: string;
  label: string;
  defaultSelected: boolean;
}

export interface ExportChoice {
  value: string;
  label: string;
}

export interface ExportFilter {
  id: string;
  label: string;
  type: "date" | "select" | "text";
  defaultValue: string;
  options: ExportChoice[];
}

export interface ExportSource {
  id: string;
  label: string;
  fields: ExportField[];
  filters: ExportFilter[];
}

export interface ExportOptions {
  sources: ExportSource[];
}

export interface ExportRequest {
  source: string;
  fields: string[];
  filters: Record<string, string>;
  filename: string;
}

export interface ExportPreview {
  source: string;
  sourceLabel: string;
  fields: ExportField[];
  filters: Record<string, string>;
  totalRows: number;
  truncated: boolean;
  rows: Array<Record<string, unknown>>;
}

export interface DataMetric {
  key: string;
  label: string;
  total: number;
  detail: string;
  tone: string;
}

export interface MaintenanceSummary {
  datasets: DataMetric[];
  generatedAt: string;
}

export interface BackupItem {
  filename: string;
  size: number;
  createdAt: string;
}

export type RecycledRepairCase = RepairCase;
