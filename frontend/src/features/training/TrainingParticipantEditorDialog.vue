<template>
  <ModalDialog
    :open="open"
    :title="form.id ? '编辑参与记录' : '新增参与记录'"
    size="sm"
    @close="close"
  >
    <form id="training-participant-editor" class="form-grid" novalidate @submit.prevent="submit">
      <label class="field">
        <span>学号</span>
        <input
          v-model.trim="form.studentNo"
          name="participant-student-no"
          inputmode="numeric"
          autocomplete="off"
          data-dialog-initial-focus
          :aria-invalid="Boolean(errors.studentNo)"
        />
        <small v-if="errors.studentNo" class="field-error" role="alert">{{ errors.studentNo }}</small>
      </label>
      <label class="field">
        <span>姓名</span>
        <input
          v-model.trim="form.name"
          name="participant-name"
          autocomplete="name"
          :aria-invalid="Boolean(errors.name)"
        />
        <small v-if="errors.name" class="field-error" role="alert">{{ errors.name }}</small>
      </label>
      <label class="field">
        <span>计入时长（小时）</span>
        <input
          v-model.number="form.durationHours"
          name="participant-duration"
          type="number"
          inputmode="decimal"
          min="0"
          step="0.25"
          :aria-invalid="Boolean(errors.durationHours)"
        />
        <small v-if="errors.durationHours" class="field-error" role="alert">{{ errors.durationHours }}</small>
      </label>
      <label class="field">
        <span>备注</span>
        <textarea v-model.trim="form.remark" name="participant-remark" rows="3" />
      </label>
    </form>
    <template #footer>
      <button class="button secondary" type="button" :disabled="pending" @click="close">取消</button>
      <button class="button primary" type="submit" form="training-participant-editor" :disabled="pending">
        <LoaderCircle v-if="pending" class="spin" aria-hidden="true" />
        {{ pending ? "正在保存" : "保存记录" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { LoaderCircle } from "@lucide/vue";
import { nextTick, reactive, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { validateParticipantForm, type TrainingParticipantErrors } from "./trainingForms";
import type { TrainingParticipantForm } from "./trainingTypes";

const props = defineProps<{ open: boolean; form: TrainingParticipantForm; pending: boolean }>();
const emit = defineEmits<{ close: []; save: [] }>();
const errors = reactive<TrainingParticipantErrors>({});

function close() {
  if (!props.pending) emit("close");
}

watch(() => props.open, (open) => open && clearErrors());

async function submit() {
  clearErrors();
  Object.assign(errors, validateParticipantForm(props.form));
  if (Object.keys(errors).length) {
    await nextTick();
    document.querySelector<HTMLElement>('#training-participant-editor [aria-invalid="true"]')?.focus();
    return;
  }
  emit("save");
}

function clearErrors() {
  for (const key of Object.keys(errors) as (keyof TrainingParticipantErrors)[]) delete errors[key];
}
</script>
