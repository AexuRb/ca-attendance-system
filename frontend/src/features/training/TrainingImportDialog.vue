<template>
  <ModalDialog
    :open="open"
    title="导入参与名单"
    size="sm"
    @close="close"
  >
    <label v-if="!file" class="upload-zone">
      <Upload aria-hidden="true" />
      <strong>选择培训名单 Excel</strong>
      <p>第一行默认作为主讲人记录</p>
      <input
        ref="input"
        type="file"
        name="training-roster-file"
        aria-label="选择培训名单 Excel"
        accept=".xlsx,.xls"
        @change="pick"
      />
    </label>
    <div v-else class="training-import-file">
      <FileSpreadsheet aria-hidden="true" />
      <div>
        <strong>{{ file.name }}</strong>
        <span>{{ fileSize(file.size) }}</span>
      </div>
      <button
        class="icon-button ghost"
        type="button"
        title="移除文件"
        aria-label="移除已选择的培训名单"
        :disabled="pending"
        @click="clearFile"
      >
        <X aria-hidden="true" />
      </button>
    </div>
    <p v-if="displayError" class="form-error training-import-error" role="alert">
      {{ displayError }}
    </p>
    <template #footer>
      <button class="button secondary" type="button" :disabled="pending || templatePending" @click="$emit('template')">
        <LoaderCircle v-if="templatePending" class="spin" aria-hidden="true" />
        <Download v-else aria-hidden="true" />{{ templatePending ? "正在下载" : "下载模板" }}
      </button>
      <button
        class="button primary"
        type="button"
        :disabled="!file || pending"
        @click="file && $emit('import', file)"
      >
        <LoaderCircle v-if="pending" class="spin" aria-hidden="true" />
        {{ pending ? "正在导入" : "开始导入" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { FileSpreadsheet, Download, LoaderCircle, Upload, X } from "@lucide/vue";
import { computed, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { excelFileError } from "../../shared/validation/fileValidation";

const props = withDefaults(
  defineProps<{
    open: boolean;
    pending: boolean;
    templatePending?: boolean;
    error: string;
  }>(),
  { templatePending: false },
);
const emit = defineEmits<{
  close: [];
  import: [file: File];
  template: [];
}>();
const file = ref<File | null>(null);
const input = ref<HTMLInputElement | null>(null);
const selectionError = ref("");
const displayError = computed(() => selectionError.value || props.error);

watch(
  () => props.open,
  () => clearFile(),
);

function pick(event: Event) {
  const target = event.target as HTMLInputElement;
  const selected = target.files?.[0] || null;
  selectionError.value = selected
    ? excelFileError(selected, "培训名单 Excel 文件")
    : "";
  file.value = selectionError.value ? null : selected;
  if (selectionError.value) target.value = "";
}

function clearFile() {
  file.value = null;
  selectionError.value = "";
  if (input.value) input.value.value = "";
}

function close() {
  if (props.pending) return;
  clearFile();
  emit("close");
}

function fileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  return `${(bytes / 1024).toFixed(1)} KB`;
}
</script>
