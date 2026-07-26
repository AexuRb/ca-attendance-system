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
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import ServiceStatus from "../../shared/ui/ServiceStatus.vue";

defineProps<{ online: boolean }>();

const now = ref(new Date());
let clockTimer: number | undefined;
const clock = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(now.value),
);
const date = computed(() =>
  new Intl.DateTimeFormat("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(now.value),
);
const clockIso = computed(() => now.value.toISOString());

onMounted(() => {
  clockTimer = window.setInterval(() => {
    now.value = new Date();
  }, 30000);
});
onBeforeUnmount(() => window.clearInterval(clockTimer));
</script>
