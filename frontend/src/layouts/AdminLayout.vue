<template>
  <RouterView v-slot="{ Component }">
    <Transition :css="false" mode="out-in" @leave="changeWorkspace">
      <component :is="Component" />
    </Transition>
  </RouterView>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, provide } from "vue";
import { RouterView } from "vue-router";
import { sidebarHandoffKey, type SidebarHandoff } from "../app/adminNavigation";

const sidebarHandoff: SidebarHandoff = {};
provide(sidebarHandoffKey, sidebarHandoff);
const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)");
const root = document.documentElement;
let activeSlide: ViewTransition | undefined;

function stopSlide() {
  const previous = activeSlide;
  activeSlide = undefined;
  previous?.skipTransition();
  delete root.dataset.workspaceSlide;
}

function changeWorkspace(shell: Element, done: () => void) {
  stopSlide();
  const navigation = shell.querySelector<HTMLElement>(".overview-navigation");
  const from = navigation?.querySelector<HTMLElement>('[data-nav-key][aria-current="page"]')?.dataset.navKey;
  sidebarHandoff.previous = from && navigation ? { page: from, scrollTop: navigation.scrollTop } : undefined;
  const content = shell.querySelector<HTMLElement>(".overview-content");
  if (!from || !content || reducedMotion?.matches || document.hidden || !document.startViewTransition) {
    // Vue must finish patching the out-in placeholder before mounting the destination.
    queueMicrotask(done);
    return;
  }

  content.inert = true;
  root.dataset.workspaceSlide = "pending";
  try {
    const slide = document.startViewTransition(async () => {
      done();
      await nextTick();
      if (activeSlide !== slide) return;
      const links = [...document.querySelectorAll<HTMLElement>(".overview-navigation [data-nav-key]")];
      const source = links.findIndex(link => link.dataset.navKey === from);
      const target = links.findIndex(link => link.getAttribute("aria-current") === "page");
      if (source < 0 || target < 0 || source === target) {
        slide.skipTransition();
        return;
      }
      root.dataset.workspaceSlide = target > source ? "forward" : "backward";
    });
    activeSlide = slide;
    void slide.ready.catch(() => {});
    const clear = () => { if (activeSlide === slide) stopSlide(); };
    void slide.finished.then(clear, clear);
  } catch {
    stopSlide();
    queueMicrotask(done);
  }
}

onMounted(() => reducedMotion?.addEventListener("change", stopSlide));
onBeforeUnmount(() => {
  reducedMotion?.removeEventListener("change", stopSlide);
  stopSlide();
});
</script>

<style src="../shared/motion/workspace-slide.css"></style>
