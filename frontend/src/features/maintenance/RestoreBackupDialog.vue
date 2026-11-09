<template>
  <ModalDialog :error="submitError" :busy="busy"
    :open="open"
    :backdrop-class="backdropClass"
    title="恢复本机备份"
    size="sm"
    @close="cancel"
  >
    <p class="confirm-copy">
      即将使用 <strong>{{ file?.name }}</strong> 替换当前系统数据。
    </p>
    <div class="inline-alert danger" role="alert">
      恢复前系统会自动创建安全备份；恢复完成后，所有账号都需要重新登录。
    </div>
    <p v-if="checking" role="status">正在校验备份…</p>
    <div v-if="checkError" class="inline-alert danger" role="alert">
      <span>{{ checkError }}</span><button class="button secondary small" type="button" :disabled="busy || checking" @click="checkFile">重新校验</button>
    </div>
    <section v-if="review && problemCount" class="restore-contact-review" aria-label="备份联系方式校验结果">
      <p>恢复时将清空 {{ review.phoneCount }} 项错误电话、{{ review.qqCount }} 项错误QQ，原备份不变。</p>
      <details :key="review.fileHash" class="restore-contact-details">
        <summary>查看错误明细</summary>
        <ul class="restore-contact-issues">
          <li v-for="issue in review.issues" :key="`${issue.rowNumber}-${issue.field}`">成员第{{ issue.rowNumber }}行 · {{ issue.field }}：{{ issue.reason }}</li>
        </ul>
        <p v-if="review.truncated">仅列出前100项；确认后共清空 {{ problemCount }} 项。</p>
      </details>
      <label class="restore-contact-consent"><input v-model="clearContacts" type="checkbox" :disabled="busy" />同意清空上述错误联系方式后恢复</label>
    </section>
    <p v-else-if="review" role="status">校验通过。</p>
    <label class="field">
      <span>请输入“恢复”确认操作</span>
      <input
        v-model="confirmation"
        name="restoreConfirmation"
        autocomplete="off"
        placeholder="恢复"
        @keyup.enter="confirm"
      />
    </label>
    <template #footer>
      <button
        class="button secondary"
        type="button"
        :disabled="busy"
        @click="cancel"
      >
        取消
      </button>
      <button
        class="button danger"
        type="button"
        :disabled="busy || checking || !review || !!checkError || (problemCount > 0 && !clearContacts) || confirmation.trim() !== requiredText"
        @click="confirm"
      >
        {{ busy ? "正在恢复" : "确认恢复" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { api } from "../../shared/api";
import type { RestoreContactReview, RestoreConfirmation } from "./restoreContacts";

const requiredText = "恢复";
const confirmation = ref("");
const props = defineProps<{
  open: boolean;
  file: File | null;
  busy: boolean; submitError?: string;
  backdropClass?: string;
}>();
const emit = defineEmits<{ cancel: []; confirm: [options: RestoreConfirmation] }>();
const review = ref<RestoreContactReview | null>(null);
const checking = ref(false), checkError = ref(""), clearContacts = ref(false);
const problemCount = computed(() => (review.value?.phoneCount ?? 0) + (review.value?.qqCount ?? 0));
let controller: AbortController | undefined;

async function checkFile() {
  controller?.abort();
  review.value = null; checkError.value = ""; clearContacts.value = false;
  if (!props.open || !props.file) { checking.value = false; return; }
  const active = controller = new AbortController(); checking.value = true;
  const body = new FormData(); body.append("file", props.file);
  try {
    const result = await api<RestoreContactReview>("/api/maintenance/backups/restore/preview", { method: "POST", body, signal: active.signal });
    if (!active.signal.aborted) review.value = result;
  } catch (cause) {
    if (!active.signal.aborted) checkError.value = cause instanceof Error ? cause.message : "备份校验失败，请重试";
  } finally { if (controller === active) checking.value = false; }
}

watch(
  [() => props.open, () => props.file],
  () => {
    confirmation.value = "";
    void checkFile();
  },
  { immediate: true },
);
onBeforeUnmount(() => controller?.abort());

function confirm() {
  if (!props.busy && !checking.value && review.value && !checkError.value
      && (!problemCount.value || clearContacts.value) && confirmation.value.trim() === requiredText) {
    emit("confirm", { clearInvalidContacts: problemCount.value > 0 && clearContacts.value, reviewHash: review.value.fileHash });
  }
}

function cancel() {
  if (!props.busy) emit("cancel");
}
</script>

<style scoped>
.restore-contact-review { margin:16px 0; font-size:13px; line-height:1.7; }
.restore-contact-issues { max-height:160px; overflow:auto; padding-left:20px; }
.restore-contact-details { margin:8px 0 12px; }
.restore-contact-details summary { width:fit-content; cursor:pointer; color:var(--mw-accent,var(--primary)); }
.restore-contact-consent { display:flex; align-items:flex-start; gap:8px; }
.restore-contact-consent input { flex:0 0 auto; margin-top:5px; }
</style>
