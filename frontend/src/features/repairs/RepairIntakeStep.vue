<template>
  <div class="form-sections repair-editor-body">
    <section>
      <h3>受理信息</h3>
      <div class="form-grid two">
        <label class="field">
          <span>受理时间</span>
          <input
            v-model="form.receivedAt"
            name="repair-received-at"
            type="datetime-local"
            :aria-invalid="Boolean(errors.receivedAt)"
          />
          <small v-if="errors.receivedAt" class="field-error" role="alert">{{ errors.receivedAt }}</small>
        </label>
        <label class="field">
          <span>完成时间</span>
          <input v-model="form.completedAt" name="repair-completed-at" type="datetime-local" />
        </label>
        <div class="field span-2">
          <span>负责人账号</span>
          <AccountPicker
            :model-value="handler"
            :candidates="candidates"
            input-name="repair-handler"
            aria-label="选择维修负责人"
            placeholder="搜索姓名或学号"
            :invalid="Boolean(errors.handler)"
            described-by="repair-handler-error"
            @update:model-value="$emit('update:handler', $event)"
          />
          <small v-if="errors.handler" id="repair-handler-error" class="field-error" role="alert">{{ errors.handler }}</small>
        </div>
        <label class="field span-2">
          <span>备注</span>
          <input v-model.trim="form.remark" name="repair-remark" autocomplete="off" />
        </label>
      </div>
      <div class="check-row">
        <label><input v-model="form.dataBackupConfirmed" name="repair-data-backup-recorded" type="checkbox" />已完成数据备份情况记录</label>
        <label><input v-model="form.riskAcknowledged" name="repair-risk-recorded" type="checkbox" />已完成维修风险告知记录</label>
        <label><input v-model="form.privacyAcknowledged" name="repair-privacy-recorded" type="checkbox" />已完成隐私事项告知记录</label>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import AccountPicker from "../accounts/AccountPicker.vue";
import type { AccountCandidate } from "../accounts/accountCandidates";
import type { RepairErrors } from "./repairForms";
import type { RepairCaseForm } from "./repairTypes";

defineProps<{
  form: RepairCaseForm;
  errors: RepairErrors;
  handler: AccountCandidate | null;
  candidates: AccountCandidate[];
}>();
defineEmits<{ "update:handler": [value: AccountCandidate | null] }>();
</script>
