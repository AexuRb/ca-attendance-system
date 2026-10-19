<template>
  <section class="data-workspace data-export-workspace">
    <p v-if="actionError && !selectedType" class="form-error" role="alert">{{ actionError }}</p>
    <DataTypeCatalog v-if="!selectedType" mode="export" :items="catalogItems" :busy="busy" @choose="chooseSource" />
    <AttendanceExportWorkspace v-else-if="request.source==='attendance'&&!attendanceCustom" @back="selectedType=false" @custom="attendanceCustom=true" />
    <template v-else>
    <header class="data-operation-header">
      <div class="import-kind-identity"><span class="import-kind-icon"><component :is="sourceIcons[request.source] || FileSpreadsheet" aria-hidden="true" /></span><h2>{{ currentSource?.label }}</h2></div>
      <button class="button secondary" data-button-kind="entry" type="button" :disabled="busy" @click="request.source==='attendance'?attendanceCustom=false:selectedType=false"><ArrowLeft aria-hidden="true" />{{request.source==='attendance'?'更换导出方式':'更换数据类型'}}</button>
    </header>
    <div class="data-frame data-export-table-frame">
      <div class="data-export-body">
        <section id="data-export-config" class="data-config-drawer open data-operation-config" aria-label="导出配置">
          <section class="data-config-section">
            <div class="data-config-title">
              <h4>筛选范围</h4>
              <button class="button text" type="button" :disabled="busy" @click="$emit('reset-filters')">
                恢复默认
              </button>
            </div>
            <div v-if="currentSource?.filters.length" class="data-filter-grid">
              <label
                v-for="filter in currentSource.filters"
                :key="filter.id"
                class="field"
              >
                <span>{{ filter.label }}</span>
                <select
                  v-if="filter.type === 'select'"
                  :name="`export-filter-${filter.id}`"
                  :disabled="busy"
                  :value="request.filters[filter.id]"
                  autocomplete="off"
                  @change="updateFilter(filter.id, $event)"
                >
                  <option value="">全部</option>
                  <option
                    v-for="option in filter.options"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
                <input
                  v-else
                  :name="`export-filter-${filter.id}`"
                  :disabled="busy"
                  :value="request.filters[filter.id]"
                  :type="filter.type === 'date' ? 'date' : 'text'"
                  autocomplete="off"
                  @input="updateFilter(filter.id, $event)"
                />
              </label>
            </div>
            <p v-else class="data-config-empty">该数据源无需额外筛选</p>
            <p v-if="dateError" class="form-error" role="alert">
              {{ dateError }}
            </p>
          </section>

          <section class="data-config-section data-fields-section">
            <div class="data-config-title">
              <div><h4>导出字段</h4><span class="data-field-count">已选 {{ request.fields.length }}/{{ currentSource?.fields.length || 0 }}</span></div>
              <button data-button-kind="soft" class="button text" type="button" :disabled="busy" @click="$emit('toggle-all')">
                {{ allFieldsSelected ? "取消全选" : "全部选择" }}
              </button>
            </div>
            <div id="export-field-selection" class="data-field-grid">
              <label
                v-for="field in currentSource?.fields"
                :key="field.id"
                :class="{ selected: request.fields.includes(field.id) }"
              >
                <input
                  type="checkbox"
                  :disabled="busy"
                  :name="`export-field-${field.id}`"
                  :checked="request.fields.includes(field.id)"
                  @change="$emit('toggle-field', field.id)"
                />
                <span>{{ field.label }}</span>
              </label>
            </div>
          </section>

          <p v-if="!request.fields.length" class="data-field-warning" role="alert">至少选择一个导出字段。</p>
          <p v-if="actionError" class="form-error" role="alert">{{ actionError }}</p>
          <footer ref="actionBar" class="data-export-actions" aria-label="导出操作">
            <label class="field data-filename-field"><span>导出文件名</span><div class="data-filename-input"><input name="export-filename" :disabled="exportPending" :value="request.filename" autocomplete="off" placeholder="留空使用默认名称" @input="updateFilename" /><span>.xlsx</span></div></label>
            <div class="data-export-action-buttons">
            <button
              class="button secondary"
              data-button-kind="entry"
              data-export-action="preview"
              type="button"
              :disabled="!canPreview || previewPending"
              @click="requestPreview"
            >
              <ScanSearch aria-hidden="true" />{{ previewPending ? "正在生成" : "生成预览" }}
            </button>
            <button class="button primary" data-export-action="download" type="button" :disabled="!preview || previewPending || exportPending" @click="$emit('export')"><Download aria-hidden="true" />{{ exportPending ? '正在导出' : '导出 Excel' }}</button>
            </div>
          </footer>
        </section>
        <section class="data-preview-pane data-preview-surface" aria-labelledby="export-preview-heading">
          <header class="data-table-toolbar" :class="{ 'is-pending': !preview }">
            <h3 id="export-preview-heading" ref="previewHeading" tabindex="-1">结果预览 <small v-if="preview">{{ preview.truncated ? `共 ${preview.totalRows} 条 · 预览前 ${preview.rows.length} 行` : `共 ${preview.totalRows} 条` }}</small></h3>
            <p v-if="!preview" class="data-preview-status" role="status">{{ previewPending ? '正在生成…' : '待生成预览' }}</p>
          </header>
          <div v-if="preview && filterSummary.length" class="data-filter-summary" aria-label="当前导出范围">
            <span v-for="item in filterSummary" :key="item.id">{{ item.label }}：{{ item.value }}</span>
          </div>
          <div v-if="preview && !previewPending" class="table-shell data-preview-table" tabindex="0" role="region" aria-label="导出结果预览，可横向滚动">
            <table>
              <thead><tr><th class="data-index-cell">#</th><th v-for="field in preview.fields" :key="field.id">{{ field.label }}</th></tr></thead>
              <tbody v-if="preview.rows.length"><tr v-for="(row, index) in preview.rows" :key="index"><td class="data-index-cell">{{ index + 1 }}</td><td v-for="field in preview.fields" :key="field.id">{{ row[field.id] ?? "—" }}</td></tr></tbody>
              <tbody v-else><tr><td :colspan="Math.max(2, preview.fields.length + 1)"><EmptyState title="当前条件下没有数据" /></td></tr></tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { ArrowLeft, Download, FileSpreadsheet, ScanSearch, UsersRound, ClipboardList, GraduationCap, CalendarDays, Wrench, History } from "@lucide/vue";
