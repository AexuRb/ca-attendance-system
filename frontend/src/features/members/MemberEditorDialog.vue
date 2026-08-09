<template>
  <ModalDialog
    :open="open"
    :title="member ? '编辑成员' : '新增成员'"
    eyebrow="MEMBER PROFILE"
    size="lg"
    @close="$emit('close')"
  >
    <form id="member-editor-form" class="form-grid two" @submit.prevent="submit">
      <label class="field">
        <span>学号</span>
        <input
          v-model.trim="form.studentNo"
          name="studentNo"
          autocomplete="off"
          :disabled="Boolean(member)"
          required
        />
      </label>
      <label class="field">
        <span>姓名</span>
        <input
          v-model.trim="form.name"
          name="name"
          autocomplete="name"
          required
        />
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
      <label class="field">
        <span>手机号</span>
        <input
          v-model.trim="form.phone"
          name="phone"
          autocomplete="tel"
        />
      </label>
      <label class="field">
        <span>学院</span>
        <input v-model.trim="form.major" name="major" />
      </label>
      <label class="field">
        <span>年级</span>
        <select v-model="form.grade" name="grade">
          <option value="">暂不填写</option>
          <option v-for="grade in gradeChoices" :key="grade" :value="grade">
            {{ grade }}
          </option>
        </select>
      </label>
      <label class="field">
        <span>QQ</span>
        <input v-model.trim="form.qq" name="qq" inputmode="numeric" />
      </label>
      <label v-if="member" class="field span-2">
        <span>修改原因</span>
        <textarea
          v-model.trim="form.reason"
          name="reason"
          rows="3"
          placeholder="用于操作日志"
          required
        />
      </label>
    </form>
    <template #footer>
      <button class="button secondary" type="button" @click="$emit('close')">
        取消
      </button>
      <button
        class="button primary"
        type="submit"
        form="member-editor-form"
        :disabled="busy || !canSubmit"
      >
        {{ member ? "保存修改" : "新增成员" }}
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import type {
  MemberRole,
  MemberStatus,
  MemberSummary,
} from "./memberDirectory";

const props = defineProps<{
  open: boolean;
  member: MemberSummary | null;
  operatorRole?: string;
  gradeChoices: string[];
  busy?: boolean;
  lockAccountControls?: boolean;
}>();
const emit = defineEmits<{
  close: [];
  save: [
    payload: {
      studentNo: string;
      name: string;
      role: MemberRole;
      status: MemberStatus;
      phone: string;
      major: string;
      grade: string;
      qq: string;
      reason?: string;
    },
  ];
}>();

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
});
const canSubmit = computed(
  () =>
    Boolean(form.studentNo && form.name) &&
    (!props.member || Boolean(form.reason.trim())),
);

watch(
  () => [props.open, props.member] as const,
  ([open, member]) => {
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
      reason: member ? "更新成员资料" : "",
    });
  },
  { immediate: true },
);

function submit() {
  if (!canSubmit.value) return;
  emit("save", { ...form });
}
</script>
