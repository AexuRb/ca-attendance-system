<template>
  <section class="data-workspace data-export-workspace">
    <div class="data-frame data-export-overview">
      <section class="data-export-lead">
        <label class="data-source-select">
          <span>导出数据源</span>
          <select
            name="export-source"
            :value="request.source"
            aria-label="导出数据源"
            autocomplete="off"
            @change="selectSource"
          >
            <option
              v-for="source in options.sources"
              :key="source.id"
              :value="source.id"
            >
              {{ source.label }}
            </option>
          </select>
          <ChevronDown aria-hidden="true" />
        </label>
        <div class="data-export-total">
          <span>当前数据总量</span>
          <strong>{{ sourceTotal.toLocaleString("zh-CN") }}</strong>
          <small>{{ currentMetric?.detail || "选择条件后生成预览" }}</small>
        </div>
      </section>

      <section class="data-source-overview">
        <header>
          <h2>数据源规模</h2>
          <span v-if="summary?.generatedAt">{{ generatedAt }}</span>
        </header>
        <div v-if="summary?.datasets?.length" class="data-source-bars">
          <button
            v-for="metric in summary?.datasets || []"
            :key="metric.key"
            type="button"
            :class="{ active: metric.key === currentMetric?.key }"
            :aria-pressed="metric.key === currentMetric?.key"
            @click="selectMetricSource(metric.key)"
          >
            <span>{{ metric.label }}</span>
            <i><b :style="barStyle(metric.total)" /></i>
            <strong>{{ metric.total.toLocaleString("zh-CN") }}</strong>
          </button>
        </div>
        <div v-else class="data-overview-empty">数据规模将在加载完成后显示</div>
      </section>

      <aside class="data-export-result">
        <span>筛选后结果</span>
        <strong v-if="preview">{{ preview.totalRows.toLocaleString("zh-CN") }}</strong>
        <strong v-else class="data-result-pending">待预览</strong>
        <p>{{ preview ? preview.sourceLabel : currentSource?.label || "尚未选择数据源" }}</p>
        <div>
          <i>{{ activeFilterCount }} 项筛选</i>
          <i>{{ request.fields.length }} 个字段</i>
        </div>
      </aside>
    </div>

    <div
      class="data-frame data-export-table-frame"
      :class="{ 'drawer-open': configOpen, 'preview-ready': Boolean(preview) }"
    >
      <header class="data-table-toolbar">
        <div>
          <h2>结果预览</h2>
          <span v-if="preview">
            {{ preview.truncated ? "显示前 12 行" : `共 ${preview.totalRows} 行` }}
          </span>
          <span v-else>设置条件后生成真实数据预览</span>
        </div>
        <button
          class="button secondary"
          type="button"
          aria-controls="data-export-config"
          :aria-expanded="configOpen"
          @click="configOpen = true"
        >
          <SlidersHorizontal aria-hidden="true" />调整条件
        </button>
      </header>

      <div class="data-export-body">
        <div class="data-preview-pane">
          <LoadingBlock v-if="previewPending" label="正在生成预览" />
          <div v-else-if="!preview" class="data-preview-pending">
            <i aria-hidden="true"><ScanSearch /></i>
            <div>
              <strong>生成预览后检查导出内容</strong>
              <span>
                {{ currentSource?.label || "未选择数据源" }} ·
                {{ request.fields.length }} 个字段 ·
                {{ activeFilterCount }} 项筛选
              </span>
            </div>
            <button
              class="button secondary"
              type="button"
              :disabled="!canPreview"
              @click="$emit('preview')"
            >
              <ScanSearch aria-hidden="true" />生成预览
            </button>
          </div>
          <div v-else class="table-shell data-preview-table">
            <table>
              <thead>
                <tr>
                  <th class="data-index-cell">#</th>
                  <th v-for="field in previewFields" :key="field.id">
                    {{ field.label }}
                  </th>
                </tr>
              </thead>
              <tbody v-if="preview.rows.length">
                <tr v-for="(row, index) in preview.rows" :key="index">
                  <td class="data-index-cell">{{ index + 1 }}</td>
                  <td v-for="field in preview.fields" :key="field.id">
                    {{ row[field.id] ?? "—" }}
                  </td>
                </tr>
              </tbody>
              <tbody v-else>
                <tr>
                  <td :colspan="Math.max(2, previewFields.length + 1)">
                    <EmptyState title="当前条件下没有数据" />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <DataCenterDrawer
          :open="configOpen"
          eyebrow="导出配置"
          title="调整条件与字段"
          close-label="收起导出配置"
          panel-class="data-config-drawer"
          panel-id="data-export-config"
          @close="configOpen = false"
        >
          <section class="data-config-section">
            <div class="data-config-title">
              <h4>筛选范围</h4>
              <button class="button text" type="button" @click="$emit('reset-filters')">
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
              <h4>导出字段</h4>
              <button class="button text" type="button" @click="$emit('toggle-all')">
                {{ allFieldsSelected ? "取消全选" : "全部选择" }}
              </button>
            </div>
            <div class="data-field-grid">
              <label
                v-for="field in currentSource?.fields"
                :key="field.id"
                :class="{ selected: request.fields.includes(field.id) }"
              >
                <input
                  type="checkbox"
                  :name="`export-field-${field.id}`"
                  :checked="request.fields.includes(field.id)"
                  @change="$emit('toggle-field', field.id)"
                />
                <span>{{ field.label }}</span>
                <Check aria-hidden="true" />
              </label>
            </div>
          </section>

          <div class="data-drawer-actions">
            <button
              class="button primary"
              type="button"
              :disabled="!canPreview || previewPending"
              @click="$emit('preview')"
            >
              <ScanSearch aria-hidden="true" />生成预览
            </button>
          </div>
        </DataCenterDrawer>
      </div>

      <footer class="data-export-footer">
        <label class="field data-filename-field">
          <span>导出文件名</span>
          <input
            name="export-filename"
            :value="request.filename"
            autocomplete="off"
            placeholder="留空使用默认文件名…"
            @input="updateFilename"
          />
        </label>
        <span>
          {{ preview ? `${preview.totalRows} 条记录` : "等待预览" }} ·
          {{ request.fields.length }} 个字段
        </span>
        <button
          class="button primary"
          type="button"
          :disabled="!preview || exportPending"
          @click="$emit('export')"
        >
          <Download aria-hidden="true" />导出 Excel
        </button>
      </footer>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import {
  Check,
  ChevronDown,
  Download,
  ScanSearch,
  SlidersHorizontal,
} from "@lucide/vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../../shared/ui/LoadingBlock.vue";
import DataCenterDrawer from "./DataCenterDrawer.vue";
import type {
  ExportOptions,
  ExportPreview,
  ExportRequest,
  ExportSource,
  MaintenanceSummary,
} from "../../../features/maintenance/dataCenterTypes";

