<template>
  <ModalDialog
    :open="open"
    :title="form.id ? '编辑参与记录' : '新增参与记录'"
    size="sm"
    @close="close"
  >
    <p v-if="session" class="training-participant-context"><strong>{{ session.title }}</strong><br />{{ session.trainingDate }} · {{ session.startTime || '未设开始时间' }} — {{ session.endTime || '未设结束时间' }}</p>
    <p v-if="savedMessage" class="training-participant-saved" role="status">{{ savedMessage }}</p>
    <form ref="formElement" id="training-participant-editor" class="form-grid" novalidate @submit.prevent="submit">
      <label class="field">
        <span>学号</span>
        <input
          v-model.trim="form.studentNo"
          name="participant-student-no"
          inputmode="numeric"
          autocomplete="off"
          data-dialog-initial-focus
          :aria-invalid="Boolean(errors.studentNo)"
          :disabled="pending"
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
          :disabled="pending"
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
          :disabled="pending"
        />
        <small v-if="errors.durationHours" class="field-error" role="alert">{{ errors.durationHours }}</small>
      </label>
      <label class="field">
        <span>备注</span>
        <textarea v-model.trim="form.remark" name="participant-remark" rows="3" :disabled="pending" />
      </label>
    </form>
    <template #footer>
      <button class="button secondary" type="button" :disabled="pending" @click="close">取消</button>
      <button v-if="!form.id" class="button secondary" type="button" :disabled="pending" @click="submit(true)">保存并继续</button>
      <button class="button primary" type="submit" form="training-participant-editor" :disabled="pending">
        <LoaderCircle v-if="pending" class="spin" aria-hidden="true" />
        {{ pending ? "正在保存" : "保存记录" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { LoaderCircle } from "@lucide/vue";
import { reactive, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { focusFirstInvalid } from "../../shared/validation/userInput";
import { validateParticipantForm, type TrainingParticipantErrors } from "./trainingForms";
import type { TrainingParticipantForm, TrainingSession } from "./trainingTypes";

const props = defineProps<{ open: boolean; form: TrainingParticipantForm; pending: boolean; session?: TrainingSession | null; savedMessage?: string }>();
const emit = defineEmits<{ close: []; save: [continueAdding: boolean] }>();
const errors = reactive<TrainingParticipantErrors>({});
const formElement = ref<HTMLFormElement | null>(null);

function close() {
  if (!props.pending) emit("close");
}

watch(() => props.open, (open) => open && clearErrors());

async function submit(continueAdding: boolean | Event = false) {
  if (props.pending) return;
  clearErrors();
  Object.assign(errors, validateParticipantForm(props.form));
  if (Object.keys(errors).length) {
    focusFirstInvalid(formElement.value, errors);
    return;
  }
  emit("save", continueAdding === true && !props.form.id);
}

function clearErrors() {
  for (const key of Object.keys(errors) as (keyof TrainingParticipantErrors)[]) delete errors[key];
}
</script>

<style scoped>
.training-participant-context { margin:0 0 16px; padding:10px 12px; background:var(--mw-soft); color:var(--mw-ink); font-size:12px; line-height:1.7; overflow-wrap:anywhere; }
.training-participant-context strong { font-size:14px; }
.training-participant-saved { margin:0 0 12px; color:var(--mw-accent); font-size:12px; overflow-wrap:anywhere; }
</style>
