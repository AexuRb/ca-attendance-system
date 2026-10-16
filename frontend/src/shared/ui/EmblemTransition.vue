<template><span ref="anchor" hidden aria-hidden="true"></span></template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { useSession } from "../../app/session";
import { createEmblemTransition, type EmblemKind } from "../motion/emblemTransition";
import "../motion/emblem-transition.css";
const anchor = ref<HTMLElement | null>(null);
const router = useRouter();
const { state, isAuthenticated } = useSession();
let transition: ReturnType<typeof createEmblemTransition> | undefined;
function kindFor(route: typeof router.currentRoute.value) {
  let kind: EmblemKind | undefined;
  if (!state.ready || (route.meta.auth && !isAuthenticated.value) || ["login", "setup", "password", "credits-editor"].includes(String(route.name))) kind = "large";
  else if (route.name === "kiosk" || route.path.startsWith("/admin")) kind = "small";
  return kind;
}
const removeGuard = router.beforeResolve(route => { transition?.prepare(kindFor(route)); });
// Authentication can replace private content before any navigation succeeds.
watch([() => state.ready, isAuthenticated], () => transition?.prepare(kindFor(router.currentRoute.value)), { flush: "sync" });
onMounted(() => {
  const root = anchor.value?.parentElement;
  if (root) transition = createEmblemTransition(root);
});
onBeforeUnmount(() => { removeGuard(); transition?.destroy(); });
</script>
