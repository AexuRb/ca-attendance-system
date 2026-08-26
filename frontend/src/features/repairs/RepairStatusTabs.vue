<template>
  <div class="repair-status-tabs" role="tablist" aria-label="维修事务状态">
    <button
      v-for="item in statuses"
      :key="item.status"
      ref="tabButtons"
      type="button"
      role="tab"
      :id="`repair-tab-${item.status}`"
      :aria-controls="`repair-panel-${item.status}`"
      :class="{ active: activeStatus === item.status }"
      :aria-selected="activeStatus === item.status"
      :tabindex="activeStatus === item.status ? 0 : -1"
      @click="$emit('change', item.status)"
      @keydown="handleKeydown($event, item.status)"
    >
      <span class="repair-status-copy">
        <strong>{{ item.label }}</strong>
      </span>
      <b>{{ count(item.status) }}</b>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import type { RepairStatus, RepairStatusCounts } from "./repairTypes";

const props = defineProps<{
  activeStatus: RepairStatus;
  counts: RepairStatusCounts;
}>();
const emit = defineEmits<{ change: [status: RepairStatus] }>();
const tabButtons = ref<HTMLButtonElement[]>([]);

const statuses: Array<{
  status: RepairStatus;
  label: string;
}> = [
  { status: "REPAIRING", label: "进行中" },
  { status: "COMPLETED", label: "已完成" },
  { status: "CANCELED", label: "已取消" },
];

function count(status: RepairStatus) {
  return new Intl.NumberFormat("zh-CN").format(props.counts[status] || 0);
}

function handleKeydown(event: KeyboardEvent, status: RepairStatus) {
  const current = statuses.findIndex((item) => item.status === status);
  let target = current;
  if (event.key === "ArrowRight") target = (current + 1) % statuses.length;
  else if (event.key === "ArrowLeft") target = (current - 1 + statuses.length) % statuses.length;
  else if (event.key === "Home") target = 0;
  else if (event.key === "End") target = statuses.length - 1;
  else return;
  const nextStatus = statuses[target];
  if (!nextStatus) return;
  event.preventDefault();
  emit("change", nextStatus.status);
  tabButtons.value[target]?.focus();
}
</script>
