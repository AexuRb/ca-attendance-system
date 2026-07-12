<template>
  <Teleport to="body">
    <Transition name="agreement-dialog">
      <div v-if="open" class="agreement-preview-backdrop" @click.self="$emit('close')">
        <section
          ref="dialogRef"
          class="agreement-preview-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="agreementPreviewTitle"
          @keydown="handleKeydown"
        >
          <header class="agreement-preview-head">
            <div>
              <p>Agreement Preview</p>
              <h2 id="agreementPreviewTitle">{{ caseNo ? `${caseNo} · ${title}` : title }}</h2>
              <span>核对协议内容后，可调用系统打印窗口打印或另存为 PDF。</span>
            </div>
            <div class="agreement-preview-actions">
              <button
                class="ghost-button"
                type="button"
                :disabled="loading || Boolean(error) || !html"
                @click="printAgreement"
              >
                <Printer :size="16" />打印
              </button>
              <button ref="closeButtonRef" class="agreement-preview-close" type="button" title="关闭" aria-label="关闭协议预览" @click="$emit('close')">
                <X :size="18" />
              </button>
            </div>
          </header>

          <div class="agreement-preview-stage" :class="{ loading, failed: Boolean(error) }">
            <div v-if="loading" class="agreement-preview-state" role="status">
              <LoaderCircle :size="28" />
              <strong>正在生成协议预览</strong>
              <span>正在读取本机协议内容</span>
            </div>

            <div v-else-if="error" class="agreement-preview-state error" role="alert">
              <TriangleAlert :size="28" />
              <strong>协议暂时无法预览</strong>
              <span>{{ error }}</span>
              <button class="ghost-button" type="button" @click="$emit('retry')"><RefreshCw :size="16" />重新加载</button>
            </div>

            <iframe
              v-else
              ref="frameRef"
              title="维修协议内容"
              :srcdoc="html"
              sandbox="allow-same-origin allow-modals"
            ></iframe>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { LoaderCircle, Printer, RefreshCw, TriangleAlert, X } from '@lucide/vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '维修协议预览' },
  caseNo: { type: String, default: '' },
  html: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['close', 'retry', 'print-error'])
const dialogRef = ref(null)
const closeButtonRef = ref(null)
const frameRef = ref(null)
let previousFocus = null

watch(() => props.open, async open => {
  if (open) {
    previousFocus = document.activeElement
    await nextTick()
    closeButtonRef.value?.focus()
    return
  }
  previousFocus?.focus?.()
  previousFocus = null
})

onBeforeUnmount(() => previousFocus?.focus?.())

function printAgreement() {
  const previewWindow = frameRef.value?.contentWindow
  if (!previewWindow) {
    emit('print-error', '协议内容尚未加载完成')
    return
  }
  try {
    previewWindow.focus()
    previewWindow.print()
  } catch {
    emit('print-error', '系统打印窗口无法打开，请重新加载协议后再试')
  }
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    emit('close')
    return
  }
  if (event.key !== 'Tab') return

  const focusable = Array.from(dialogRef.value?.querySelectorAll('button:not(:disabled)') || [])
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
</script>

<style scoped>
.agreement-preview-backdrop {
  position: fixed;
  z-index: 70;
  inset: 0;
  display: grid;
  padding: 24px;
  place-items: center;
  background: rgba(17, 45, 69, .48);
  backdrop-filter: blur(12px) saturate(.88);
}

.agreement-preview-dialog {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: min(1120px, 100%);
  height: min(860px, calc(100dvh - 48px));
  overflow: hidden;
  border-radius: 8px;
  background: #f3f8fc;
  box-shadow: 0 34px 100px rgba(10, 39, 61, .28), inset 0 0 0 1px rgba(255, 255, 255, .9);
}

.agreement-preview-head {
  display: flex;
  gap: 24px;
  align-items: center;
  justify-content: space-between;
  min-height: 82px;
  padding: 16px 20px 16px 24px;
  border-bottom: 1px solid #cfe1ed;
  background: rgba(255, 255, 255, .96);
}

.agreement-preview-head p,
.agreement-preview-head h2,
.agreement-preview-head span {
  margin: 0;
}

.agreement-preview-head p {
  color: #2877aa;
  font-family: "Cascadia Mono", "Microsoft YaHei UI", monospace;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .14em;
}

.agreement-preview-head h2 {
  margin-top: 4px;
  color: #0c2b43;
  font-size: 18px;
  line-height: 1.35;
}

.agreement-preview-head span {
  display: block;
  margin-top: 3px;
  color: #6b8294;
  font-size: 12px;
}

.agreement-preview-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 0 0 auto;
}

.agreement-preview-close {
  display: grid;
  width: 36px;
  height: 36px;
  padding: 0;
  place-items: center;
  border: 1px solid #cee0ec;
  border-radius: 50%;
  background: #f9fcfe;
  color: #60788b;
  cursor: pointer;
  transition: color 360ms cubic-bezier(.22, .76, .18, 1), background-color 360ms cubic-bezier(.22, .76, .18, 1), transform 460ms cubic-bezier(.18, .9, .24, 1.18);
}

.agreement-preview-close:hover {
  color: #15527f;
  background: #e9f5fc;
  transform: rotate(90deg);
}

.agreement-preview-stage {
  min-height: 0;
  padding: 16px;
  background: #e8f1f7;
}

.agreement-preview-stage iframe {
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 12px 34px rgba(30, 82, 119, .12);
}

.agreement-preview-state {
  display: grid;
  width: 100%;
  height: 100%;
  min-height: 300px;
  place-content: center;
  justify-items: center;
  color: #59758a;
  text-align: center;
}

.agreement-preview-state svg {
  margin-bottom: 14px;
  color: #2b78aa;
}

.agreement-preview-state:not(.error) svg {
  animation: agreement-spin 1.1s steps(12) infinite;
}

.agreement-preview-state strong {
  color: #173b56;
  font-size: 16px;
}

.agreement-preview-state span {
  margin-top: 7px;
  font-size: 12px;
}

.agreement-preview-state .ghost-button {
  margin-top: 18px;
}

.agreement-preview-state.error svg {
  color: #b45a4b;
}

.agreement-dialog-enter-active,
.agreement-dialog-leave-active {
  transition: opacity 420ms cubic-bezier(.22, .76, .18, 1);
}

.agreement-dialog-enter-active .agreement-preview-dialog,
.agreement-dialog-leave-active .agreement-preview-dialog {
  transition: opacity 420ms cubic-bezier(.22, .76, .18, 1), transform 560ms cubic-bezier(.18, .9, .24, 1.08);
}

.agreement-dialog-enter-from,
.agreement-dialog-leave-to,
.agreement-dialog-enter-from .agreement-preview-dialog,
.agreement-dialog-leave-to .agreement-preview-dialog {
  opacity: 0;
}

.agreement-dialog-enter-from .agreement-preview-dialog,
.agreement-dialog-leave-to .agreement-preview-dialog {
  transform: translateY(22px) scale(.975);
}

@keyframes agreement-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 720px) {
  .agreement-preview-backdrop { padding: 10px; }
  .agreement-preview-dialog { height: calc(100dvh - 20px); }
  .agreement-preview-head { align-items: flex-start; padding: 14px; }
  .agreement-preview-head span { display: none; }
  .agreement-preview-actions .ghost-button span { display: none; }
  .agreement-preview-stage { padding: 8px; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 1ms !important;
    transition-duration: 1ms !important;
  }
}
</style>
