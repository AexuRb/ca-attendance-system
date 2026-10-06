<template>
  <ModalDialog :open="open" :title="member ? `${member.name}的统计详情` : '统计详情'" size="lg" @close="$emit('close')">
    <template v-if="member">
      <p class="stats-detail-meta">{{ member.studentNo }} · {{ member.grade || '年级未知' }} · {{ roleLabel(member.role) }}</p>
      <p class="stats-detail-range">统计范围 {{ from }} — {{ to }}</p>
      <dl class="stats-detail-summary">
        <div><dt>合计时长</dt><dd>{{ number(member.totalHours) }}<small>小时</small></dd></div>
        <div><dt>有效值班</dt><dd>{{ number(member.attendanceHours ?? member.dutyHours) }}<small>小时</small></dd></div>
        <div><dt>培训时长</dt><dd>{{ number(member.trainingHours) }}<small>小时</small></dd></div>
        <div><dt>有效记录次数</dt><dd>{{ effectiveDutyCount(member) }}<small>次</small></dd></div>
      </dl>
      <section class="stats-detail-daily" aria-label="每日有效值班时长">
        <h3>有效值班明细 <small v-if="activeDays.length">{{ activeDays.length }} 天</small></h3>
        <p class="stats-detail-hint">有效记录次数包含有效值班记录与培训参与；下方仅列有效值班日期，培训时长在上方单独汇总。</p>
        <LoadingBlock v-if="loading" />
        <div v-else-if="error" class="inline-alert danger" role="alert">
          <span>{{ error }}</span><button class="button secondary small" type="button" @click="$emit('retry')">重试</button>
        </div>
        <p v-else-if="!activeDays.length" class="stats-detail-empty">该范围内没有有效值班记录。</p>
        <div v-else class="stats-detail-groups">
          <section v-for="group in monthGroups" :key="group.key">
            <h4 v-if="monthGroups.length > 1">{{ group.label }} <small>有效值班 {{ number(group.hours) }} 小时</small></h4>
            <ol class="stats-detail-days">
              <li v-for="day in group.days" :key="day.dutyDate">
                <span><time :datetime="day.dutyDate">{{ day.dutyDate }}</time><small>{{ day.weekdayName }}</small></span>
                <strong>{{ number(weeklyCellHours(detail!, day.dutyDate, member.userId!)) }} <small>小时</small></strong>
              </li>
            </ol>
          </section>
        </div>
      </section>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import { effectiveDutyCount, type StatsSummaryRow } from "./statsSummary";
import { weeklyCellHours, type WeeklyStatsDay, type WeeklyStatsDetail } from "./weeklyStats";

const props = defineProps<{
  open: boolean;
  member: StatsSummaryRow | null;
  detail: WeeklyStatsDetail | null;
  from: string;
  to: string;
  loading: boolean;
  error: string;
}>();
defineEmits<{ close: []; retry: [] }>();

const activeDays = computed(() => props.detail?.days.filter(day =>
  props.member?.userId != null && weeklyCellHours(props.detail!, day.dutyDate, props.member.userId) > 0,
) || []);
const monthGroups = computed(() => {
  const groups = new Map<string, { key: string; label: string; hours: number; days: WeeklyStatsDay[] }>();
  for (const day of activeDays.value) {
    const key = day.dutyDate.slice(0, 7);
    let group = groups.get(key);
    if (!group) {
      group = { key, label: `${key.slice(0, 4)}年${Number(key.slice(5))}月`, hours: 0, days: [] };
      groups.set(key, group);
    }
    group.days.push(day);
    group.hours += weeklyCellHours(props.detail!, day.dutyDate, props.member!.userId!);
  }
  return [...groups.values()];
});
const number = (value: number | string | null | undefined) => {
  const numeric = Number(value || 0);
  return numeric.toFixed(numeric % 1 ? 1 : 0);
};
const roleLabel = (role: string) => ({ MEMBER: "成员", MINISTER: "部长", PRESIDENT: "会长", ADMIN: "管理员" })[role as "MEMBER" | "MINISTER" | "PRESIDENT" | "ADMIN"] || role;
</script>
