<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div
        v-if="open"
        class="modal-backdrop agreement-backdrop"
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
              <h2 id="agreement-title">{{ caseNo }} · 协议预览</h2>
              <span>核对内容后可直接调用系统打印窗口。</span>
            </div>
            <div>
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
          <main data-dialog-content>
            <LoadingBlock v-if="loading" label="正在生成协议" />
            <div v-else-if="error" class="agreement-error">
              <TriangleAlert /><strong>协议暂时无法预览</strong>
              <p>{{ error }}</p>
              <button class="button secondary" @click="$emit('retry')">
                <RefreshCw />重新加载
              </button>
            </div>
            <iframe
              v-else
              ref="frame"
              title="维修协议内容"
              :srcdoc="previewHtml"
              sandbox="allow-same-origin allow-modals"
            />
          </main>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
<script setup lang="ts">
import { computed, ref } from "vue";
import { Printer, RefreshCw, TriangleAlert, X } from "@lucide/vue";
import LoadingBlock from "./LoadingBlock.vue";
import { sanitizeAgreementPreviewHtml } from "./agreementPreview";
import { useDialogFocus } from "./useDialogFocus";

const props = defineProps<{
  open: boolean;
  caseNo?: string;
  html?: string;
  loading?: boolean;
  error?: string;
}>();
const emit = defineEmits<{ close: []; retry: [] }>();
const dialog = ref<HTMLElement | null>(null);
const frame = ref<HTMLIFrameElement | null>(null);
const previewHtml = computed(() => sanitizeAgreementPreviewHtml(props.html));

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
