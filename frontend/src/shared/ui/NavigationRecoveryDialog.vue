<template>
  <ModalDialog
    :open="!!failure"
    :title="confirmReload ? '重新加载应用？' : '页面未能打开'"
    size="sm"
    @close="close"
  >
    <p v-if="confirmReload" class="confirm-copy">
      重新加载将丢失未保存的输入和内存中的搜索条件，已保存的数据不受影响。
      请确认本机服务已恢复，再重新加载。
    </p>
    <p v-else class="confirm-copy">
      {{ failure?.hasCurrentPage ? '当前页面与输入已保留。' : '页面资源暂时无法加载。' }}
      请确认本机服务已启动，再重试打开；
      如果仍然失败，可以重新加载应用。
    </p>
    <template #footer>
      <template v-if="confirmReload">
        <button ref="cancelReloadButton" class="button secondary" type="button" @click="confirmReload = false">取消</button>
        <button class="button primary" type="button" :disabled="pending" @click="recovery?.reload()">确认重新加载</button>
      </template>
      <template v-else>
        <button class="button text" type="button" @click="close">暂不打开</button>
        <button class="button secondary" type="button" :disabled="pending" @click="confirmReload = true">重新加载应用</button>
        <button ref="retryButton" class="button primary" type="button" :disabled="pending" @click="recovery?.retry()">重试打开</button>
      </template>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { inject, nextTick, ref, watch } from "vue";
import { navigationRecoveryKey } from "../../app/navigationRecovery";
import ModalDialog from "./ModalDialog.vue";

const recovery = inject(navigationRecoveryKey, undefined);
const failure = recovery?.failure ?? ref(null);
const pending = recovery?.pending ?? ref(false);
const confirmReload = ref(false);
const cancelReloadButton = ref<HTMLButtonElement | null>(null);
const retryButton = ref<HTMLButtonElement | null>(null);
watch(failure, () => { confirmReload.value = false; });
watch(confirmReload, async () => {
  await nextTick();
  (confirmReload.value ? cancelReloadButton.value : retryButton.value)?.focus();
});
function close() {
  if (confirmReload.value) confirmReload.value = false;
  else recovery?.dismiss();
}
</script>
