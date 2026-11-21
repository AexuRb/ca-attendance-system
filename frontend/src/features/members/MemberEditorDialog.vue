<template>
  <ModalDialog :error="submitError" :busy="busy"
    :backdrop-class="backdropClass"
    :open="open"
    :title="member ? '编辑成员' : '新增成员'"
    eyebrow="MEMBER PROFILE"
    size="lg"
    @close="close"
  >
    <form
      ref="formElement"
      id="member-editor-form"
      class="form-grid two"
      novalidate
      @submit.prevent="submit"
    >
      <label class="field">
        <span>学号</span>
        <input
          v-model.trim="form.studentNo"
          name="studentNo"
          autocomplete="off"
          :disabled="Boolean(member)"
          inputmode="numeric"
          pattern="[0-9]{6,32}"
          minlength="6"
          maxlength="32"
          required
          :aria-invalid="Boolean(errors.studentNo)"
        />
        <small v-if="errors.studentNo" class="field-error" role="alert">{{ errors.studentNo }}</small>
      </label>
      <label class="field">
        <span>姓名</span>
        <input
          v-model.trim="form.name"
          name="name"
          autocomplete="name"
          maxlength="64"
          required
          :aria-invalid="Boolean(errors.name)"
        />
        <small v-if="errors.name" class="field-error" role="alert">{{ errors.name }}</small>
      </label>
      <label class="field">
        <span>角色</span>
        <select
          v-model="form.role"
          name="role"
          :disabled="lockAccountControls"
        >
          <option value="MEMBER">成员</option>
          <option value="MINISTER">部长</option>
          <option value="PRESIDENT">会长</option>
          <option v-if="operatorRole === 'ADMIN'" value="ADMIN">管理员</option>
        </select>
      </label>
      <label class="field">
        <span>账号状态</span>
        <select
          v-model="form.status"
          name="status"
          :disabled="lockAccountControls"
        >
          <option value="ACTIVE">启用</option>
          <option value="DISABLED">停用</option>
        </select>
      </label>
      <fieldset v-if="member" class="member-editor-assessments span-2">
        <legend>技能考核</legend>
        <div v-if="form.role === 'MEMBER'" class="form-grid two">
          <label v-for="field in memberAssessments" :key="field.key" class="field">
            <span>{{ field.label }}</span>
            <select v-model="form[field.key]" :name="field.key" :disabled="busy">
              <option value="NOT_PASSED">未通过</option>
              <option value="PASSED">通过</option>
            </select>
          </label>
        </div>
        <p v-else class="member-editor-assessment-note">
          <LockKeyhole aria-hidden="true" />
          <span>两项考核均已通过，任职期间锁定。</span>
        </p>
      </fieldset>
      <label class="field">
        <span>手机号</span>
        <input
          v-model.trim="form.phone"
          name="phone"
          inputmode="tel"
          placeholder="选填"
          autocomplete="tel"
          maxlength="64"
          :aria-invalid="Boolean(errors.phone)"
        />
        <small v-if="errors.phone" class="field-error" role="alert">{{ errors.phone }}</small>
      </label>
      <label class="field">
        <span>学院</span>
        <input v-model.trim="form.major" name="major" maxlength="128" :aria-invalid="Boolean(errors.major)" />
        <small v-if="errors.major" class="field-error" role="alert">{{ errors.major }}</small>
      </label>
      <label class="field">
        <span>年级</span>
        <select v-model="form.grade" name="grade" :aria-invalid="Boolean(errors.grade)">
          <option value="">暂不填写</option>
          <option v-for="grade in availableGradeChoices" :key="grade" :value="grade">
            {{ grade }}
          </option>
        </select>
        <small v-if="errors.grade" class="field-error" role="alert">{{ errors.grade }}</small>
      </label>
      <label class="field">
        <span>QQ</span>
        <input v-model.trim="form.qq" name="qq" inputmode="numeric" maxlength="32" placeholder="选填" :aria-invalid="Boolean(errors.qq)" />
        <small v-if="errors.qq" class="field-error" role="alert">{{ errors.qq }}</small>
      </label>
      <ReasonPicker v-if="member" :key="`${open}-${member.id}`" v-model="form.reason" class="span-2" name="reason" label="修改原因"
        :options="reasonOptions.memberEdit" :disabled="busy" :invalid="Boolean(errors.reason)" />
      <small v-if="member && errors.reason" class="field-error span-2" role="alert">{{ errors.reason }}</small>
    </form>
    <template #footer>
      <button class="button secondary" type="button" :disabled="busy" @click="close">
        取消
      </button>
      <button
        class="button primary member-editor-save"
        data-button-kind="action"
        type="submit"
        form="member-editor-form"
        :disabled="busy"
      >
        {{ member ? "保存修改" : "新增成员" }}
      </button>
    </template>
  </ModalDialog>
  <ConfirmDialog
    :backdrop-class="backdropClass"
    :open="unsaved.confirmOpen.value"
    title="放弃未保存修改"
    message="成员资料尚未保存，确定放弃修改吗？"
    confirm-label="放弃修改"
    :pending="busy"
    @cancel="unsaved.cancel"
    @confirm="unsaved.discard"
  />
