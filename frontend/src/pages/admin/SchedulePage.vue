<template>
  <RefinedWorkspaceShell class="support-workspace schedule-presentation" title="排班管理" description="按星期安排固定值班时段" section-key="duty" filter-label="筛选排班管理">
    <template #tools>
        <button
          class="button primary"
          :disabled="loading || Boolean(loadError) || actions.isPending('save') || !periods.length"
          :title="!periods.length ? '请先在系统设置中添加值班时段' : loadError ? '请先重试加载排班' : '新增固定排班'"
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
            :disabled="loading || Boolean(loadError)"
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

    <div v-if="loadError && slots.length" class="inline-alert danger schedule-stale-alert" role="alert">
      <span>{{ loadError }}。当前显示上次加载的排班，刷新成功后可继续编辑。</span>
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
      :read-only="Boolean(loadError)"
      @edit="openFixed"
      @archive="deleteFixed"
      @add="(weekday, period) => openFixed(null, weekday, period)"
      @weekday-change="setPreferredWeekday"
    />

    <ModalDialog
      :open="editorOpen"
      :title="fixedForm.id ? '编辑固定排班' : '新增固定排班'"
      size="lg"
      @close="closeEditor"
    >
      <div class="form-grid two" :inert="actions.isPending('save') ? true : undefined" :aria-busy="actions.isPending('save')">
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
          {{ actions.isPending('save') ? '正在保存' : '保存' }}
        </button>
      </template>
    </ModalDialog>

    <ScheduleImportDialog
      :open="importOpen"
      @close="importOpen = false"
      @imported="loadBase"
    />

    <ConfirmDialog
      :open="unsaved.confirmOpen.value"
      title="放弃未保存修改"
      message="当前排班还有未保存的内容，放弃后无法恢复。"
      confirm-label="放弃修改"
      danger
      @cancel="unsaved.cancel"
      @confirm="unsaved.discard"
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
  </RefinedWorkspaceShell>
</template>

<script setup lang="ts">
import { Download, Plus, RefreshCw, Upload } from "@lucide/vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/workspaces/presentation.css";
import "../../features/schedule/presentation.css";
provide(memberPresentationKey, true);
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import ActionMenu from "../../shared/ui/ActionMenu.vue";
import FixedScheduleBoard from "./schedule/FixedScheduleBoard.vue";
import ScheduleImportDialog from "./schedule/ScheduleImportDialog.vue";
import ScheduleAssigneePicker from "../../features/schedule/ScheduleAssigneePicker.vue";
import { useScheduleWorkspace } from "../../features/schedule/useScheduleWorkspace";

const {
  unsaved,
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
