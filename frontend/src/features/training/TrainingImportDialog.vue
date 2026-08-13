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
    <p v-if="error" class="form-error training-import-error" role="alert">
      {{ error }}
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
import { ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";

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

watch(
  () => props.open,
  () => clearFile(),
);

function pick(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0] || null;
}

function clearFile() {
  file.value = null;
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
