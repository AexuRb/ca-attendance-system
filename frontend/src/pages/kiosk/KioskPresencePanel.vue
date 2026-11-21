<template>
  <footer class="kiosk-presence" aria-labelledby="kiosk-presence-title">
    <h2 id="kiosk-presence-title">
      当前在场
      <span v-if="presence">{{ presence.total }} 人</span>
    </h2>
    <RouterLink
      data-button-kind="entry"
      class="kiosk-signal-admin"
      :to="{ name: 'login' }"
      title="后台登录"
    >
      <Settings2 aria-hidden="true" />
      <span>后台</span>
    </RouterLink>
    <div class="kiosk-presence-content">
      <p v-if="failed" role="status">{{ presence ? '名单暂未更新' : '暂时无法读取在场名单' }}</p>
      <p v-else-if="!presence" role="status">正在读取在场名单</p>
      <p v-else-if="!presence.total">当前暂无在场人员</p>
      <ul v-if="presence?.names.length" aria-label="在场人员">
        <li v-for="(name, index) in presence.names" :key="index">{{ name }}</li>
      </ul>
      <p v-if="presence && presence.total > presence.names.length">
        {{ presence.names.length ? `其中 ${presence.total - presence.names.length} 人已列在当前排班中` : '在场人员已列在当前排班中' }}
      </p>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { Settings2 } from "@lucide/vue";
import { RouterLink } from "vue-router";
import type { KioskPresence } from "../../features/kiosk/types";

defineProps<{ presence: KioskPresence | null; failed: boolean }>();
</script>
