<template>
  <RefinedWorkspaceShell class="settings-page system-workspace settings-workspace" title="系统设置" description="设置外观、开放日期与有效时长规则" section-key="system">
    <div v-if="loadError" class="inline-alert danger" role="alert">
      <span>{{ loadError }}</span>
      <button class="button secondary small" type="button" data-action="retry-settings" @click="loadSettings">
        重试
      </button>
    </div>
    <LoadingBlock v-if="loading && !weekdays.length" />
    <AppearanceSelector
      v-model="appearanceDraft"
      :active-appearance="appearanceState.active"
      :can-edit="canEditAppearance"
      :pending="actions.isPending('appearance')"
      :error="appearanceError"
      @save="saveAppearance"
    />
    <section id="settings-weekdays" class="sw-section sw-weekdays">
      <div class="sw-heading">
        <div>
          <h2>值班星期</h2>
          <span>未开放日仍可签到签退，计时遵循审核与下方规则。</span>
          <span v-if="weekdaysDirty" class="sw-section-status" role="status">星期设置未保存</span>
        </div>
        <button class="button primary small" :disabled="!weekdaysDirty || actions.isPending('weekdays')" @click="saveWeekdays">
          <Save />{{ actions.isPending('weekdays') ? "正在保存" : "保存星期" }}
        </button>
      </div>
      <WeekdayCalendarSelector :days="weekdays" @toggle="toggleWeekday" />
    </section>
    <DutyTimeWorkspace
      refined
      v-model:periods="periods"
      v-model:policy="attendancePolicy"
      :can-edit-policy="canEditAttendancePolicy"
      :period-error="periodError"
      :policy-dirty="policyDirty"
      :periods-dirty="periodsDirty"
      :policy-pending="actions.isPending('policy')"
      :periods-pending="actions.isPending('periods')"
      @save-policy="saveAttendancePolicy"
      @save-periods="savePeriods"
    />
    <ConfirmDialog
      :open="unsaved.confirmOpen.value"
      title="放弃未保存修改"
      message="系统设置还有未保存的修改，离开后将无法恢复。"
      confirm-label="放弃修改"
      danger
      @cancel="unsaved.cancel"
      @confirm="unsaved.discard"
    />
  </RefinedWorkspaceShell>
</template>
<script setup lang="ts">
import { Save } from "@lucide/vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/settings/presentation.css";
provide(memberPresentationKey, true);
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import DutyTimeWorkspace from "./settings/DutyTimeWorkspace.vue";
import WeekdayCalendarSelector from "./settings/WeekdayCalendarSelector.vue";
import AppearanceSelector from "./settings/AppearanceSelector.vue";
import { useSettingsWorkspace } from "../../features/settings/useSettingsWorkspace";

const {
  actions,
  appearanceDraft,
  appearanceError,
  appearanceState,
  attendancePolicy,
  canEditAppearance,
  canEditAttendancePolicy,
  loadError,
  loading,
  loadSettings,
  periodError,
  periods,
  periodsDirty,
  policyDirty,
  saveAttendancePolicy,
  saveAppearance,
  savePeriods,
  saveWeekdays,
  toggleWeekday,
  unsaved,
  weekdays,
  weekdaysDirty,
} = useSettingsWorkspace();
</script>
