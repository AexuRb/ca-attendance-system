<template>
  <div class="repair-status-tabs" role="tablist" aria-label="维修事务状态">
    <button
      v-for="item in statuses"
      :key="item.status"
      type="button"
      role="tab"
      :class="{ active: activeStatus === item.status }"
      :aria-selected="activeStatus === item.status"
      @click="$emit('change', item.status)"
    >
      <span class="repair-status-marker" :data-tone="item.tone" aria-hidden="true" />
      <span class="repair-status-copy">
        <strong>{{ item.label }}</strong>
        <small>{{ item.description }}</small>
      </span>
      <b>{{ count(item.status) }}</b>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { RepairStatus, RepairStatusCounts } from "./repairTypes";

const props = defineProps<{
  activeStatus: RepairStatus;
  counts: RepairStatusCounts;
}>();
defineEmits<{ change: [status: RepairStatus] }>();

const statuses: Array<{
  status: RepairStatus;
  label: string;
  description: string;
  tone: string;
}> = [
  { status: "REPAIRING", label: "进行中", description: "当前工作队列", tone: "blue" },
  { status: "COMPLETED", label: "已完成", description: "交付归档", tone: "green" },
  { status: "CANCELED", label: "已取消", description: "终止记录", tone: "gray" },
];

function count(status: RepairStatus) {
  return new Intl.NumberFormat("zh-CN").format(props.counts[status] || 0);
}
</script>