import type { Component } from "vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import DataTypeCatalog from "./DataTypeCatalog.vue";
import AttendanceExportWorkspace from "./AttendanceExportWorkspace.vue";
import { onBeforeRouteLeave, onBeforeRouteUpdate } from "vue-router";
import type { ExportOptions, ExportPreview, ExportRequest, ExportSource } from "../../../features/maintenance/dataCenterTypes";
const props = defineProps<{
  options: ExportOptions; request: ExportRequest; preview: ExportPreview | null;
  dateError: string; actionError?: string; routeSource?: string; previewPending: boolean; exportPending: boolean;
}>();
const emit = defineEmits<{
  "select-source": [source: ExportSource]; "update-filter": [id: string, value: string]; "update-filename": [value: string];
  "toggle-field": [id: string]; "toggle-all": []; "reset-filters": []; preview: []; export: [];
}>();
const selectedType = ref(false),attendanceCustom=ref(false);
watch(() => props.routeSource, value => { selectedType.value = Boolean(value); attendanceCustom.value=Boolean(value); }, { immediate: true });
const busy = computed(() => props.previewPending || props.exportPending);
const previewHeading = ref<HTMLElement | null>(null);
const actionBar = ref<HTMLElement | null>(null);
const sourceIcons: Record<string, Component> = { members: UsersRound, attendance: ClipboardList, training: GraduationCap, schedule: CalendarDays, repairs: Wrench, logs: History };
watch(() => props.preview, async value => {
  if (!value) return;
  await nextTick();
  previewHeading.value?.focus({ preventScroll: true });
  actionBar.value?.scrollIntoView?.({ block: 'start', behavior: 'instant' });
});
const currentSource = computed(() => props.options.sources.find(source => source.id === props.request.source));
const allFieldsSelected = computed(() => Boolean(currentSource.value?.fields.length) && props.request.fields.length === currentSource.value?.fields.length);
const canPreview = computed(() => Boolean(currentSource.value) && props.request.fields.length > 0 && !props.dateError && !busy.value);
const filterSummary = computed(() => (currentSource.value?.filters || []).flatMap(filter => {
  const value = (props.preview?.filters || props.request.filters)[filter.id];
  return value?.trim() ? [{ id:filter.id, label:filter.label, value:filter.options?.find(option => option.value === value)?.label || value }] : [];
}));
function requestPreview() { if (canPreview.value && !props.previewPending) emit('preview'); }
const descriptions: Record<string, string> = {
  members: '成员账号与基本资料',
  attendance: '签到签退与值班时长',
  training: '培训场次与参与记录',
  schedule: '固定排班与值班安排',
  repairs: '设备维修与处理记录',
  logs: '系统操作与变更记录',
};
const catalogItems = computed(() => props.options.sources.map(source => ({ id: source.id, label: source.label,
  description: descriptions[source.id] || '业务数据' })));
function chooseSource(id: string) {
  if (busy.value) return;
  const source = props.options.sources.find(item => item.id === id);
  if (!source) return;
  if (source.id !== props.request.source) emit('select-source', source);
  attendanceCustom.value=false;
  selectedType.value = true;
}
onBeforeRouteLeave(() => !busy.value);
onBeforeRouteUpdate(() => !busy.value);
function updateFilter(id: string, event: Event) { emit('update-filter', id, (event.target as HTMLInputElement).value); }
function updateFilename(event: Event) { emit('update-filename', (event.target as HTMLInputElement).value); }
</script>
