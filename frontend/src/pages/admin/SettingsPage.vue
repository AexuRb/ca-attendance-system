<template>
  <ExpandedWorkspaceShell class="settings-page system-workspace settings-workspace overview-settings" current-page="settings">
    <template #heading><h1>系统设置</h1></template>
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
      confirm-backdrop-class="settings-refined-backdrop"
      @save="saveAppearance"
    />
    <section id="settings-weekdays" class="sw-section sw-weekdays">
      <div class="sw-heading">
        <div>
          <h2>值班星期</h2>
          <span v-if="weekdaysDirty" class="sw-section-status" role="status">星期设置未保存</span>
        </div>
        <button class="button primary small" :disabled="!weekdaysDirty || actions.isPending('weekdays')" @click="saveWeekdays">
          <Save />{{ actions.isPending('weekdays') ? "正在保存" : "保存星期" }}
        </button>
      </div>
      <WeekdayCalendarSelector :days="weekdays" @toggle="toggleWeekday" />
      <p v-if="weekdaySaveError" class="form-error settings-save-error" role="alert">{{ weekdaySaveError }}</p>
    </section>
    <DutyTimeWorkspace
      refined
      v-model:periods="periods"
      v-model:policy="attendancePolicy"
      :can-edit-policy="canEditAttendancePolicy"
      :period-error="periodError"
      :period-save-error="periodSaveError"
      :policy-save-error="policySaveError"
      :policy-dirty="policyDirty"
      :periods-dirty="periodsDirty"
      :policy-pending="actions.isPending('policy')"
      :periods-pending="actions.isPending('periods')"
      @save-policy="saveAttendancePolicy"
      @save-periods="savePeriods"
    />
    <ConfirmDialog
      :open="unsaved.confirmOpen.value"
      backdrop-class="settings-refined-backdrop"
      title="放弃未保存修改"
      message="系统设置还有未保存的修改，离开后将无法恢复。"
      confirm-label="放弃修改"
      danger
      @cancel="unsaved.cancel"
      @confirm="unsaved.discard"
    />
  </ExpandedWorkspaceShell>
</template>
<script setup lang="ts">
import { Save } from "@lucide/vue";
import ExpandedWorkspaceShell from "../../layouts/ExpandedWorkspaceShell.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/today/overview-presentation.css";
import "../../features/settings/refined-presentation.css";
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
  periodSaveError,
  policySaveError,
  weekdaySaveError,
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
