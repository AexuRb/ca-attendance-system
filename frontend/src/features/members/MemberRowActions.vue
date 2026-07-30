<template>
  <div class="member-row-actions">
    <button
      class="icon-button"
      type="button"
      :title="`编辑 ${member.name}`"
      :aria-label="`编辑 ${member.name}`"
      :disabled="!editable"
      @click="$emit('edit')"
    >
      <Pencil aria-hidden="true" />
    </button>
    <ActionMenu :label="`${member.name}的更多操作`">
      <button
        role="menuitem"
        type="button"
        :disabled="!editable || self"
        @click="$emit('toggle-status')"
      >
        <Power v-if="member.status !== 'ACTIVE'" aria-hidden="true" />
        <PowerOff v-else aria-hidden="true" />
        {{ member.status === "ACTIVE" ? "停用账号" : "启用账号" }}
      </button>
      <button
        role="menuitem"
        type="button"
        :disabled="!editable"
        @click="$emit('reset-password')"
      >
        <KeyRound aria-hidden="true" />
        重置密码
      </button>
      <button
        v-if="deletable"
        class="danger-text"
        role="menuitem"
        type="button"
        :disabled="self"
        @click="$emit('delete')"
      >
        <Trash2 aria-hidden="true" />
        删除成员
      </button>
    </ActionMenu>
  </div>
</template>

<script setup lang="ts">
import { KeyRound, Pencil, Power, PowerOff, Trash2 } from "@lucide/vue";
import ActionMenu from "../../shared/ui/ActionMenu.vue";
import type { MemberSummary } from "./memberDirectory";

defineProps<{
  member: MemberSummary;
  editable: boolean;
  self: boolean;
  deletable: boolean;
}>();

defineEmits<{
  edit: [];
  "toggle-status": [];
  "reset-password": [];
  delete: [];
}>();
</script>