</template>

<script setup lang="ts">
import { LockKeyhole } from "@lucide/vue";
import { computed, reactive, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import ReasonPicker from "../../shared/ui/ReasonPicker.vue";
import { reasonOptions } from "../../shared/config/reasonOptions";
import { useUnsavedChanges } from "../../shared/composables/useUnsavedChanges";
import {
  focusFirstInvalid,
  validateMemberInput,
  type InputErrors,
} from "../../shared/validation/userInput";
import {
  memberAssessments,
  memberAssessmentStatus,
  type AssessmentStatus,
  type MemberAssessmentChanges,
  type MemberEditorPayload,
  type MemberRole,
  type MemberStatus,
  type MemberSummary,
} from "./memberDirectory";

const props = defineProps<{
  backdropClass?: string;
  open: boolean;
  member: MemberSummary | null;
  operatorRole?: string;
  gradeChoices: string[];
  busy?: boolean; submitError?: string;
  lockAccountControls?: boolean;
}>();
const emit = defineEmits<{
  close: [];
  save: [payload: MemberEditorPayload];
}>();

function close() {
  if (!props.busy) unsaved.request(() => emit("close"));
}

const form = reactive({
  studentNo: "",
  name: "",
  role: "MEMBER" as MemberRole,
  status: "ACTIVE" as MemberStatus,
  phone: "",
  major: "",
  grade: "",
  qq: "",
  reason: "",
  disassemblyAssessment: "NOT_PASSED" as AssessmentStatus,
  osReinstallAssessment: "NOT_PASSED" as AssessmentStatus,
});
const formElement = ref<HTMLFormElement | null>(null);
const baseline = ref("");
const unsaved = useUnsavedChanges(
  () => props.open && JSON.stringify(form) !== baseline.value,
);
const errors = reactive<InputErrors>({});
const availableGradeChoices = computed(() =>
  form.grade && !props.gradeChoices.includes(form.grade)
    ? [form.grade, ...props.gradeChoices]
    : props.gradeChoices,
);
watch(
  () => [props.open, props.member] as const,
  ([open, member]) => {
    unsaved.cancel();
    if (!open) return;
    Object.assign(form, {
      studentNo: member?.studentNo || "",
      name: member?.name || "",
      role: member?.role || "MEMBER",
      status: member?.status || "ACTIVE",
      phone: member?.phone || "",
      major: member?.major || "",
      grade: member?.grade || "",
      qq: member?.qq || "",
      reason: "",
      disassemblyAssessment: member ? memberAssessmentStatus(member, "disassemblyAssessment") ?? "NOT_PASSED" : "NOT_PASSED",
      osReinstallAssessment: member ? memberAssessmentStatus(member, "osReinstallAssessment") ?? "NOT_PASSED" : "NOT_PASSED",
    });
    baseline.value = JSON.stringify(form);
    Object.keys(errors).forEach((key) => delete errors[key]);
  },
  { immediate: true },
);

function submit() {
  if (props.busy) return;
  const member = props.member;
  const assessmentChanges: MemberAssessmentChanges = {};
  if (member && form.role === "MEMBER") {
    for (const { key } of memberAssessments) {
      if (form[key] !== (memberAssessmentStatus(member, key) ?? "NOT_PASSED")) {
        assessmentChanges[key] = form[key];
      }
    }
  }
  const profileFields = ["name", "role", "status", "phone", "major", "grade", "qq"] as const;
  const assessmentOnly = Boolean(member && Object.keys(assessmentChanges).length &&
    profileFields.every(key => form[key] === (member[key] ?? "")));
  const validation = validateMemberInput(form, {
    validateStudentNo: !member,
    requireReason: Boolean(member),
  });
  const nextErrors = assessmentOnly ? (validation.reason ? { reason: validation.reason } : {}) : validation;
  Object.keys(errors).forEach((key) => delete errors[key]);
  Object.assign(errors, nextErrors);
  if (Object.keys(errors).length) {
    focusFirstInvalid(formElement.value, errors);
    return;
  }
  const { studentNo, name, role, status, phone, major, grade, qq, reason } = form;
  emit("save", { studentNo, name, role, status, phone, major, grade, qq, reason, assessmentChanges, assessmentOnly });
}
</script>

<style scoped>
.member-editor-assessments {
  min-inline-size: 0;
  margin: 0;
  padding: 8px 12px 12px;
  border: 1px solid var(--mw-line, var(--border-soft, #dce2e8));
  border-radius: 8px;
}
.member-editor-assessments legend {
  padding-inline: 4px;
  font-weight: 600;
}
.member-editor-assessment-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--mw-muted, var(--text-muted));
}
.member-editor-assessment-note svg {
  flex: 0 0 14px;
  width: 14px;
  height: 14px;
}
</style>
