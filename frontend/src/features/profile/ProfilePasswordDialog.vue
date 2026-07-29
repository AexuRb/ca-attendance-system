<template>
  <ModalDialog
    :open="open"
    title="修改登录密码"
    eyebrow="ACCOUNT SECURITY"
    size="sm"
    @close="close"
  >
    <form
      id="profile-password-form"
      class="form-grid"
      @submit.prevent="submit"
    >
      <label class="field">
        <span>当前密码</span>
        <input
          v-model="form.oldPassword"
          type="password"
          name="currentPassword"
          autocomplete="current-password"
          required
        />
      </label>
      <label class="field">
        <span>新密码</span>
        <input
          v-model="form.newPassword"
          type="password"
          name="newPassword"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          required
        />
      </label>
      <label class="field">
        <span>确认新密码</span>
        <input
          v-model="confirmation"
          type="password"
          name="passwordConfirmation"
          autocomplete="new-password"
          minlength="6"
          maxlength="64"
          required
        />
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <button class="button secondary" type="button" @click="close">取消</button>
      <button
        class="button primary"
        type="submit"
        form="profile-password-form"
        :disabled="busy"
      >
        保存新密码
      </button>
    </template>
  </ModalDialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from "vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { post } from "../../shared/api";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ close: []; changed: [] }>();
const form = reactive({ oldPassword: "", newPassword: "" });
const confirmation = ref("");
const error = ref("");
const busy = ref(false);

watch(
  () => props.open,
  (open) => {
    if (!open) return;
    form.oldPassword = "";
    form.newPassword = "";
    confirmation.value = "";
    error.value = "";
  },
);

function close() {
  if (!busy.value) emit("close");
}

async function submit() {
  if (form.newPassword !== confirmation.value) {
    error.value = "两次输入的新密码不一致";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    await post("/api/auth/change-password", form);
    emit("changed");
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "密码更新失败";
  } finally {
    busy.value = false;
  }
}
</script>
