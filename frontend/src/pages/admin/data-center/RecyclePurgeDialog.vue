<template>
  <ModalDialog :open="Boolean(item)" title="彻底删除维修事务" size="sm" backdrop-class="recycle-refined-backdrop" @close="cancel">
    <p class="confirm-copy">彻底删除后，无法从回收站恢复。系统会先自动备份，再删除这条维修事务。</p>
    <p class="recycle-purge-reference">确认编号：<strong>{{ item?.caseNo }}</strong></p>
    <label class="field">
      <span>维修编号</span>
      <input v-model="caseNo" name="confirmReason" autocomplete="off" placeholder="输入要彻底删除的维修编号"
        :disabled="pending" :aria-invalid="Boolean(mismatch)" :aria-describedby="mismatch ? errorId : undefined" />
    </label>
    <p v-if="mismatch" :id="errorId" class="recycle-field-error" role="alert">编号不匹配，请输入上方完整维修编号。</p>
    <template #footer>
      <button class="button secondary" type="button" :disabled="pending" @click="cancel">取消</button>
      <button class="button danger" type="button" :disabled="pending || !matches" @click="confirm">{{ pending ? '处理中...' : '彻底删除' }}</button>
    </template>
  </ModalDialog>
</template>
<script setup lang="ts">
import { computed, ref, useId, watch } from "vue";
import ModalDialog from "../../../shared/ui/ModalDialog.vue";
import type { RecycledRepairCase } from "../../../features/maintenance/dataCenterTypes";
const props = defineProps<{ item: RecycledRepairCase | null; pending: boolean }>();
const emit = defineEmits<{ cancel: []; confirm: [value: string] }>();
const caseNo = ref("");
const errorId = useId();
const matches = computed(() => Boolean(props.item && caseNo.value.trim() === props.item.caseNo));
const mismatch = computed(() => Boolean(caseNo.value.trim() && !matches.value));
watch(() => props.item?.id, () => { caseNo.value = ""; });
function cancel() { if (!props.pending) emit("cancel"); }
function confirm() { if (!props.pending && matches.value) emit("confirm", caseNo.value.trim()); }
</script>
