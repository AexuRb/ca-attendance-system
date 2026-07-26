<template>
  <section class="kiosk-focus-stage">
    <div class="kiosk-focus-court" aria-live="polite">
      <Transition
        name="kiosk-signal-step"
        mode="out-in"
        @after-enter="focusInput"
      >
        <section
          v-if="step === 'input'"
          key="input"
          class="kiosk-focus-state kiosk-focus-input-state"
        >
          <header class="kiosk-signal-terminal-head">
            <div>
              <p class="kiosk-focus-eyebrow">ATTENDANCE</p>
              <h1>签到 / 签退</h1>
            </div>
            <time>{{ dateLabel }}</time>
          </header>

          <form class="kiosk-signal-form" @submit.prevent="$emit('lookup')">
            <label class="sr-only" for="member-query">学号或姓名</label>
            <div class="kiosk-focus-query-row">
              <ScanLine aria-hidden="true" />
              <input
                id="member-query"
                ref="queryInput"
                :value="query"
                name="memberQuery"
                autocomplete="off"
                inputmode="text"
                placeholder="学号或姓名"
                @input="onQueryInput"
              />
              <button type="submit" :disabled="busy || !query">
                <LoaderCircle v-if="busy" class="spin" aria-hidden="true" />
                <template v-else>
                  <span>继续</span><ArrowRight aria-hidden="true" />
                </template>
              </button>
            </div>
          </form>
          <p class="kiosk-focus-hint">
            <i aria-hidden="true"></i>
            <span>本机服务正常</span>
            <span>·</span>
            <span>Enter 确认</span>
          </p>
          <p v-if="error" class="kiosk-focus-error" role="alert">
            <CircleAlert aria-hidden="true" />
            <span>{{ error }}</span>
          </p>
        </section>

        <section
          v-else-if="step === 'choose'"
          key="choose"
          class="kiosk-focus-state kiosk-focus-choice-state"
        >
          <button class="kiosk-focus-back" type="button" @click="$emit('reset')">
            <ArrowLeft aria-hidden="true" />重新输入
          </button>
          <p class="kiosk-focus-eyebrow">IDENTITY</p>
          <h1>选择账号</h1>
          <div class="kiosk-focus-choice-list">
            <button
              v-for="member in matches"
              :key="member.studentNo"
              type="button"
              @click="$emit('select-member', member.studentNo)"
            >
              <span class="kiosk-focus-avatar">{{ member.name.slice(0, 1) }}</span>
              <span>
                <strong>{{ member.name }}</strong>
                <small>学号末四位 {{ member.studentNo.slice(-4) }}</small>
              </span>
              <ChevronRight aria-hidden="true" />
            </button>
          </div>
        </section>

        <section
          v-else-if="step === 'confirm' && lookupResult"
          key="confirm"
          class="kiosk-focus-state kiosk-focus-confirm-state"
        >
          <header class="kiosk-signal-terminal-head">
            <div>
              <p class="kiosk-focus-eyebrow">IDENTITY</p>
              <h1>确认身份</h1>
            </div>
            <time>{{ pendingAction }}</time>
          </header>
          <div class="kiosk-focus-member-ticket">
            <span class="kiosk-focus-person-mark">{{
              lookupResult.name?.slice(0, 1)
            }}</span>
            <div>
              <strong>{{ lookupResult.name }}</strong>
              <span>学号末四位 {{ lookupResult.studentNo?.slice(-4) }}</span>
            </div>
            <div class="kiosk-focus-action-row">
              <button
                class="kiosk-focus-secondary"
                type="button"
                @click="$emit('reset')"
              >
                重新输入
              </button>
              <button
                class="kiosk-focus-primary"
                type="button"
                :disabled="busy"
                @click="$emit('submit')"
              >
                <LoaderCircle v-if="busy" class="spin" aria-hidden="true" />
                <LogIn
                  v-else-if="lookupResult.action === 'CHECK_IN'"
                  aria-hidden="true"
                />
                <LogOut v-else aria-hidden="true" />
                {{
                  busy
                    ? "正在提交"
                    : lookupResult.action === "CHECK_IN"
                      ? "确认签到"
                      : "确认签退"
                }}
              </button>
            </div>
          </div>
          <p class="kiosk-focus-confirm-note">
            <i aria-hidden="true"></i>
            <span>账号正常</span>
            <span v-if="lookupResult.message">· {{ lookupResult.message }}</span>
          </p>
          <p v-if="error" class="kiosk-focus-error" role="alert">
            <CircleAlert aria-hidden="true" />
            <span>{{ error }}</span>
          </p>
        </section>

        <section
          v-else
          key="success"
          class="kiosk-focus-state kiosk-focus-success-state"
          role="status"
        >
          <span class="kiosk-focus-success-mark" aria-hidden="true">
            <Check aria-hidden="true" />
          </span>
          <div class="kiosk-signal-success-copy">
            <h1>{{ successName }}，{{ successAction }}</h1>
            <span>{{ successTime }} · 记录已保存</span>
          </div>
          <div class="kiosk-focus-reset-progress" aria-hidden="true"><i></i></div>
          <button
            class="kiosk-focus-secondary"
            type="button"
            @click="$emit('reset')"
          >
            下一位
          </button>
        </section>
      </Transition>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronRight,
  CircleAlert,
  LoaderCircle,
  LogIn,
  LogOut,
  ScanLine,
} from "@lucide/vue";
import type {
  AttendanceLookupResult,
  AttendanceMemberOption,
} from "../../features/kiosk/attendanceLookup";
import type { KioskStep } from "../../features/kiosk/types";

const props = defineProps<{
  step: KioskStep;
  query: string;
  dateLabel: string;
  busy: boolean;
  error: string;
  lookupResult: AttendanceLookupResult | null;
  matches: AttendanceMemberOption[];
  successName: string;
  successAction: string;
  successTime: string;
}>();

const emit = defineEmits<{
  "update:query": [value: string];
  "clear-error": [];
  lookup: [];
  reset: [];
  submit: [];
  "select-member": [studentNo: string];
}>();

const queryInput = ref<HTMLInputElement>();
const pendingAction = computed(() =>
  props.lookupResult?.action === "CHECK_OUT" ? "待签退" : "待签到",
);

onMounted(focusInput);
watch(
  () => props.step,
  (value) => {
    if (value === "input") focusInput();
  },
);
watch(
  () => props.error,
  (value) => {
    if (value && props.step === "input") focusInput();
  },
);

function onQueryInput(event: Event) {
  emit("update:query", (event.target as HTMLInputElement).value.trimStart());
  emit("clear-error");
}

async function focusInput() {
  if (props.step !== "input") return;
  await nextTick();
  queryInput.value?.focus();
}
</script>
