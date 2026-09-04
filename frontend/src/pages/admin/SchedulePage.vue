<template>
  <div class="page-stack">
    <PageHeader
      title="排班管理"
    >
      <template #actions>
        <button
          class="button primary"
          :disabled="loading || actions.isPending('save') || !periods.length"
          @click="openFixed(null)"
        >
          <Plus />新增排班
        </button>
        <ActionMenu label="导入排班" trigger-text="导入">
          <button
            role="menuitem"
            type="button"
            :disabled="actions.isPending('template')"
            @click="downloadImportTemplate"
          >
            <Download aria-hidden="true" />下载导入模板
          </button>
          <button
            role="menuitem"
            type="button"
            :disabled="loading"
            @click="importOpen = true"
          >
            <Upload aria-hidden="true" />批量导入
          </button>
        </ActionMenu>
        <button
          class="icon-button"
          type="button"
          aria-label="刷新排班"
          title="刷新排班"
          :disabled="loading"
          @click="loadBase"
        >
          <RefreshCw aria-hidden="true" />
        </button>
      </template>
    </PageHeader>

    <div v-if="loadError && slots.length" class="inline-alert danger" role="alert">
      <span>{{ loadError }}</span>
      <button class="button secondary small" type="button" data-action="retry-schedule" @click="loadBase">
        重试
      </button>
    </div>
    <LoadingBlock v-if="loading && !slots.length" />
    <div v-else-if="loadError && !slots.length" class="inline-alert danger" role="alert">
      <span>{{ loadError }}</span>
      <button
        class="button secondary small"
        type="button"
        data-action="retry-schedule"
        @click="loadBase"
      >
        重试
      </button>
    </div>
    <FixedScheduleBoard
      v-else
      :slots="slots"
      :periods="periods"
      :weekdays="weekdays"
      :preferred-weekday="preferredWeekday"
      @edit="openFixed"
      @archive="deleteFixed"
      @weekday-change="setPreferredWeekday"
    />

    <ModalDialog
      :open="editorOpen"
      :title="fixedForm.id ? '编辑固定排班' : '新增固定排班'"
      size="lg"
      @close="closeEditor"
    >
      <div class="form-grid two">
        <label class="field">
          <span>星期</span>
          <select v-model.number="fixedForm.weekday" name="scheduleWeekday">
            <option
              v-for="day in weekdays"
              :key="day.value"
              :value="day.value"
              :disabled="!day.enabled"
            >
              {{ day.label }}{{ day.enabled ? "" : "（未开放）" }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>值班时段</span>
          <select v-model="fixedForm.period" name="schedulePeriod">
            <option
              v-for="period in periods"
              :key="periodKey(period)"
              :value="periodKey(period)"
            >
              {{ shortTime(period.startTime) }}–{{ shortTime(period.endTime) }}
            </option>
          </select>
        </label>
        <label class="field">
          <span>标题</span>
          <input v-model="fixedForm.title" name="scheduleTitle" autocomplete="off" />
        </label>
        <label class="field">
          <span>地点</span>
          <input v-model="fixedForm.location" name="scheduleLocation" autocomplete="off" />
        </label>
        <div class="field span-2">
          <span>排班人员</span>
          <ScheduleAssigneePicker
            v-model="fixedForm.assignees"
            :candidates="assigneeCandidates"
            :open="editorOpen"
          />
        </div>
        <div class="field span-2 schedule-visibility-field">
          <span>签到台展示</span>
          <label class="period-enabled-toggle schedule-visibility-toggle">
            <input v-model="fixedForm.enabled" name="scheduleVisible" type="checkbox" />
            <span>{{ fixedForm.enabled ? "显示" : "隐藏" }}</span>
          </label>
          <small>
            隐藏后保留排班内容，但不会出现在签到台今日和本周排班中。
          </small>
        </div>
        <label class="field span-2">
          <span>备注</span>
          <textarea v-model="fixedForm.note" name="scheduleNote" rows="2" />
        </label>
      </div>
      <template #footer>
        <button class="button secondary" :disabled="actions.isPending('save')" @click="closeEditor">
          取消
        </button>
        <button
          class="button primary"
          :disabled="actions.isPending('save') || !fixedForm.period || !fixedForm.title.trim()"
          @click="saveFixed"
        >
          保存
        </button>
      </template>
    </ModalDialog>

    <ScheduleImportDialog
      :open="importOpen"
      @close="importOpen = false"
      @imported="loadBase"
    />

    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="归档固定排班"
      :message="`归档 ${deleteTarget?.weekdayName || ''} ${shortTime(deleteTarget?.startTime)} 的固定排班。`"
      confirm-label="确认归档"
      :pending="actions.isPending('archive')"
      @cancel="deleteTarget = null"
      @confirm="confirmDeleteFixed"
    />
  </div>
</template>

<script setup lang="ts">
import { Download, Plus, RefreshCw, Upload } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import ActionMenu from "../../shared/ui/ActionMenu.vue";
import FixedScheduleBoard from "./schedule/FixedScheduleBoard.vue";
import ScheduleImportDialog from "./schedule/ScheduleImportDialog.vue";
import ScheduleAssigneePicker from "../../features/schedule/ScheduleAssigneePicker.vue";
import { useScheduleWorkspace } from "../../features/schedule/useScheduleWorkspace";

const {
  actions,
  assigneeCandidates,
  closeEditor,
  confirmDeleteFixed,
  deleteFixed,
  deleteTarget,
  downloadImportTemplate,
  editorOpen,
  fixedForm,
  importOpen,
  loadBase,
  loadError,
  loading,
  openFixed,
  periodKey,
  periods,
  preferredWeekday,
  saveFixed,
  setPreferredWeekday,
  slots,
  shortTime,
  weekdays,
} = useScheduleWorkspace();
</script>
