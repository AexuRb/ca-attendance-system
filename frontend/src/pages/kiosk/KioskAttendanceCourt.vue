<template>
  <section class="kiosk-focus-stage">
    <div class="kiosk-focus-court">
      <Transition
        name="kiosk-signal-step"
        mode="out-in"
        @after-enter="focusCurrentStep"
      >
        <section
          v-if="step === 'input'"
          key="input"
          class="kiosk-focus-state kiosk-focus-input-state"
        >
          <header class="kiosk-signal-terminal-head">
            <h1>签到 / 签退</h1>
          </header>

          <form
            class="kiosk-signal-form"
            :aria-busy="busy"
            @submit.prevent="$emit('lookup')"
          >
            <div class="kiosk-focus-query-row">
              <ScanLine aria-hidden="true" />
              <input
                id="member-query"
                ref="queryInput"
                :value="query"
                name="memberQuery"
                aria-label="学号或姓名"
                autocomplete="off"
                inputmode="text"
                maxlength="128"
                placeholder="输入学号或姓名"
                @input="onQueryInput"
              />
              <button data-button-kind="action" type="submit" :disabled="busy || !query" :aria-busy="busy">
                <template v-if="busy">
                  <LoaderCircle class="spin" aria-hidden="true" />
                  <span>正在查询</span>
                </template>
                <template v-else>
                  <span>继续</span><ArrowRight aria-hidden="true" />
                </template>
              </button>
            </div>
          </form>
          <div class="kiosk-focus-feedback">
            <p v-if="!online" class="kiosk-focus-hint offline">
              服务连接异常，可稍后重新查询
            </p>
            <p v-if="error" class="kiosk-focus-error" role="alert">
              <CircleAlert aria-hidden="true" />
              <span>{{ error }}</span>
            </p>
          </div>
        </section>

        <section
          v-else-if="step === 'choose'"
          key="choose"
          class="kiosk-focus-state kiosk-flow-state kiosk-focus-choice-state"
        >
          <header class="kiosk-signal-terminal-head">
            <h1>选择账号</h1>
            <p class="kiosk-focus-choice-intro">请核对学号尾号，选择你的账号</p>
          </header>
          <div
            class="kiosk-focus-choice-list"
            :class="{ 'is-short-list': matches.length <= 4 }"
          >
            <button
              v-for="member in matches"
              :key="member.memberToken"
              ref="choiceButtons"
              type="button"
              :disabled="busy"
              :aria-busy="busy && selectingMemberToken === member.memberToken"
              @click="$emit('select-member', member.memberToken)"
            >
              <span class="kiosk-flow-choice-copy">
                <span class="kiosk-flow-choice-heading">
                  <strong>{{ member.name }}</strong>
                  <small v-if="member.grade">{{ displayGrade(member.grade) }}</small>
                </span>
                <span class="kiosk-flow-student-number">
                  <span>学号尾号</span>
                  <b>{{ studentNoSuffix(member.maskedStudentNo) }}</b>
                </span>
              </span>
              <LoaderCircle
                v-if="busy && selectingMemberToken === member.memberToken"
                class="spin"
                aria-hidden="true"
              />
              <ChevronRight v-else aria-hidden="true" />
            </button>
          </div>
          <button data-button-kind="entry"
            class="kiosk-focus-secondary"
            type="button"
            :disabled="busy"
            @click="$emit('reset')"
          >
            重新输入
          </button>
          <div class="kiosk-flow-feedback">
            <p v-if="matches.length > 4" class="kiosk-focus-choice-scroll">
              <ChevronDown aria-hidden="true" />滚动查看更多账号
            </p>
            <p v-if="error" class="kiosk-focus-error" role="alert">
              <CircleAlert aria-hidden="true" /><span>{{ error }}</span>
            </p>
          </div>
        </section>

        <section
          v-else-if="step === 'confirm' && lookupResult"
          key="confirm"
          class="kiosk-focus-state kiosk-flow-state kiosk-focus-confirm-state"
        >
          <header class="kiosk-signal-terminal-head">
            <h1>确认身份</h1>
          </header>
          <div class="kiosk-focus-member-ticket">
            <div>
              <strong>{{ lookupResult.name }}</strong>
              <span class="kiosk-flow-student-number">
                <span>学号尾号</span>
                <b>{{ studentNoSuffix(lookupResult.maskedStudentNo) }}</b>
              </span>
            </div>
          </div>
          <span class="kiosk-focus-step-status">{{ pendingAction }}</span>
          <div class="kiosk-focus-action-row">
            <button data-button-kind="entry"
              class="kiosk-focus-secondary"
              type="button"
              :disabled="busy"
              @click="$emit('reset')"
            >
              重新输入
            </button>
            <button data-button-kind="action"
              ref="confirmButton"
              class="kiosk-focus-primary"
              type="button"
              :disabled="busy"
              :aria-busy="busy"
              @click="$emit('submit')"
            >
              <LoaderCircle v-if="busy" class="spin" aria-hidden="true" />
              <Check v-else aria-hidden="true" />
              <span class="kiosk-confirm-label">{{
                busy
                  ? "正在提交"
                  : lookupResult.action === "CHECK_IN"
                    ? "确认签到"
                    : "确认签退"
              }}</span>
            </button>
          </div>
          <div class="kiosk-flow-feedback">
            <p v-if="lookupResult.message" class="kiosk-flow-message">
              {{ lookupResult.message }}
            </p>
            <p v-if="error" class="kiosk-focus-error" role="alert">
              <CircleAlert aria-hidden="true" /><span>{{ error }}</span>
            </p>
          </div>
        </section>

        <section
          v-else
          key="success"
          class="kiosk-focus-state kiosk-flow-state kiosk-focus-success-state"
        >
          <span class="kiosk-focus-success-mark" aria-hidden="true">
            <Check aria-hidden="true" />
          </span>
          <div class="kiosk-signal-success-copy" role="status">
            <h1>{{ successAction }}</h1>
            <strong class="kiosk-flow-success-name">{{ successName }}</strong>
            <span>{{ successTimeLabel }} {{ successTime }}</span>
          </div>
          <button data-button-kind="entry"
            ref="nextButton"
            class="kiosk-focus-secondary"
            type="button"
            @click="$emit('reset')"
          >
            下一位
          </button>
          <div class="kiosk-flow-return">
            <span>即将返回签到台</span>
            <div class="kiosk-focus-reset-progress" aria-hidden="true"><i></i></div>
          </div>
        </section>
      </Transition>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import {
  ArrowRight,
  Check,
  ChevronDown,
  ChevronRight,
  CircleAlert,
  LoaderCircle,
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
  busy: boolean;
  error: string;
  online: boolean;
  lookupResult: AttendanceLookupResult | null;
  matches: AttendanceMemberOption[];
  selectingMemberToken: string;
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
  "select-member": [memberToken: string];
}>();

