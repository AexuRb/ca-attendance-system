<template>
  <ModalDialog
    :open="open"
    :title="form.id ? '编辑培训' : '新建培训'"
    size="lg"
    @close="close"
  >
    <form ref="formElement" id="training-session-editor" class="form-grid two" novalidate @submit.prevent="submit">
      <label class="field span-2">
        <span>培训标题</span>
        <input
          v-model.trim="form.title"
          name="training-title"
          maxlength="100"
          autocomplete="off"
          data-dialog-initial-focus
          :aria-invalid="Boolean(errors.title)"
          :aria-describedby="errors.title ? 'training-title-error' : undefined"
        />
        <small v-if="errors.title" id="training-title-error" class="field-error" role="alert">{{ errors.title }}</small>
      </label>
      <label class="field">
        <span>培训日期</span>
        <input
          v-model="form.trainingDate"
          name="training-date"
          type="date"
          :aria-invalid="Boolean(errors.trainingDate)"
          :aria-describedby="errors.trainingDate ? 'training-date-error' : undefined"
        />
        <small v-if="errors.trainingDate" id="training-date-error" class="field-error" role="alert">{{ errors.trainingDate }}</small>
      </label>
      <label class="field">
        <span>地点</span>
        <input v-model.trim="form.location" name="training-location" autocomplete="off" />
      </label>
      <label class="field">
        <span>开始时间</span>
        <input v-model="form.startTime" name="training-start-time" type="time" />
      </label>
      <label class="field">
        <span>结束时间</span>
        <input
          v-model="form.endTime"
          name="training-end-time"
          type="time"
          :aria-invalid="Boolean(errors.endTime)"
          :aria-describedby="errors.endTime ? 'training-end-time-error' : undefined"
        />
        <small v-if="errors.endTime" id="training-end-time-error" class="field-error" role="alert">{{ errors.endTime }}</small>
      </label>
      <label class="field">
        <span>主讲人</span>
        <input v-model.trim="form.speaker" name="training-speaker" autocomplete="name" />
      </label>
      <label class="field">
        <span>说明</span>
        <input v-model.trim="form.description" name="training-description" autocomplete="off" />
      </label>
    </form>
    <template #footer>
      <button class="button secondary" type="button" :disabled="pending" @click="close">取消</button>
      <button class="button primary" type="submit" form="training-session-editor" :disabled="pending">
        <LoaderCircle v-if="pending" class="spin" aria-hidden="true" />
        {{ pending ? "正在保存" : "保存培训" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { LoaderCircle } from "@lucide/vue";
import { reactive, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { focusFirstInvalid } from "../../shared/validation/userInput";
import { validateTrainingSessionForm, type TrainingSessionErrors } from "./trainingForms";
import type { TrainingSessionForm } from "./trainingTypes";

const props = defineProps<{ open: boolean; form: TrainingSessionForm; pending: boolean }>();
const emit = defineEmits<{ close: []; save: [] }>();
const errors = reactive<TrainingSessionErrors>({});
const formElement = ref<HTMLFormElement | null>(null);

function close() {
  if (!props.pending) emit("close");
}

watch(() => props.open, (open) => open && clearErrors());

async function submit() {
  clearErrors();
  Object.assign(errors, validateTrainingSessionForm(props.form));
  if (Object.keys(errors).length) {
    focusFirstInvalid(formElement.value, errors);
    return;
  }
  emit("save");
}

function clearErrors() {
  for (const key of Object.keys(errors) as (keyof TrainingSessionErrors)[]) delete errors[key];
}
</script>
