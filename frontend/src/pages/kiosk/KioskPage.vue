<template>
  <main class="kiosk-page">
    <header class="kiosk-header">
      <div class="kiosk-brand">
        <span class="kiosk-mark">CA</span>
        <div><strong>计算机协会</strong><span>值班签到台</span></div>
      </div>
      <div class="kiosk-header-actions">
        <span class="service-state" :data-online="online"
          ><i></i>{{ online ? "本机服务正常" : "连接中断，正在重试" }}</span
        >
        <RouterLink
          class="icon-button kiosk-login"
          :to="{ name: 'login' }"
          title="后台登录"
          aria-label="后台登录"
          ><LogIn
        /></RouterLink>
      </div>
    </header>

    <section class="kiosk-workspace">
      <div class="kiosk-primary">
        <Transition name="kiosk-step" mode="out-in">
          <section v-if="step === 'input'" key="input" class="kiosk-entry">
            <p class="kiosk-date">{{ dateLabel }}</p>
            <h1>签到或签退</h1>
            <form @submit.prevent="lookup">
              <label for="member-query">输入学号或姓名</label>
              <div class="kiosk-input-wrap">
                <ScanLine aria-hidden="true" />
                <input
                  id="member-query"
                  ref="queryInput"
                  v-model.trim="query"
                  autocomplete="off"
                  inputmode="text"
                  placeholder="学号 / 姓名"
                  @input="error = ''"
                />
                <button
                  class="kiosk-submit"
                  type="submit"
                  :disabled="busy || !query"
                >
                  <ArrowRight v-if="!busy" /><LoaderCircle
                    v-else
                    class="spin"
                  /><span class="sr-only">查询</span>
                </button>
              </div>
            </form>
            <p v-if="error" class="kiosk-error" role="alert">
              <CircleAlert />{{ error }}
            </p>
          </section>

          <section
            v-else-if="step === 'choose'"
            key="choose"
            class="kiosk-entry"
          >
            <button class="kiosk-back" type="button" @click="reset">
              <ArrowLeft />重新输入
            </button>
            <p class="kiosk-date">找到多位同名成员</p>
            <h1>请选择你的账号</h1>
            <div class="member-choice-list">
              <button
                v-for="member in matches"
                :key="member.studentNo"
                type="button"
                @click="selectMember(member.studentNo)"
              >
                <span class="member-choice-avatar">{{
                  member.name.slice(0, 1)
                }}</span>
                <span
                  ><strong>{{ member.name }}</strong
                  ><small
                    >学号末四位 {{ member.studentNo.slice(-4) }}</small
                  ></span
                ><ChevronRight />
              </button>
            </div>
          </section>

          <section
            v-else-if="step === 'confirm' && lookupResult"
            key="confirm"
            class="kiosk-entry"
          >
            <button class="kiosk-back" type="button" @click="reset">
              <ArrowLeft />不是本人
            </button>
            <p class="kiosk-date">请确认身份</p>
            <div class="identity-display">
              <span>{{ lookupResult.name?.slice(0, 1) }}</span>
              <div>
                <h1>{{ lookupResult.name }}</h1>
                <p>学号末四位 {{ lookupResult.studentNo?.slice(-4) }}</p>
              </div>
            </div>
            <button
              class="kiosk-action"
              type="button"
              :disabled="busy"
              @click="submitAttendance"
            >
              <LogIn v-if="lookupResult.action === 'CHECK_IN'" /><LogOut
                v-else
              />
              {{
                busy
                  ? "正在提交"
                  : lookupResult.action === "CHECK_IN"
                    ? "确认签到"
                    : "确认签退"
              }}
            </button>
            <p class="kiosk-confirm-note">{{ lookupResult.message }}</p>
          </section>

          <section v-else key="success" class="kiosk-success">
            <div class="success-rings"><Check /></div>
            <p>{{ successAction }}</p>
            <h1>{{ successName }}</h1>
            <span>{{ successTime }} · 记录已保存</span>
            <div class="reset-progress"><i></i></div>
            <button type="button" @click="reset">下一位</button>
          </section>
        </Transition>
      </div>

      <aside class="kiosk-schedule">
        <div class="schedule-heading">
          <div>
            <p>今日部长排班</p>
            <h2>{{ todaySchedule?.weekdayName || weekdayLabel }}</h2>
          </div>
          <span>{{ scheduleCount }} 人</span>
        </div>
        <div v-if="scheduleError" class="schedule-message">
          <CalendarX2 />
          <p>{{ scheduleError }}</p>
        </div>
        <div v-else-if="!todaySchedule" class="schedule-message">
          <LoaderCircle class="spin" />
          <p>正在读取排班</p>
        </div>
        <div v-else-if="todaySchedule.cancelled" class="schedule-message">
          <CalendarX2 />
          <p>今日排班已取消</p>
        </div>
        <div v-else-if="!todaySchedule.slots?.length" class="schedule-message">
          <CalendarClock />
          <p>今日暂无排班</p>
        </div>
        <div v-else class="kiosk-timeline">
          <article
            v-for="slot in todaySchedule.slots"
            :key="slot.key"
            class="timeline-slot"
          >
            <time
              >{{ shortTime(slot.startTime)
              }}<span>{{ shortTime(slot.endTime) }}</span></time
            >
            <div class="timeline-line"><i></i></div>
            <div class="timeline-content">
              <div class="slot-meta">
                <strong
                  >{{ shortTime(slot.startTime) }}–{{
                    shortTime(slot.endTime)
                  }}</strong
                ><span v-if="slot.origin === 'TEMPORARY_ADDITION'">临时</span>
              </div>
              <div class="slot-people">
                <span
                  v-for="person in slot.assignees"
                  :key="`${slot.key}-${person.studentNo}`"
                  :class="{ reassigned: person.reassigned }"
                  >{{ person.name }}</span
                ><em v-if="!slot.assignees.length">待安排部长</em>
              </div>
            </div>
          </article>
        </div>
        <div class="week-strip">
          <div
            v-for="day in weekSchedule"
            :key="day.date"
            :class="{ today: day.date === todayValue }"
          >
            <span>{{ day.weekdayName?.replace("星期", "周") }}</span
            ><strong>{{
              day.cancelled
                ? "休"
                : day.slots?.reduce(
                    (sum: number, slot: any) => sum + slot.assignees.length,
                    0,
                  ) || "—"
            }}</strong>
          </div>
        </div>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import {
  ArrowLeft,
  ArrowRight,
  CalendarClock,
  CalendarX2,
  Check,
  ChevronRight,
  CircleAlert,
  LoaderCircle,
  LogIn,
  LogOut,
  ScanLine,
} from "@lucide/vue";
import { get, post } from "../../shared/api";

