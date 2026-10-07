<template>
  <ModalDialog side-panel :open="open" :title="member ? `${member.name}的统计详情` : '统计详情'" size="lg" @close="$emit('close')">
    <template v-if="member">
      <p class="stats-detail-meta">{{ member.studentNo }} · {{ member.grade || '年级未知' }} · {{ roleLabel(member.role) }}</p>
      <p class="stats-detail-range">统计范围 {{ from }} — {{ to }}</p>
      <div class="stats-detail-navigation">
        <button class="button secondary small" :disabled="!hasPrevious" @click="$emit('previous')">上一位</button>
        <button class="button secondary small" :disabled="!hasNext" @click="$emit('next')">下一位</button>
        <button class="button primary small" @click="$emit('records')">查看此范围值班记录</button>
        <small>从值班记录返回可恢复当前统计定位</small>
      </div>
      <dl class="stats-detail-summary">
        <div><dt>合计时长</dt><dd>{{ number(member.totalHours) }}<small>小时</small></dd></div>
        <div><dt>有效值班</dt><dd>{{ number(member.attendanceHours ?? member.dutyHours) }}<small>小时</small></dd></div>
        <div><dt>培训时长</dt><dd>{{ number(member.trainingHours) }}<small>小时</small></dd></div>
        <div><dt>有效记录次数</dt><dd>{{ effectiveDutyCount(member) }}<small>次</small></dd></div>
      </dl>
      <section class="stats-detail-daily" aria-label="每日有效值班时长">
        <h3>有效值班明细 <small v-if="activeDays.length">{{ activeDays.length }} 天</small></h3>
        <p class="stats-detail-hint">有效记录次数包含有效值班记录与培训参与；值班按日期汇总，培训按实际计入时长列出来源。</p>
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
                <button class="button text small" @click="$emit('records', day.dutyDate)">核对记录</button>
              </li>
            </ol>
          </section>
        </div>
      </section>
      <section v-if="detail" class="stats-detail-training">
        <h3>培训来源</h3>
        <p v-if="!detail.trainingVisible">当前权限仅可查看培训汇总。</p>
        <p v-else-if="!detail.training.length">该范围内没有计入统计的培训。</p>
        <ol v-else><li v-for="(item, index) in detail.training" :key="index"><time>{{ item.trainingDate }}</time> · {{ item.title }} · {{ number(item.durationHours) }} 小时</li></ol>
      </section>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import { effectiveDutyCount, type StatsSummaryRow } from "./statsSummary";
import { weeklyCellHours, type WeeklyStatsDay } from "./weeklyStats";

import type { MemberStatsDetail } from "./useStatsWorkspace";

const props = defineProps<{
  open: boolean;
  member: StatsSummaryRow | null;
  detail: MemberStatsDetail | null;
  from: string;
  to: string;
  loading: boolean;
  error: string;
  hasPrevious?: boolean;
  hasNext?: boolean;
}>();
defineEmits<{ close: []; retry: []; previous: []; next: []; records: [day?: string] }>();

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
