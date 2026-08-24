<template>
  <Teleport to="body">
    <Transition name="training-drawer">
      <div
        v-if="open"
        class="training-drawer-backdrop"
        @mousedown.self="$emit('close')"
      >
        <aside
          ref="dialog"
          class="training-session-drawer"
          role="dialog"
          aria-modal="true"
          aria-labelledby="training-drawer-title"
          tabindex="-1"
        >
          <header>
            <div>
              <p class="eyebrow">SESSIONS</p>
              <h2 id="training-drawer-title">切换培训场次</h2>
            </div>
            <button
              class="icon-button"
              type="button"
              aria-label="关闭场次目录"
              title="关闭"
              @click="$emit('close')"
            >
              <X aria-hidden="true" />
            </button>
          </header>
          <TrainingSessionList
            :items="items"
            :selected-id="selectedId"
            :total="total"
            :page="page"
            :page-size="pageSize"
            :has-more="hasMore"
            :loading="loading"
            :error="error"
            @select="choose"
            @page="$emit('page', $event)"
            @retry="$emit('retry')"
          />
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { X } from "@lucide/vue";
import { useDialogFocus } from "../../shared/ui/useDialogFocus";
import TrainingSessionList from "./TrainingSessionList.vue";
import type { TrainingSession } from "./trainingTypes";

const props = defineProps<{
  open: boolean;
  items: TrainingSession[];
  selectedId: number | null;
  total: number;
  page: number;
  pageSize: number;
  hasMore: boolean;
  loading: boolean;
  error: string;
}>();

const emit = defineEmits<{
  close: [];
  select: [session: TrainingSession];
  page: [page: number];
  retry: [];
}>();
const dialog = ref<HTMLElement | null>(null);
useDialogFocus({
  root: dialog,
  open: () => props.open,
  close: () => emit("close"),
});

function choose(session: TrainingSession) {
  emit("select", session);
  emit("close");
}
</script>
