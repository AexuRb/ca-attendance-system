<template>
  <section class="weekly-stats-workspace">
    <div class="weekly-stats-legend" aria-label="统计图例">
      <span><i data-tone="duty"></i>有效值班时长</span>
      <span><i data-tone="training"></i>培训时长</span>
      <span>— 表示无有效时长</span>
    </div>
    <div class="table-shell weekly-stats-table">
      <table>
      <thead>
        <tr>
          <th class="weekly-member-column">成员</th>
          <th>年级</th>
          <th v-for="day in detail.days" :key="day.dutyDate">
            <strong>{{ day.weekdayName }}</strong>
            <small>{{ day.dutyDate.slice(5) }}</small>
          </th>
          <th class="weekly-training-column">培训</th>
          <th class="weekly-total-column">合计</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="member in detail.users" :key="member.userId">
          <td class="weekly-member-column">
            <span class="stats-member-title"><strong>{{ member.name }}</strong><button class="stats-detail-trigger" type="button" :disabled="disabled" @click="$emit('select-member', member.userId)">查看详情</button></span>
            <small>{{ member.studentNo }} · {{ roleLabel(member.role) }}</small>
          </td>
          <td>{{ member.grade || "—" }}</td>
          <td
            v-for="day in detail.days"
            :key="`${member.userId}-${day.dutyDate}`"
            class="weekly-hours-cell"
            :data-heat="
              weeklyHeatLevel(
                weeklyCellHours(detail, day.dutyDate, member.userId),
              )
            "
          >
            <span
              :class="{
                active:
                  weeklyCellHours(detail, day.dutyDate, member.userId) > 0,
              }"
            >
              {{
                displayHours(
                  weeklyCellHours(detail, day.dutyDate, member.userId),
                )
              }}
            </span>
          </td>
          <td class="weekly-training-cell">
            {{ displayHours(member.trainingHours) }}
          </td>
          <td class="weekly-total-cell">
            <strong>{{ displayHours(member.totalHours) }}</strong>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
    <ol class="weekly-stats-mobile" aria-label="成员每周值班统计">
      <li v-for="member in detail.users" :key="member.userId">
        <details class="weekly-stats-mobile-record">
          <summary>
            <span class="weekly-stats-mobile-record__person"><strong>{{ member.name }}</strong><small>{{ member.studentNo }} · {{ member.grade || '年级未知' }} · {{ roleLabel(member.role) }}</small></span>
            <span class="weekly-stats-mobile-record__total"><strong>{{ displayAmount(member.totalHours) }}</strong><small>合计小时</small></span>
            <span class="weekly-stats-mobile-record__sub">值班 {{ displayAmount(member.attendanceHours) }} 小时 · 培训 {{ displayAmount(member.trainingHours) }} 小时</span>
            <span class="weekly-stats-mobile-record__toggle"><ChevronDown aria-hidden="true" /><span class="weekly-stats-mobile-record__show-label">查看详情</span><span class="weekly-stats-mobile-record__hide-label">收起详情</span></span>
          </summary>
          <dl class="weekly-stats-mobile-record__days">
            <div v-for="day in detail.days" :key="day.dutyDate" :data-heat="weeklyHeatLevel(weeklyCellHours(detail, day.dutyDate, member.userId))">
              <dt>{{ day.weekdayName }} <small>{{ day.dutyDate.slice(5) }}</small></dt>
              <dd>{{ displayHours(weeklyCellHours(detail, day.dutyDate, member.userId)) }}<small v-if="weeklyCellHours(detail, day.dutyDate, member.userId) > 0"> 小时</small></dd>
            </div>
          </dl>
          <button class="stats-detail-trigger weekly-stats-mobile-record__detail" type="button" :disabled="disabled" @click="$emit('select-member', member.userId)">查看成员详情</button>
        </details>
      </li>
    </ol>
  </section>
</template>

<script setup lang="ts">
import { ChevronDown } from "@lucide/vue";
import {
  weeklyCellHours,
  weeklyHeatLevel,
  type WeeklyStatsDetail,
} from "./weeklyStats";

defineProps<{ detail: WeeklyStatsDetail; disabled?: boolean }>();
defineEmits<{ 'select-member': [userId: number] }>();

function displayHours(value: unknown) {
  const numeric = Number(value || 0);
  return numeric ? displayAmount(numeric) : "—";
}

function displayAmount(value: unknown) {
  const numeric = Number(value || 0);
  return numeric.toFixed(numeric % 1 ? 1 : 0);
}

const roleLabel = (role: string) =>
  (
    {
      MEMBER: "成员",
      MINISTER: "部长",
      PRESIDENT: "会长",
      ADMIN: "管理员",
    } as Record<string, string>
  )[role] || role;
</script>