const queryInput = ref<HTMLInputElement>();
const choiceButtons = ref<HTMLButtonElement[]>([]);
const confirmButton = ref<HTMLButtonElement>();
const nextButton = ref<HTMLButtonElement>();
const pendingAction = computed(() =>
  props.lookupResult?.action === "CHECK_OUT" ? "本次签退" : "本次签到",
);
const successTimeLabel = computed(() =>
  props.successAction === "签退成功" ? "签退时间" : "签到时间",
);

function studentNoSuffix(maskedStudentNo?: string) {
  return maskedStudentNo?.match(/\d{1,4}$/)?.[0] ?? maskedStudentNo ?? "—";
}

function displayGrade(grade: string) {
  return /^\d{4}$/.test(grade) ? `${grade}级` : grade;
}

onMounted(focusCurrentStep);
watch(() => props.busy, async busy => {
  if (!busy && props.step === "confirm") {
    await nextTick();
    confirmButton.value?.focus();
  }
});
watch(
  () => props.step,
  () => focusCurrentStep(),
);
watch(
  () => props.error,
  (value) => {
    if (value && props.step === "input") focusCurrentStep();
  },
);

function onQueryInput(event: Event) {
  emit("update:query", (event.target as HTMLInputElement).value.trimStart());
  emit("clear-error");
}

async function focusCurrentStep() {
  await nextTick();
  if (props.step === "input") queryInput.value?.focus();
  else if (props.step === "choose") choiceButtons.value[0]?.focus();
  else if (props.step === "confirm") confirmButton.value?.focus();
  else nextButton.value?.focus();
}
</script>
