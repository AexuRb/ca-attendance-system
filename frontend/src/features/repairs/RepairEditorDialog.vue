<template>
  <ModalDialog
    :open="open"
    :title="form.id ? '编辑维修事务' : '新建维修事务'"
    size="lg"
    @close="close"
  >
    <form id="repair-editor-form" novalidate @submit.prevent="submit">
      <nav class="repair-editor-steps" aria-label="维修事务填写步骤">
        <button type="button" :class="{ active: step === 1 }" :aria-current="step === 1 ? 'step' : undefined" @click="step = 1">
          <span>1</span><strong>设备与联系人</strong>
        </button>
        <button type="button" :class="{ active: step === 2 }" :aria-current="step === 2 ? 'step' : undefined" @click="step = 2">
          <span>2</span><strong>受理与确认</strong>
        </button>
      </nav>
      <RepairDeviceStep v-if="step === 1" :form="form" :errors="errors" />
      <RepairIntakeStep
        v-else
        :form="form"
        :errors="errors"
        :handler="handler"
        :candidates="candidates"
        @update:handler="$emit('update:handler', $event)"
      />
    </form>
    <template #footer>
      <button class="button secondary" type="button" :disabled="pending" @click="close">取消</button>
      <button v-if="step === 2" class="button secondary" type="button" :disabled="pending" @click="step = 1">
        <ArrowLeft aria-hidden="true" />上一步
      </button>
      <button v-if="step === 1" class="button primary" type="button" @click="nextStep">
        下一步<ArrowRight aria-hidden="true" />
      </button>
      <button v-else class="button primary" type="submit" form="repair-editor-form" :disabled="pending">
        <LoaderCircle v-if="pending" class="spin" aria-hidden="true" />
        {{ pending ? "正在保存" : "保存事务" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, LoaderCircle } from "@lucide/vue";
import { nextTick, reactive, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import type { AccountCandidate } from "../accounts/accountCandidates";
import RepairDeviceStep from "./RepairDeviceStep.vue";
import RepairIntakeStep from "./RepairIntakeStep.vue";
import { validateRepairForm, type RepairErrors } from "./repairForms";
import type { RepairCaseForm } from "./repairTypes";

const props = defineProps<{
  open: boolean;
  form: RepairCaseForm;
  handler: AccountCandidate | null;
  candidates: AccountCandidate[];
  pending: boolean;
}>();
const emit = defineEmits<{ close: []; save: []; "update:handler": [value: AccountCandidate | null] }>();
const step = ref<1 | 2>(1);
const errors = reactive<RepairErrors>({});

function close() {
  if (!props.pending) emit("close");
}

watch(() => props.open, (open) => {
  if (open) {
    step.value = 1;
    clearErrors();
  }
});

async function nextStep() {
  clearErrors();
  const result = validateRepairForm(props.form, props.handler);
  for (const key of ["ownerName", "ownerPhone", "deviceType", "faultDescription"] as const) {
    if (result.errors[key]) errors[key] = result.errors[key];
  }
  if (Object.keys(errors).length) return focusFirstError();
  step.value = 2;
}

async function submit() {
  clearErrors();
  const result = validateRepairForm(props.form, props.handler);
  Object.assign(errors, result.errors);
  if (Object.keys(errors).length) {
    step.value = result.step;
    await focusFirstError();
    return;
  }
  emit("save");
}

async function focusFirstError() {
  await nextTick();
  document.querySelector<HTMLElement>('#repair-editor-form [aria-invalid="true"]')?.focus();
}

function clearErrors() {
  for (const key of Object.keys(errors) as (keyof RepairErrors)[]) delete errors[key];
}
</script>
