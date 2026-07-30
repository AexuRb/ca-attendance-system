<template>
  <div
    ref="root"
    class="action-menu"
    @keydown="onKeydown"
  >
    <button
      ref="trigger"
      :class="triggerText ? 'button secondary' : 'icon-button'"
      type="button"
      :aria-label="label"
      :title="label"
      aria-haspopup="menu"
      :aria-expanded="open"
      @click="toggle"
    >
      <template v-if="triggerText">
        {{ triggerText }}<ChevronDown aria-hidden="true" />
      </template>
      <MoreHorizontal v-else aria-hidden="true" />
    </button>
  </div>
  <Teleport to="body">
    <Transition name="menu-pop">
      <div
        v-if="open"
        ref="menu"
        class="action-menu-popover"
        :style="menuStyle"
        role="menu"
        @click="onMenuClick"
        @keydown="onKeydown"
      >
        <slot />
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, type CSSProperties } from "vue";
import { ChevronDown, MoreHorizontal } from "@lucide/vue";

const props = withDefaults(
  defineProps<{
    label?: string;
    align?: "start" | "end";
    triggerText?: string;
  }>(),
  {
    label: "更多操作",
    align: "end",
  },
);

const root = ref<HTMLElement | null>(null);
const trigger = ref<HTMLButtonElement | null>(null);
const menu = ref<HTMLElement | null>(null);
const open = ref(false);
const menuStyle = ref<CSSProperties>({});

function menuItems() {
  return Array.from(
    menu.value?.querySelectorAll<HTMLElement>(
      '[role="menuitem"]:not(:disabled)',
    ) || [],
  );
}

async function toggle() {
  open.value = !open.value;
  if (!open.value) return;
  document.addEventListener("pointerdown", onOutsidePointerDown);
  window.addEventListener("resize", positionMenu);
  window.addEventListener("scroll", positionMenu, true);
  await nextTick();
  positionMenu();
  menuItems()[0]?.focus();
}

function close(restoreFocus = false) {
  if (!open.value) return;
  open.value = false;
  document.removeEventListener("pointerdown", onOutsidePointerDown);
  window.removeEventListener("resize", positionMenu);
  window.removeEventListener("scroll", positionMenu, true);
  if (restoreFocus) void nextTick(() => trigger.value?.focus());
}

function onOutsidePointerDown(event: PointerEvent) {
  const target = event.target as Node;
  if (!root.value?.contains(target) && !menu.value?.contains(target)) close();
}

function onMenuClick(event: MouseEvent) {
  const target = (event.target as HTMLElement).closest('[role="menuitem"]');
  if (target && !(target as HTMLButtonElement).disabled) close();
}

function onKeydown(event: KeyboardEvent) {
  if (!open.value) return;
  if (event.key === "Escape") {
    event.preventDefault();
    close(true);
    return;
  }
  if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;

  const items = menuItems();
  if (!items.length) return;
  event.preventDefault();
  const activeIndex = items.indexOf(document.activeElement as HTMLElement);
  if (event.key === "Home") items[0].focus();
  else if (event.key === "End") items.at(-1)?.focus();
  else if (event.key === "ArrowDown")
    items[(activeIndex + 1 + items.length) % items.length].focus();
  else
    items[(activeIndex - 1 + items.length) % items.length].focus();
}

function positionMenu() {
  if (!trigger.value || !menu.value) return;
  const triggerRect = trigger.value.getBoundingClientRect();
  const menuRect = menu.value.getBoundingClientRect();
  const margin = 8;
  const gap = 6;
  const desiredLeft =
    props.align === "end"
      ? triggerRect.right - menuRect.width
      : triggerRect.left;
  const left = Math.min(
    window.innerWidth - menuRect.width - margin,
    Math.max(margin, desiredLeft),
  );
  const opensUp =
    triggerRect.bottom + gap + menuRect.height > window.innerHeight - margin &&
    triggerRect.top - gap - menuRect.height >= margin;
  const top = opensUp
    ? triggerRect.top - gap - menuRect.height
    : triggerRect.bottom + gap;
  menuStyle.value = {
    top: `${Math.max(margin, top)}px`,
    left: `${left}px`,
  };
}

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", onOutsidePointerDown);
  window.removeEventListener("resize", positionMenu);
  window.removeEventListener("scroll", positionMenu, true);
});
</script>
