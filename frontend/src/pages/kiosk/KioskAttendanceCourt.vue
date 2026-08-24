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
                maxlength="128"
                placeholder="输入学号或姓名"
                @input="onQueryInput"
              />
              <button type="submit" :disabled="busy || !query">
                <template v-if="busy">
                  <LoaderCircle class="spin" aria-hidden="true" />
                  <span class="sr-only">正在查询</span>
                </template>
                <template v-else>
                  <span>继续</span><ArrowRight aria-hidden="true" />
                </template>
              </button>
            </div>
          </form>
          <p class="kiosk-focus-hint" :class="{ offline: !online }">
            <i aria-hidden="true"></i>
            <span>{{ online ? "本机服务正常" : "连接中断，正在重试" }}</span>
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
          <h1>选择账号</h1>
          <div class="kiosk-focus-choice-list">
            <button
              v-for="member in matches"
              :key="member.memberToken"
              ref="choiceButtons"
              type="button"
              :disabled="busy"
              :aria-busy="busy && selectingMemberToken === member.memberToken"
              @click="$emit('select-member', member.memberToken)"
            >
              <span class="kiosk-focus-avatar">{{ member.name.slice(0, 1) }}</span>
              <span>
                <strong>{{ member.name }}</strong>
                <small
                  >{{ member.maskedStudentNo
                  }}{{ member.grade ? ` · ${member.grade}` : "" }}</small
                >
              </span>
              <LoaderCircle
                v-if="busy && selectingMemberToken === member.memberToken"
                class="spin"
                aria-hidden="true"
              />
              <ChevronRight v-else aria-hidden="true" />
            </button>
          </div>
          <p v-if="error" class="kiosk-focus-error" role="alert">
            <CircleAlert aria-hidden="true" />
            <span>{{ error }}</span>
          </p>
        </section>

        <section
          v-else-if="step === 'confirm' && lookupResult"
          key="confirm"
          class="kiosk-focus-state kiosk-focus-confirm-state"
        >
          <header class="kiosk-signal-terminal-head">
            <h1>确认身份</h1>
            <span class="kiosk-focus-step-status">{{ pendingAction }}</span>
          </header>
          <div class="kiosk-focus-member-ticket">
            <span class="kiosk-focus-person-mark">{{
              lookupResult.name?.slice(0, 1)
            }}</span>
            <div>
              <strong>{{ lookupResult.name }}</strong>
              <span>{{ lookupResult.maskedStudentNo }}</span>
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
                ref="confirmButton"
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
        >
          <span class="kiosk-focus-success-mark" aria-hidden="true">
            <Check aria-hidden="true" />
          </span>
          <div class="kiosk-signal-success-copy" role="status">
            <h1>{{ successName }}，{{ successAction }}</h1>
            <span>{{ successTime }} · 记录已保存</span>
          </div>
          <div class="kiosk-focus-reset-progress" aria-hidden="true"><i></i></div>
          <button
            ref="nextButton"
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
  props.lookupResult?.action === "CHECK_OUT" ? "待签退" : "待签到",
);

onMounted(focusCurrentStep);
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
