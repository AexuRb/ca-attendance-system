<template>
  <RefinedWorkspaceShell class="daily-workspace attendance-workspace" title="值班记录" description="查看签到、签退与有效时长" section-key="duty" filter-label="筛选值班记录">
    <template #tools
        ><button v-if="canCreate" class="button primary" @click="openCreate">
          <Plus />补录记录
        </button></template>
    <template #filters><form class="mw-filter daily-filter" @submit.prevent="applyFilters">
      <label
        ><span>开始日期</span
        ><input v-model="filters.from" name="attendanceFrom" type="date" /></label
      ><label
        ><span>结束日期</span><input v-model="filters.to" name="attendanceTo" type="date" /></label
      ><label class="filter-grow"
        ><span>成员</span
        ><input
          v-model.trim="filters.keyword"
          name="attendanceKeyword"
          type="search"
          autocomplete="off"
          placeholder="学号或姓名" /></label
      ><label
        ><span>状态</span
        ><select v-model="filters.status" name="attendanceStatus">
          <option value="">全部</option>
          <option value="VALID">有效</option>
          <option value="INCOMPLETE">未签退</option>
          <option value="PENDING">待审核</option>
          <option value="INVALID">无效</option>
        </select></label
      ><button class="button secondary" type="submit"><Search />查询</button>
    </form></template>
    <div v-if="displayError" class="inline-alert danger" role="alert">
      <span>{{ displayError }}</span>
      <button
        v-if="listError"
        class="button secondary small"
        type="button"
        data-action="retry-attendance"
        @click="load()"
      >
        重试
      </button>
    </div>
    <LoadingBlock v-if="listLoading && !records.length" />
    <EmptyState v-else-if="!records.length && !listError" title="没有符合条件的记录" />
    <div v-else class="mw-table-scroll" tabindex="0" aria-label="值班记录，可横向滚动">
      <table class="mw-table attendance-table">
        <thead>
          <tr v-if="spatial" class="mw-column-groups"><th colspan="2" scope="colgroup">成员与日期</th><th colspan="2" scope="colgroup">签到与签退</th><th colspan="3" scope="colgroup">认定与操作</th></tr>
          <tr>
            <th>{{ editorial ? "成员与日期" : "成员" }}</th>
            <th v-if="!editorial">日期</th>
            <th>{{ editorial ? "签到 / 签退" : "签到" }}</th>
            <th v-if="!editorial">签退</th>
            <th>{{ editorial ? "认定结果" : "有效时长" }}</th>
            <th v-if="!editorial">状态</th>
            <th class="align-right mw-actions-column">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in records" :key="item.id">
            <td class="daily-person">
              <strong>{{ item.name }}</strong
              ><small>{{ item.studentNo }}</small><small v-if="editorial">日期 {{ item.dutyDate }}</small>
            </td>
            <td v-if="!editorial">{{ item.dutyDate }}</td>
            <td><div class="daily-times"><span><small v-if="editorial">签到</small>{{ dateTime(item.checkInTime) }}</span><span v-if="editorial"><small>签退</small>{{ dateTime(item.checkOutTime) }}</span></div></td>
            <td v-if="!editorial">{{ dateTime(item.checkOutTime) }}</td>
            <td class="daily-outcome">
              <small v-if="editorial">有效时长</small>
              {{ item.durationMinutes ? `${item.durationMinutes} 分钟` : "—" }}
              <StatusBadge v-if="editorial" :label="statusLabel(item.effectiveStatus)" :tone="statusTone(item.effectiveStatus)" />
            </td>
            <td v-if="!editorial">
              <StatusBadge
                :label="statusLabel(item.effectiveStatus)"
                :tone="statusTone(item.effectiveStatus)"
              />
            </td>
            <td class="align-right row-actions mw-actions-column">
              <button
                class="icon-button"
                :title="
                  actionAccess(item).allowed
                    ? '编辑'
                    : actionAccess(item).reason
                "
                :aria-label="
                  actionAccess(item).allowed
                    ? '编辑'
                    : `不可编辑：${actionAccess(item).reason}`
                "
                :disabled="!actionAccess(item).allowed"
                @click="openEdit(item)"
              >
                <Pencil /></button
              ><button
                class="icon-button danger-ghost"
                :title="
                  actionAccess(item).allowed
                    ? '删除'
                    : actionAccess(item).reason
                "
                :aria-label="
                  actionAccess(item).allowed
                    ? '删除'
                    : `不可删除：${actionAccess(item).reason}`
                "
                :disabled="!actionAccess(item).allowed"
                @click="askDelete(item)"
              >
                <Trash2 />
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div v-if="total" class="pagination">
      <span>共 {{ total }} 条记录</span>
      <div>
        <button
          class="button secondary small"
          :disabled="page <= 1 || listLoading"
          @click="setPage(page - 1)"
        >
          <ChevronLeft />上一页
        </button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button
          class="button secondary small"
          :disabled="page >= totalPages || listLoading"
          @click="setPage(page + 1)"
        >
          下一页<ChevronRight />
        </button>
      </div>
    </div>
    <ModalDialog
      :open="editorOpen"
      :title="editing ? '修改值班记录' : '补录值班记录'"
      size="lg"
      @close="closeEditor"
    >
      <div class="form-grid two">
        <div v-if="!editing" class="field span-2">
          <span>补录成员</span>
          <AccountPicker
            v-model="selectedMember"
            :candidates="manualCandidates"
            :open="editorOpen"
            aria-label="选择补录成员"
            placeholder="搜索姓名或学号"
          />
        </div>
        <label class="field"
          ><span>签到时间</span
          ><input
            v-model="form.checkInTime"
            name="attendanceCheckInTime"
            type="datetime-local"
            required /></label
        ><label class="field"
          ><span>签退时间</span
          ><input v-model="form.checkOutTime" name="attendanceCheckOutTime" type="datetime-local" /></label
        ><label v-if="editing && canReviewStatus" class="field"
          ><span>签到状态</span
          ><select v-model="form.checkInStatus" name="attendanceCheckInStatus">
            <option value="APPROVED">已通过</option>
            <option value="AUTO_APPROVED">自动通过</option>
            <option value="REJECTED">已驳回</option>
          </select></label
        ><label v-if="editing && canReviewStatus" class="field"
          ><span>签退状态</span
          ><select v-model="form.checkOutStatus" name="attendanceCheckOutStatus">
            <option value="APPROVED">已通过</option>
            <option value="AUTO_APPROVED">自动通过</option>
            <option value="REJECTED">已驳回</option>
            <option value="NOT_SUBMITTED">未提交</option>
          </select></label
        ><label class="field span-2"
          ><span>操作原因</span
          ><textarea v-model="form.reason" name="attendanceReason" rows="3" required />
        </label>
        <label
          v-if="editing && canReviewStatus"
          class="attendance-reevaluate span-2"
        >
          <input v-model="form.recomputeSnapshot" name="attendanceRecomputeSnapshot" type="checkbox" />
          <span>
            <strong>按当前值班设置重新评估</strong>
            <small>关闭时保留该记录产生时的值班日与时段规则</small>
          </span>
        </label>
      </div>
      <template #footer
        ><button class="button secondary" :disabled="actions.isPending('save')" @click="closeEditor">
          取消</button
        ><button
          class="button primary"
          :disabled="
            actions.isPending('save') ||
            !form.reason.trim() || (!editing && !selectedMember)
          "
          @click="save"
        >
          保存
        </button></template
      >
    </ModalDialog>
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除值班记录"
      :message="`将删除 ${deleteTarget?.name || ''} 的这条记录，系统会先自动备份。`"
      confirm-label="删除记录"
      danger
      require-reason
      :pending="actions.isPending('delete')"
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
  </RefinedWorkspaceShell>
