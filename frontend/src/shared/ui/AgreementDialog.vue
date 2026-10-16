<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div
        v-if="open"
        class="modal-backdrop agreement-backdrop"
        :class="backdropClass"
        @click.self="$emit('close')"
      >
        <section
          ref="dialog"
          class="agreement-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="agreement-title"
          tabindex="-1"
        >
          <header>
            <div>
              <p class="eyebrow">AGREEMENT PREVIEW</p>
              <h2 id="agreement-title">协议预览</h2>
              <span>{{ caseNo }}</span>
            </div>
            <div>
              <label class="agreement-zoom-control">
                <span class="sr-only">预览比例</span>
                <select v-model="zoomChoice" aria-label="预览比例">
                  <option value="fit">适合宽度</option>
                  <option value="75">75%</option>
                  <option value="100">100%</option>
                  <option value="125">125%</option>
                </select>
              </label>
              <button
                class="button secondary"
                :disabled="!html || loading"
                @click="print"
              >
                <Printer />打印</button
              ><button
                class="icon-button"
                aria-label="关闭预览"
                @click="$emit('close')"
              >
                <X />
              </button>
            </div>
          </header>
          <main ref="viewport" data-dialog-content tabindex="0" aria-label="协议正文">
            <LoadingBlock v-if="loading" label="正在生成协议" />
            <div v-else-if="error" class="agreement-error">
              <TriangleAlert /><strong>协议暂时无法预览</strong>
              <p>{{ error }}</p>
              <button class="button secondary" @click="$emit('retry')">
                <RefreshCw />重新加载
              </button>
            </div>
            <div v-else class="agreement-paper-canvas" :style="{ width: `${paperWidth * scale}px`, height: `${paperHeight * scale}px` }">
              <iframe
                ref="frame"
                title="维修协议内容"
                :srcdoc="previewHtml"
                :style="{ width: `${paperWidth}px`, height: `${paperHeight}px`, transform: `scale(${scale})` }"
                sandbox="allow-same-origin allow-modals"
                @load="measurePaper"
              />
            </div>
          </main>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { Printer, RefreshCw, TriangleAlert, X } from "@lucide/vue";
import LoadingBlock from "./LoadingBlock.vue";
import { sanitizeAgreementPreviewHtml } from "./agreementPreview";
import { useDialogFocus } from "./useDialogFocus";

const props = defineProps<{
  open: boolean;
  backdropClass?: string;
  caseNo?: string;
  html?: string;
  loading?: boolean;
  error?: string;
}>();
const emit = defineEmits<{ close: []; retry: [] }>();
const dialog = ref<HTMLElement | null>(null);
const frame = ref<HTMLIFrameElement | null>(null);
const viewport = ref<HTMLElement | null>(null);
const paperWidth = 830;
const paperHeight = ref(1155);
const viewportWidth = ref(paperWidth + 32);
const zoomChoice = ref("fit");
const scale = computed(() => zoomChoice.value === "fit"
  ? Math.min(1, Math.max(0.2, (viewportWidth.value - 32) / paperWidth))
  : Number(zoomChoice.value) / 100);
let viewportObserver: ResizeObserver | undefined;
let paperObserver: ResizeObserver | undefined;
const previewHtml = computed(() => sanitizeAgreementPreviewHtml(props.html));

watch(() => props.open, async (open) => {
  viewportObserver?.disconnect();
  paperObserver?.disconnect();
  if (!open) return;
  zoomChoice.value = "fit";
  await nextTick();
  if (!viewport.value) return;
  viewport.value.scrollTop = 0;
  viewport.value.scrollLeft = 0;
  viewportWidth.value = viewport.value.clientWidth;
  if (typeof ResizeObserver !== "undefined") {
    viewportObserver = new ResizeObserver(() => {
      if (viewport.value) viewportWidth.value = viewport.value.clientWidth;
    });
    viewportObserver.observe(viewport.value);
  }
}, { immediate: true });

function measurePaper() {
  const document = frame.value?.contentDocument;
  if (!document?.body) return;
  paperObserver?.disconnect();
  const measure = () => {
    if (frame.value?.contentDocument !== document || !document.body) return;
    const sheets = Array.from(document.querySelectorAll<HTMLElement>(".sheet, .paper"));
    const height = sheets.length
      ? Math.max(...sheets.map(sheet => sheet.getBoundingClientRect().bottom)) + 16
      : document.body.scrollHeight;
    paperHeight.value = Math.max(sheets.length ? 1 : 1155, Math.ceil(height));
  };
  measure();
  void document.fonts?.ready.then(measure);
  if (typeof ResizeObserver !== "undefined") {
    paperObserver = new ResizeObserver(measure);
    paperObserver.observe(document.body);
  }
}

onBeforeUnmount(() => { viewportObserver?.disconnect(); paperObserver?.disconnect(); });

useDialogFocus({
  root: dialog,
  open: () => props.open,
  close: () => emit("close"),
});

function print() {
  frame.value?.contentWindow?.focus();
  frame.value?.contentWindow?.print();
}
</script>
