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
            <strong>{{ member.name }}</strong>
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
  </section>
</template>

<script setup lang="ts">
import {
  weeklyCellHours,
  weeklyHeatLevel,
  type WeeklyStatsDetail,
} from "./weeklyStats";

defineProps<{ detail: WeeklyStatsDetail }>();

function displayHours(value: unknown) {
  const numeric = Number(value || 0);
  return numeric ? numeric.toFixed(numeric % 1 ? 1 : 0) : "—";
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
