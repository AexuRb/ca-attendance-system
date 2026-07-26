<template>
  <span
    class="service-status"
    :class="{ compact }"
    :data-online="online"
    role="status"
    :aria-label="label"
  >
    <i aria-hidden="true"></i>
    <span>{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    online: boolean;
    onlineLabel?: string;
    offlineLabel?: string;
    compact?: boolean;
  }>(),
  {
    onlineLabel: "本机服务正常",
    offlineLabel: "连接中断，正在重试",
    compact: false,
  },
);

const label = computed(() =>
  props.online ? props.onlineLabel : props.offlineLabel,
);
</script>
