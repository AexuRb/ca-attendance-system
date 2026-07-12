<template>
  <Teleport to="body">
    <Transition name="credits-dialog">
      <div v-if="open" class="credits-backdrop" @click.self="$emit('close')">
        <section
          ref="dialogRef"
          class="credits-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="creditsTitle"
          aria-describedby="creditsDescription"
          @keydown="handleKeydown"
        >
          <button ref="closeButtonRef" class="credits-close" type="button" title="关闭" aria-label="关闭鸣谢" @click="$emit('close')">
            <X :size="18" />
          </button>

          <header class="credits-intro">
            <p>{{ content.eyebrow }}</p>
            <h2 id="creditsTitle">{{ content.title }}</h2>
            <span id="creditsDescription">{{ content.introduction }}</span>
          </header>

          <div class="credits-body">
            <div class="credits-label">项目贡献</div>
            <article v-for="(person, index) in content.contributors" :key="person.name" class="credits-person" :style="{ '--credit-index': index }">
              <strong class="credits-index">{{ String(index + 1).padStart(2, '0') }}</strong>
              <div>
                <span class="credits-role">{{ person.role }}</span>
                <h3>{{ person.name }}</h3>
                <p>{{ person.contribution }}</p>
              </div>
            </article>
            <footer>
              <span>{{ content.closing }}</span>
              <strong>CA ATTENDANCE · V2.1</strong>
            </footer>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { X } from '@lucide/vue'
import { acknowledgements } from '../config/acknowledgements.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  content: { type: Object, default: () => acknowledgements }
})

const emit = defineEmits(['close'])
const dialogRef = ref(null)
const closeButtonRef = ref(null)
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

function handleKeydown(event) {
  if (event.key === 'Escape') {
    emit('close')
    return
  }
  if (event.key === 'Tab') {
    event.preventDefault()
    closeButtonRef.value?.focus()
  }
}
</script>

<style scoped>
.credits-backdrop {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: grid;
  padding: 24px;
  place-items: center;
  background: rgba(17, 45, 69, .44);
  backdrop-filter: blur(14px) saturate(.86);
}

.credits-dialog {
  position: relative;
  width: min(760px, 100%);
  max-height: calc(100dvh - 48px);
  overflow: auto;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 38px 100px rgba(11, 41, 64, .26), 0 3px 12px rgba(11, 41, 64, .1), inset 0 0 0 6px #edf6fb;
}

.credits-close {
  position: absolute;
  z-index: 2;
  top: 20px;
  right: 20px;
  display: grid;
  width: 36px;
  height: 36px;
  padding: 0;
  place-items: center;
  border: 1px solid #d5e5ef;
  border-radius: 50%;
  background: rgba(255, 255, 255, .9);
  color: #5b7386;
  cursor: pointer;
  transition: color 380ms cubic-bezier(.22, .76, .18, 1), background-color 380ms cubic-bezier(.22, .76, .18, 1), transform 520ms cubic-bezier(.18, .9, .24, 1.18);
}

.credits-close:hover {
  color: #15527f;
  background: #e8f4fc;
  transform: rotate(90deg) scale(1.04);
}

.credits-intro {
  position: relative;
  padding: 58px 64px 42px;
  overflow: hidden;
  border-bottom: 1px solid #d9e8f2;
  background: #f4faff;
}

.credits-intro::after {
  position: absolute;
  top: 0;
  right: 66px;
  width: 72px;
  height: 7px;
  background: #27796f;
  content: "";
}

.credits-intro p {
  display: flex;
  gap: 9px;
  align-items: center;
  margin: 0 0 22px;
  color: #2475aa;
  font-family: "Cascadia Mono", "Microsoft YaHei UI", monospace;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .16em;
}

.credits-intro p::before {
  width: 22px;
  height: 1px;
  background: currentColor;
  content: "";
}

.credits-intro h2 {
  max-width: 600px;
  margin: 0;
  color: #09233a;
  font-size: clamp(34px, 5vw, 54px);
  font-weight: 650;
  line-height: 1.12;
}

.credits-intro span {
  display: block;
  max-width: 560px;
  margin-top: 20px;
  color: #5f7a8f;
  font-size: 14px;
  line-height: 1.85;
}

.credits-body {
  padding: 42px 64px 48px;
}

.credits-label {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 23px;
  color: #7890a2;
  font-size: 10px;
  font-weight: 720;
  text-transform: uppercase;
  letter-spacing: .15em;
}

.credits-label::after {
  flex: 1;
  height: 1px;
  background: #dce9f2;
  content: "";
}

.credits-person {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 26px;
  animation: credits-person-in 720ms calc(180ms + var(--credit-index) * 70ms) cubic-bezier(.22, .76, .18, 1) both;
}

.credits-index {
  padding-top: 3px;
  color: #8fb3ca;
  font-family: "Cascadia Mono", "Microsoft YaHei UI", monospace;
  font-size: 42px;
  font-weight: 420;
  line-height: 1;
}

.credits-role {
  display: inline-flex;
  align-items: center;
  min-height: 27px;
  padding: 0 10px;
  border: 1px solid #b9d8e9;
  border-radius: 5px;
  color: #15527f;
  background: #eff8fd;
  font-size: 11px;
  font-weight: 700;
}

.credits-person h3 {
  margin: 14px 0 13px;
  color: #09233a;
  font-size: 30px;
  font-weight: 690;
}

.credits-person p {
  margin: 0;
  color: #587287;
  font-size: 14px;
  line-height: 1.9;
}

.credits-body footer {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  margin-top: 36px;
  padding-top: 20px;
  border-top: 1px solid #e0ebf2;
  color: #8298a9;
  font-size: 11px;
}

.credits-body footer strong {
  color: #517087;
  font-family: "Cascadia Mono", "Microsoft YaHei UI", monospace;
  font-weight: 600;
}

.credits-dialog-enter-active,
.credits-dialog-leave-active {
  transition: opacity 500ms cubic-bezier(.22, .76, .18, 1);
}

.credits-dialog-enter-active .credits-dialog,
.credits-dialog-leave-active .credits-dialog {
  transition: opacity 440ms cubic-bezier(.22, .76, .18, 1), transform 680ms cubic-bezier(.18, .9, .24, 1.1);
}

.credits-dialog-enter-from,
.credits-dialog-leave-to,
.credits-dialog-enter-from .credits-dialog,
.credits-dialog-leave-to .credits-dialog {
  opacity: 0;
}

.credits-dialog-enter-from .credits-dialog,
.credits-dialog-leave-to .credits-dialog {
  transform: translateY(26px) scale(.965);
}

@keyframes credits-person-in {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 640px) {
  .credits-backdrop { padding: 12px; place-items: end center; }
  .credits-dialog { max-height: calc(100dvh - 24px); }
  .credits-intro { padding: 52px 24px 32px; }
  .credits-intro::after { right: 24px; }
  .credits-body { padding: 30px 24px 36px; }
  .credits-person { grid-template-columns: 1fr; gap: 14px; }
  .credits-index { font-size: 28px; }
  .credits-body footer { align-items: flex-start; flex-direction: column; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 1ms !important;
    transition-duration: 1ms !important;
  }
}
</style>
