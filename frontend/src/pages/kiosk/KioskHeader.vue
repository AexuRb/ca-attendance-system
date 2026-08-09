<template>
  <header class="kiosk-focus-header">
    <div class="kiosk-focus-brand">
      <span class="kiosk-focus-brand-mark">
        <img src="/brand/ca-logo-white.png" alt="计算机协会会徽" />
      </span>
      <div>
        <strong>计算机协会值班签到台</strong>
        <span>LOCAL DUTY KIOSK</span>
      </div>
    </div>

    <div class="kiosk-focus-header-actions">
      <ServiceStatus
        class="kiosk-focus-service"
        :online="online"
        online-label="本机服务正常"
        offline-label="连接中断，正在重试"
      />
      <div class="kiosk-focus-time">
        <time class="kiosk-focus-clock" :datetime="clockIso">{{ clock }}</time>
        <span>{{ date }}</span>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from "vue";
import ServiceStatus from "../../shared/ui/ServiceStatus.vue";

const props = defineProps<{ online: boolean; now: Date }>();

const clock = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(props.now),
);
const date = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(props.now),
);
const clockIso = computed(() => props.now.toISOString());
</script>
