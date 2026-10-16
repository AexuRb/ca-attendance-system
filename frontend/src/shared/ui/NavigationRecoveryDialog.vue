<template>
  <ModalDialog
    :open="!!failure"
    :title="confirmReload ? '重新加载应用' : '页面加载失败'"
    size="sm"
    backdrop-class="navigation-recovery-backdrop"
    @close="close"
  >
    <template #title-icon><CircleAlert aria-hidden="true" /></template>
    <div v-if="confirmReload" class="recovery-copy">
      <p>未保存的输入和当前搜索条件将被清除。</p>
      <p class="recovery-detail">已保存的数据不受影响。</p>
    </div>
    <div v-else class="recovery-copy">
      <p>{{ failure?.hasCurrentPage ? '当前页面和未保存的输入已保留。' : '页面暂时无法加载。' }}</p>
      <p class="recovery-detail">请重试加载；若仍无法打开，可重新加载应用。</p>
    </div>
    <template #footer>
      <template v-if="confirmReload">
        <button ref="cancelReloadButton" class="button secondary" type="button" data-dialog-initial-focus @click="confirmReload = false">取消</button>
        <button class="button primary" type="button" :disabled="pending" @click="recovery?.reload()">重新加载</button>
      </template>
      <template v-else>
        <button class="button text recovery-dismiss" type="button" @click="close">{{ failure?.hasCurrentPage ? '留在当前页面' : '关闭' }}</button>
        <button class="button secondary" type="button" :disabled="pending" @click="confirmReload = true">重新加载应用</button>
        <button data-button-kind="entry" ref="retryButton" class="button primary" type="button" data-dialog-initial-focus :disabled="pending" @click="recovery?.retry()">重试加载</button>
      </template>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { inject, nextTick, ref, watch } from "vue";
import { CircleAlert } from "@lucide/vue";
import { navigationRecoveryKey } from "../../app/navigationRecovery";
import ModalDialog from "./ModalDialog.vue";
import "./navigation-recovery.css";

const recovery = inject(navigationRecoveryKey, undefined);
const failure = recovery?.failure ?? ref(null);
const pending = recovery?.pending ?? ref(false);
const confirmReload = ref(false);
const cancelReloadButton = ref<HTMLButtonElement | null>(null);
const retryButton = ref<HTMLButtonElement | null>(null);
watch(failure, () => { confirmReload.value = false; });
watch([failure, pending], async ([currentFailure, isPending]) => {
  if (!currentFailure || isPending) return;
  await nextTick();
  if (failure.value === currentFailure && !pending.value && !confirmReload.value) retryButton.value?.focus();
});
watch(confirmReload, async () => {
  await nextTick();
  (confirmReload.value ? cancelReloadButton.value : retryButton.value)?.focus();
});
function close() {
  if (confirmReload.value) confirmReload.value = false;
  else recovery?.dismiss();
}
</script>