type Step = "input" | "choose" | "confirm" | "success";
interface MemberOption {
  studentNo: string;
  name: string;
}
interface LookupResult {
  found: boolean;
  studentNo?: string;
  name?: string;
  action?: "CHECK_IN" | "CHECK_OUT";
  message: string;
  matches?: MemberOption[];
}

const step = ref<Step>("input");
const query = ref("");
const queryInput = ref<HTMLInputElement>();
const lookupResult = ref<LookupResult | null>(null);
const matches = ref<MemberOption[]>([]);
const busy = ref(false);
const error = ref("");
const online = ref(true);
const todaySchedule = ref<any>(null);
const weekSchedule = ref<any[]>([]);
const scheduleError = ref("");
const successName = ref("");
const successAction = ref("");
const successTime = ref("");
let resetTimer = 0;
let retryTimer = 0;

const now = new Date();
const todayValue = localDate(now);
const dateLabel = new Intl.DateTimeFormat("zh-CN", {
  month: "long",
  day: "numeric",
  weekday: "long",
}).format(now);
const weekdayLabel = new Intl.DateTimeFormat("zh-CN", {
  weekday: "long",
}).format(now);
const scheduleCount = computed(
  () =>
    todaySchedule.value?.slots?.reduce(
      (total: number, slot: any) => total + (slot.assignees?.length || 0),
      0,
    ) || 0,
);

onMounted(async () => {
  await loadSchedule();
  focusInput();
});
onBeforeUnmount(() => {
  clearTimeout(resetTimer);
  clearTimeout(retryTimer);
});

async function loadSchedule() {
  try {
    const [today, week] = await Promise.all([
      get<any>("/api/public/schedules/today"),
      get<any[]>("/api/public/schedules/week"),
    ]);
    todaySchedule.value = today;
    weekSchedule.value = week;
    scheduleError.value = "";
    online.value = true;
  } catch (cause) {
    scheduleError.value =
      cause instanceof Error ? cause.message : "排班暂时不可用";
    online.value = false;
    retryTimer = window.setTimeout(loadSchedule, 3000);
  }
}

async function lookup() {
  if (!query.value || busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    const result = await get<LookupResult>(
      `/api/public/attendance/lookup?query=${encodeURIComponent(query.value)}`,
    );
    online.value = true;
    if (result.matches?.length) {
      matches.value = result.matches;
      step.value = "choose";
    } else if (result.found) {
      lookupResult.value = result;
      step.value = "confirm";
    } else {
      error.value = `${result.message}。请检查学号，或联系管理员确认账号是否停用。`;
      focusInput();
    }
  } catch (cause) {
    online.value = false;
    error.value = cause instanceof Error ? cause.message : "查询失败";
    retryTimer = window.setTimeout(() => {
      if (step.value === "input" && query.value) lookup();
    }, 2500);
  } finally {
    busy.value = false;
  }
}

async function selectMember(studentNo: string) {
  query.value = studentNo;
  step.value = "input";
  await lookup();
}

async function submitAttendance() {
  if (!lookupResult.value?.studentNo || busy.value) return;
  busy.value = true;
  error.value = "";
  const requestId =
    crypto.randomUUID?.() ||
    `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  try {
    const result = await post<any>("/api/public/attendance/submit", {
      studentNo: lookupResult.value.studentNo,
      requestId,
    });
    online.value = true;
    successName.value = result.name;
    successAction.value =
      result.action === "CHECK_IN" ? "签到成功" : "签退成功";
    successTime.value = new Intl.DateTimeFormat("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    }).format(new Date(result.submittedAt));
    step.value = "success";
    resetTimer = window.setTimeout(reset, 4000);
  } catch (cause) {
    online.value = false;
    error.value = cause instanceof Error ? cause.message : "提交失败";
    step.value = "confirm";
  } finally {
    busy.value = false;
  }
}

function reset() {
  clearTimeout(resetTimer);
  query.value = "";
  lookupResult.value = null;
  matches.value = [];
  error.value = "";
  successName.value = "";
  step.value = "input";
  focusInput();
}
async function focusInput() {
  await nextTick();
  queryInput.value?.focus();
}
function shortTime(value?: string) {
  return value?.slice(0, 5) || "--:--";
}
function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
</script>