const props = defineProps<{
  options: ExportOptions;
  request: ExportRequest;
  preview: ExportPreview | null;
  summary: MaintenanceSummary | null;
  dateError: string;
  previewPending: boolean;
  exportPending: boolean;
}>();

const emit = defineEmits<{
  "select-source": [source: ExportSource];
  "update-filter": [id: string, value: string];
  "update-filename": [value: string];
  "toggle-field": [id: string];
  "toggle-all": [];
  "reset-filters": [];
  preview: [];
  export: [];
}>();

const configOpen = ref(false);
const sourceMetricKeys: Record<string, string> = {
  members: "users",
  attendance: "attendance_records",
  training: "training_sessions",
  schedule: "duty_schedule_slots",
  repairs: "repair_cases",
  logs: "operation_logs",
};
const metricSourceIds: Record<string, string> = Object.fromEntries(
  Object.entries(sourceMetricKeys).map(([source, metric]) => [metric, source]),
);

const currentSource = computed(() =>
  props.options.sources.find((source) => source.id === props.request.source),
);
const currentMetric = computed(() =>
  props.summary?.datasets?.find(
    (metric) => metric.key === sourceMetricKeys[props.request.source],
  ),
);
const sourceTotal = computed(() => currentMetric.value?.total || 0);
const maxMetric = computed(() =>
  Math.max(1, ...(props.summary?.datasets?.map((metric) => metric.total) || [])),
);
const activeFilterCount = computed(() =>
  Object.values(props.request.filters).filter((value) => value.trim()).length,
);
const allFieldsSelected = computed(
  () =>
    Boolean(currentSource.value?.fields.length) &&
    props.request.fields.length === currentSource.value?.fields.length,
);
const canPreview = computed(
  () => props.request.fields.length > 0 && !props.dateError,
);
const previewFields = computed(() => {
  if (props.preview) return props.preview.fields;
  return (
    currentSource.value?.fields.filter((field) =>
      props.request.fields.includes(field.id),
    ) || []
  );
});
const generatedAt = computed(() => {
  if (!props.summary?.generatedAt) return "";
  return `更新于 ${new Date(props.summary.generatedAt).toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })}`;
});

function barStyle(total: number) {
  return { "--data-scale": `${Math.max(4, (total / maxMetric.value) * 100)}%` };
}

function selectSource(event: Event) {
  const id = (event.target as HTMLSelectElement).value;
  const source = props.options.sources.find((item) => item.id === id);
  if (source) emit("select-source", source);
}

function selectMetricSource(metricKey: string) {
  const id = metricSourceIds[metricKey];
  const source = props.options.sources.find((item) => item.id === id);
  if (source) emit("select-source", source);
}

function updateFilter(id: string, event: Event) {
  emit("update-filter", id, (event.target as HTMLInputElement).value);
}

function updateFilename(event: Event) {
  emit("update-filename", (event.target as HTMLInputElement).value);
}
</script>
