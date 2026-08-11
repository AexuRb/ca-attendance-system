<template>
  <section
    class="panel today-records-panel"
    :class="{ 'is-empty': !records.length }"
  >
    <div class="section-heading">
      <div>
        <h2>今日值班记录</h2>
        <span>{{ recordCount }} 条记录 · {{ validHours }} 小时</span>
      </div>
      <RouterLink :to="{ name: 'attendance' }">
        全部记录<ArrowUpRight aria-hidden="true" />
      </RouterLink>
    </div>

    <EmptyState v-if="!records.length" title="今天还没有签到记录" />
    <div v-else class="today-record-table">
      <table>
        <thead>
          <tr>
            <th>成员</th>
            <th>签到</th>
            <th>签退</th>
            <th>状态</th>
            <th>有效时长</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="record in records"
            :key="record.id"
            :class="{
              'record-attention': ['PENDING', 'INCOMPLETE'].includes(
                record.effectiveStatus,
              ),
            }"
          >
            <td>
              <div class="today-person">
                <span class="avatar small">{{ record.name.slice(0, 1) }}</span>
                <span>
                  <strong>{{ record.name }}</strong>
                  <small v-if="record.studentNo">···· {{ record.studentNo.slice(-4) }}</small>
                </span>
              </div>
            </td>
            <td>{{ clock(record.checkInTime) }}</td>
            <td>{{ clock(record.checkOutTime) }}</td>
            <td>
              <StatusBadge
                :label="statusLabel(record.effectiveStatus)"
                :tone="statusTone(record.effectiveStatus)"
              />
            </td>
            <td>{{ duration(record) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { RouterLink } from "vue-router";
import { ArrowUpRight } from "@lucide/vue";
import EmptyState from "../../../shared/ui/EmptyState.vue";
import StatusBadge from "../../../shared/ui/StatusBadge.vue";
import type { TodayAttendanceRecord } from "./types";

defineProps<{
  records: TodayAttendanceRecord[];
  recordCount: number;
  validHours: number;
}>();

const clock = (value?: string) => value?.slice(11, 16) || "--:--";

function duration(record: TodayAttendanceRecord) {
  if (record.durationMinutes) {
    const hours = record.durationMinutes / 60;
    if (hours >= 1) return `${hours.toFixed(hours % 1 ? 1 : 0)} h`;
    const minutes = record.durationMinutes;
    return `${minutes}分`;
  }
  return "—";
}

function statusLabel(status: string) {
  return (
    (
      {
        VALID: "有效",
        INCOMPLETE: "未签退",
        PENDING: "待审核",
        INVALID: "无效",
      } as Record<string, string>
    )[status] || status
  );
}

function statusTone(status: string) {
  if (status === "VALID") return "success" as const;
  if (status === "INVALID") return "danger" as const;
  if (status === "PENDING") return "warning" as const;
  return "info" as const;
}
</script>
