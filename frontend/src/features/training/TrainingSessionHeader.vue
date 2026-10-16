<template>
  <aside class="training-session-overview" aria-labelledby="training-session-title">
    <div class="training-session-heading">
      <time :datetime="session.trainingDate">
        {{ readableDate(session.trainingDate) }} · {{ timeRange(session) }}
      </time>
      <h2 id="training-session-title">{{ session.title }}</h2>
    </div>

    <div class="training-session-overview-actions">
      <button data-button-kind="entry" class="button training-overview-edit" type="button" @click="$emit('edit')">
        <Pencil aria-hidden="true" />编辑培训
      </button>
      <ActionMenu :label="`${session.title}的更多操作`">
        <button
          class="danger-text"
          role="menuitem"
          type="button"
          :disabled="archivePending"
          @click="$emit('archive')"
        >
          <Archive aria-hidden="true" />归档培训
        </button>
      </ActionMenu>
    </div>

    <p v-if="session.description" class="training-session-description">{{ session.description }}</p>
    <dl class="training-session-facts">
      <div>
        <dt>主讲人</dt>
        <dd>{{ session.speaker || "未填写" }}</dd>
      </div>
      <div>
        <dt>培训地点</dt>
        <dd>{{ session.location || "未填写" }}</dd>
      </div>
      <div>
        <dt>培训时长</dt>
        <dd>{{ duration(session) }}</dd>
      </div>
      <div>
        <dt>参与人数</dt>
        <dd>{{ session.participantCount || 0 }} 人</dd>
      </div>
      <div>
        <dt>累计时长</dt>
        <dd>{{ hours(session.totalDurationHours) }} 小时</dd>
      </div>
    </dl>

  </aside>
</template>

<script setup lang="ts">
import {
  Archive,
  Pencil,
} from "@lucide/vue";
import ActionMenu from "../../shared/ui/ActionMenu.vue";
import type { TrainingSession } from "./trainingTypes";

defineProps<{
  session: TrainingSession;
  archivePending?: boolean;
}>();
defineEmits<{ edit: []; archive: [] }>();

function readableDate(value: string) {
  const [year, month, day] = value.split("-");
  return year && month && day ? `${year}年${Number(month)}月${Number(day)}日` : value;
}

function timeRange(value: Pick<TrainingSession, "startTime" | "endTime">) {
  return value.startTime && value.endTime
    ? `${value.startTime.slice(0, 5)}–${value.endTime.slice(0, 5)}`
    : "未设置";
}

function duration(value: Pick<TrainingSession, "startTime" | "endTime">) {
  if (!value.startTime || !value.endTime) return "未设置";
  const [startHour = 0, startMinute = 0] = value.startTime.split(":").map(Number);
  const [endHour = 0, endMinute = 0] = value.endTime.split(":").map(Number);
  const minutes = endHour * 60 + endMinute - startHour * 60 - startMinute;
  return minutes > 0 ? `${hours(minutes / 60)} 小时` : "未设置";
}

function hours(value: number | string | null | undefined) {
  return Number(value || 0).toFixed(2).replace(/\.00$/, "");
}
</script>
