<template>
  <div class="page-stack">
    <PageHeader title="今日" :description="todayLabel">
      <template #actions
        ><StatusBadge label="自动更新" tone="success"
      /></template>
    </PageHeader>
    <LoadingBlock v-if="busy && !dashboard" />
    <template v-else>
      <section class="metric-strip">
        <article>
          <span>今日记录</span
          ><strong>{{ dashboard?.todayRecordCount ?? 0 }}</strong
          ><small>{{ dashboard?.todayValidHours ?? 0 }} 小时</small>
        </article>
        <article :class="{ attention: dashboard?.todayPendingCount }">
          <span>待审核</span
          ><strong>{{ dashboard?.todayPendingCount ?? 0 }}</strong
          ><small>需要处理</small>
        </article>
        <article :class="{ attention: dashboard?.todayOpenCount }">
          <span>未签退</span
          ><strong>{{ dashboard?.todayOpenCount ?? 0 }}</strong
          ><small>今日记录</small>
        </article>
        <article>
          <span>处理中维修</span
          ><strong>{{ dashboard?.ongoingRepairCount ?? 0 }}</strong
          ><small>事务</small>
        </article>
      </section>
      <div class="today-grid">
        <section class="panel schedule-panel-wide">
          <div class="section-heading">
            <div>
              <p class="eyebrow">EFFECTIVE ROSTER</p>
              <h2>今日部长排班</h2>
            </div>
            <RouterLink v-if="canSchedule" :to="{ name: 'schedules' }"
              >管理排班<ArrowUpRight
            /></RouterLink>
          </div>
          <EmptyState
            v-if="!schedule?.slots?.length"
            :title="schedule?.cancelled ? '今日排班已取消' : '今日暂无排班'"
          />
          <div v-else class="compact-timeline">
            <article v-for="slot in schedule.slots" :key="slot.key">
              <time
                >{{ time(slot.startTime)
                }}<span>{{ time(slot.endTime) }}</span></time
              ><i></i>
              <div>
                <strong>{{ slot.title }}</strong>
                <p>
                  <span
                    v-for="person in slot.assignees"
                    :key="person.studentNo"
                    >{{ person.name }}</span
                  ><em v-if="!slot.assignees.length">待安排</em>
                </p>
              </div>
            </article>
          </div>
        </section>
        <section class="panel">
          <div class="section-heading">
            <div>
              <p class="eyebrow">TODAY LOG</p>
              <h2>今日值班记录</h2>
            </div>
            <RouterLink :to="{ name: 'attendance' }"
              >全部记录<ArrowUpRight
            /></RouterLink>
          </div>
          <EmptyState v-if="!records.length" title="今天还没有签到记录" />
          <div v-else class="activity-list">
            <article v-for="record in records.slice(0, 8)" :key="record.id">
              <span class="avatar small">{{ record.name.slice(0, 1) }}</span>
              <div>
                <strong>{{ record.name }}</strong>
                <p>
                  {{ clock(record.checkInTime) }} 签到 ·
                  {{
                    record.checkOutTime
                      ? `${clock(record.checkOutTime)} 签退`
                      : "尚未签退"
                  }}
                </p>
              </div>
              <StatusBadge
                :label="statusLabel(record.effectiveStatus)"
                :tone="statusTone(record.effectiveStatus)"
              />
            </article>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { ArrowUpRight } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import { get } from "../../shared/api";
import { useSession } from "../../app/session";
const { user } = useSession();
const busy = ref(false);
const dashboard = ref<any>(null);
const schedule = ref<any>(null);
const records = ref<any[]>([]);
let timer = 0;
const today = localDate(new Date());
const todayLabel = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "long",
  day: "numeric",
  weekday: "long",
}).format(new Date());
const canSchedule = computed(
  () => user.value?.role === "PRESIDENT" || user.value?.role === "ADMIN",
);
onMounted(() => {
  load();
  timer = window.setInterval(load, 60_000);
});
onBeforeUnmount(() => clearInterval(timer));
async function load() {
  busy.value = true;
  try {
    [dashboard.value, schedule.value, records.value] = await Promise.all([
      get(`/api/stats/dashboard?date=${today}`),
      get("/api/public/schedules/today"),
      get(`/api/attendance?from=${today}&to=${today}`),
    ]);
  } finally {
    busy.value = false;
  }
}
function localDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
const time = (v?: string) => v?.slice(0, 5) || "--:--";
const clock = (v?: string) => v?.slice(11, 16) || "--:--";
const statusLabel = (v: string) =>
  (
    ({
      VALID: "有效",
      INCOMPLETE: "未签退",
      PENDING: "待审核",
      INVALID: "无效",
    }) as any
  )[v] || v;
const statusTone = (v: string) =>
  v === "VALID"
    ? "success"
    : v === "INVALID"
      ? "danger"
      : v === "PENDING"
        ? "warning"
        : "info";
</script>