</template>

<script setup lang="ts">
import {
  ChevronLeft,
  ChevronRight,
  Pencil,
  Plus,
  Search,
  Trash2,
} from "@lucide/vue";
import { computed, provide } from "vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import { useAppearance } from "../../appearance/appearanceStore";
import "../../features/members/presentation.css";
import "../../features/attendance/presentation.css";
provide(memberPresentationKey, true);
const { state: appearance } = useAppearance();
const editorial = computed(() => appearance.active === "EDITORIAL");
const spatial = computed(() => appearance.active === "SPATIAL");
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import AccountPicker from "../../features/accounts/AccountPicker.vue";
import { useAttendanceRecordsWorkspace } from "../../features/attendance/useAttendanceRecordsWorkspace";

const {
  actionAccess,
  actions,
  applyFilters,
  askDelete,
  canCreate,
  canReviewStatus,
  closeEditor,
  dateTime,
  deleteTarget,
  displayError,
  editing,
  editorOpen,
  filters,
  form,
  listError,
  listLoading,
  load,
  manualCandidates,
  openCreate,
  openEdit,
  page,
  records,
  remove,
  save,
  selectedMember,
  setPage,
  statusLabel,
  statusTone,
  total,
  totalPages,
} = useAttendanceRecordsWorkspace();
</script>
