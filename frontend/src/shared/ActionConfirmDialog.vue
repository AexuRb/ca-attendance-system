<template>
  <Transition name="confirm-dialog">
    <div v-if="confirmationState.open" class="confirm-dialog-backdrop" @click.self="cancelConfirmation">
      <section
        ref="dialogRef"
        class="confirm-dialog"
        :class="`tone-${confirmationState.tone}`"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirmDialogTitle"
        aria-describedby="confirmDialogMessage"
        @keydown="handleKeydown"
      >
        <header>
          <div class="confirm-dialog-symbol" aria-hidden="true">
            <AlertTriangle :size="20" />
          </div>
          <div>
            <p>请确认操作</p>
            <h2 id="confirmDialogTitle">{{ confirmationState.title }}</h2>
          </div>
          <button type="button" title="关闭" aria-label="关闭确认窗口" @click="cancelConfirmation">
            <X :size="18" aria-hidden="true" />
          </button>
        </header>

        <p v-if="confirmationState.message" id="confirmDialogMessage" class="confirm-dialog-message">
          {{ confirmationState.message }}
        </p>

        <label v-if="confirmationState.requiredText || confirmationState.inputRequired" class="confirm-dialog-field">
          <span>{{ inputLabel }}</span>
          <input
            ref="inputRef"
            v-model="inputValue"
            name="confirmationText"
            autocomplete="off"
            spellcheck="false"
            :aria-required="confirmationState.inputRequired ? 'true' : undefined"
            :placeholder="confirmationState.inputPlaceholder || confirmationState.requiredText"
            @keyup.enter="accept"
          />
        </label>

        <footer>
          <button ref="cancelButtonRef" type="button" class="confirm-dialog-cancel" data-action="cancel" @click="cancelConfirmation">
            {{ confirmationState.cancelLabel }}
          </button>
          <button
            type="button"
            class="confirm-dialog-accept"
            data-action="confirm"
            :disabled="!canAccept"
            @click="accept"
          >
            {{ confirmationState.confirmLabel }}
          </button>
        </footer>
      </section>
    </div>
  </Transition>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { AlertTriangle, X } from '@lucide/vue'
import {
  acceptConfirmation,
  cancelConfirmation,
  confirmationState
} from './confirm.js'

const dialogRef = ref(null)
const cancelButtonRef = ref(null)
const inputRef = ref(null)
const inputValue = ref('')
let previousFocus = null

const canAccept = computed(() => (
  (!confirmationState.requiredText || inputValue.value === confirmationState.requiredText) &&
  (!confirmationState.inputRequired || Boolean(inputValue.value.trim()))
))
const inputLabel = computed(() => (
  confirmationState.inputLabel || `输入“${confirmationState.requiredText}”继续`
))

watch(() => confirmationState.open, async open => {
  if (!open) {
    document.body.classList.remove('dialog-open')
    previousFocus?.focus?.()
    previousFocus = null
    return
  }

  previousFocus = document.activeElement
  inputValue.value = ''
  document.body.classList.add('dialog-open')
  await nextTick()
  ;(inputRef.value || cancelButtonRef.value)?.focus()
})

onBeforeUnmount(() => {
  document.body.classList.remove('dialog-open')
})

function accept() {
  if (canAccept.value) acceptConfirmation(inputValue.value)
}

function handleKeydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    cancelConfirmation()
    return
  }
  if (event.key !== 'Tab') return

  const controls = Array.from(dialogRef.value?.querySelectorAll(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])'
  ) || [])
  if (!controls.length) return
  const first = controls[0]
  const last = controls[controls.length - 1]
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
:global(body.dialog-open) {
  overflow: hidden;
}

.confirm-dialog-backdrop {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(26, 52, 72, 0.32);
  backdrop-filter: blur(8px);
}

.confirm-dialog {
  width: min(440px, 100%);
  overflow: hidden;
  border: 1px solid #bdd9ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(35, 83, 119, 0.2);
}

.confirm-dialog header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 18px 20px;
  border-bottom: 1px solid #d9e9f4;
}

.confirm-dialog header p,
.confirm-dialog header h2,
.confirm-dialog-message {
  margin: 0;
}

.confirm-dialog header p {
  color: #6a879c;
  font-size: 12px;
  font-weight: 700;
}

.confirm-dialog header h2 {
  margin-top: 2px;
  color: #17334a;
  font-size: 19px;
  line-height: 1.3;
  text-wrap: balance;
}

.confirm-dialog-symbol {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  color: #ad5c31;
  border: 1px solid #f0d4b6;
  border-radius: 6px;
  background: #fff8ee;
}

.confirm-dialog header > button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: #56748a;
  border: 0;
  border-radius: 5px;
  background: transparent;
  cursor: pointer;
}

.confirm-dialog header > button:hover {
  color: #17334a;
  background: #edf6fc;
}

.confirm-dialog-message {
  padding: 18px 20px 0;
  color: #48677d;
  line-height: 1.65;
  text-wrap: pretty;
}

.confirm-dialog-field {
  display: grid;
  gap: 7px;
  padding: 16px 20px 0;
  color: #365b74;
  font-size: 13px;
  font-weight: 700;
}

.confirm-dialog-field input {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  color: #17334a;
  border: 1px solid #b8d4e7;
  border-radius: 5px;
  background: #f9fcfe;
}

.confirm-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 20px;
}

.confirm-dialog footer button {
  min-width: 88px;
  height: 40px;
  padding: 0 16px;
  border-radius: 5px;
  font-weight: 750;
  cursor: pointer;
  transition: transform 160ms ease, background-color 160ms ease, border-color 160ms ease;
}

.confirm-dialog footer button:active:not(:disabled) {
  transform: translateY(1px);
}

.confirm-dialog-cancel {
  color: #365b74;
  border: 1px solid #bfd8e9;
  background: #fff;
}

.confirm-dialog-cancel:hover {
  background: #f1f8fc;
}

.confirm-dialog-accept {
  color: #fff;
  border: 1px solid #bb5b56;
  background: #bb5b56;
}

.confirm-dialog-accept:hover:not(:disabled) {
  border-color: #a34843;
  background: #a34843;
}

.confirm-dialog-accept:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.confirm-dialog :focus-visible {
  outline: 3px solid rgba(44, 137, 199, 0.3);
  outline-offset: 2px;
}

.confirm-dialog-enter-active,
.confirm-dialog-leave-active {
  transition: opacity 180ms ease;
}

.confirm-dialog-enter-active .confirm-dialog,
.confirm-dialog-leave-active .confirm-dialog {
  transition: transform 180ms ease, opacity 180ms ease;
}

.confirm-dialog-enter-from,
.confirm-dialog-leave-to,
.confirm-dialog-enter-from .confirm-dialog,
.confirm-dialog-leave-to .confirm-dialog {
  opacity: 0;
}

.confirm-dialog-enter-from .confirm-dialog,
.confirm-dialog-leave-to .confirm-dialog {
  transform: translateY(8px) scale(0.985);
}

@media (prefers-reduced-motion: reduce) {
  .confirm-dialog-enter-active,
  .confirm-dialog-leave-active,
  .confirm-dialog-enter-active .confirm-dialog,
  .confirm-dialog-leave-active .confirm-dialog,
  .confirm-dialog footer button {
    transition-duration: 0.01ms;
  }
}
</style>
