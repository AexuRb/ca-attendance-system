<template>
  <StartupStatus v-if="!state.ready" />
  <RouterView v-else v-slot="{ Component, route }">
    <template v-if="route.meta.auth">
      <component v-if="isAuthenticated" :is="Component" />
      <LoginPage v-else />
    </template>
    <Transition v-else name="route" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>
  <ToastHost />
  <NavigationRecoveryDialog />
</template>

<script setup lang="ts">
import { RouterView } from "vue-router";
import ToastHost from "./shared/ui/ToastHost.vue";
import LoginPage from "./pages/auth/LoginPage.vue";
import { useSession } from "./app/session";
import NavigationRecoveryDialog from "./shared/ui/NavigationRecoveryDialog.vue";
import StartupStatus from "./shared/ui/StartupStatus.vue";

// A failed or cancelled redirect must never keep a signed-out workspace visible.
const { isAuthenticated, state } = useSession();
</script>
