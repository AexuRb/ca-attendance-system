<template>
  <RefinedWorkspaceShell class="daily-workspace attendance-workspace" title="值班记录" description="查看签到、签退与有效时长" section-key="duty" filter-label="筛选值班记录">
    <template #heading><h1>值班记录</h1><p>逐条核对签到、签退与计入时长</p></template>
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
          ref="keywordInput"
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
      ><button class="button secondary" type="submit" :disabled="listLoading"><Search />{{ listLoading ? '查询中…' : filtersPending ? '更新结果' : '查询' }}</button>
    </form></template>
    <div v-if="displayError" class="inline-alert danger" role="alert">
      <span>{{ displayError }}{{ listError && records.length ? '；下方保留上次成功查询的结果' : '' }}</span>
      <button
        v-if="listError"
        class="button secondary small"
        type="button"
        data-action="retry-attendance"
        @click="retryLoad"
      >
        重试
      </button>
    </div>
    <div v-if="hasAppliedQuery" class="attendance-results-summary">
      <div class="attendance-results-summary__title"><span>记录列表</span><strong>{{ total }} 条</strong></div>
      <Transition name="attendance-range-swap" mode="out-in">
        <span :key="`${appliedFilters.from}-${appliedFilters.to}`" class="attendance-results-summary__range" aria-live="polite">{{ appliedFilters.from || '不限开始日期' }} — {{ appliedFilters.to || '不限结束日期' }} · {{ statusLabel(appliedFilters.status) || '全部状态' }}<template v-if="appliedFilters.keyword"> · 成员：{{ appliedFilters.keyword }}</template></span>
      </Transition>
      <QueryStatus class="attendance-query-status" :loading="listLoading" :failed="Boolean(listError)" :dirty="filtersPending" loading-text="正在更新，暂示上次结果" failed-text="查询未成功，重试将沿用上次请求条件" dirty-text="筛选已修改，查询后生效" :idle-text="records.length ? '计入时长按每条记录独立舍入' : ''" />
    </div>
    <LoadingBlock v-if="listLoading && !records.length" />
    <EmptyState v-else-if="!records.length && listError" title="记录暂时无法加载" description="请使用上方的重试按钮重新获取记录" />
    <EmptyState v-else-if="!records.length && hasAppliedQuery" title="没有符合条件的记录" description="本次查询已完成。可调整日期、成员或状态后重新查询。">
      <button class="button secondary small" type="button" data-action="adjust-attendance-filters" @click="keywordInput?.focus()">调整筛选</button>
    </EmptyState>
    <template v-else-if="records.length">
    <div :ref="bindTableScroll" class="mw-table-scroll attendance-table-scroll" tabindex="0" aria-label="值班记录，可横向滚动" @scroll.passive="rememberTableScroll">
      <table class="mw-table attendance-table">
        <thead>
          <tr>
            <th>{{ editorial ? "成员与日期" : "成员" }}</th>
            <th v-if="!editorial">日期</th>
            <th>{{ editorial ? "签到 / 签退" : "签到" }}</th>
            <th v-if="!editorial">签退</th>
            <th>{{ editorial ? "认定结果" : "计入时长" }}</th>
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
            <td v-if="!editorial" class="attendance-date"><time :datetime="item.dutyDate">{{ item.dutyDate }}</time></td>
            <td><div class="daily-times"><span><small v-if="editorial">签到</small><time v-if="item.checkInTime" :datetime="item.checkInTime">{{ dateTime(item.checkInTime) }}</time><span v-else>—</span></span><span v-if="editorial"><small>签退</small><time v-if="item.checkOutTime" :datetime="item.checkOutTime">{{ dateTime(item.checkOutTime) }}</time><span v-else>—</span></span></div></td>
            <td v-if="!editorial" class="attendance-clock"><time v-if="item.checkOutTime" :datetime="item.checkOutTime">{{ dateTime(item.checkOutTime) }}</time><span v-else>—</span></td>
            <td class="daily-outcome">
              <strong>{{ item.validHours ?? '—' }} 小时</strong>
              <small>有效分钟 {{ item.durationMinutes ?? '—' }}</small>
              <StatusBadge v-if="editorial" :label="statusLabel(item.effectiveStatus)" :tone="statusTone(item.effectiveStatus)" />
            </td>
            <td v-if="!editorial">
              <StatusBadge
                :label="statusLabel(item.effectiveStatus)"
                :tone="statusTone(item.effectiveStatus)"
              />
            </td>
            <td class="align-right row-actions mw-actions-column">
              <small v-if="!actionAccess(item).allowed" class="attendance-action-reason">{{ actionAccess(item).reason }}</small>
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
    <ol class="attendance-mobile-list" aria-label="值班记录">
      <li v-for="item in records" :key="item.id" class="attendance-mobile-record">
        <div class="attendance-mobile-record__head">
          <div class="attendance-mobile-record__person">
            <strong>{{ item.name }}</strong>
            <span>{{ item.studentNo }}</span>
            <time :datetime="item.dutyDate">{{ item.dutyDate }}</time>
          </div>
          <StatusBadge :label="statusLabel(item.effectiveStatus)" :tone="statusTone(item.effectiveStatus)" />
        </div>
        <div class="attendance-mobile-record__times">
          <div>
            <small>签到</small>
            <time v-if="item.checkInTime" :datetime="item.checkInTime">{{ dateTime(item.checkInTime) }}</time>
            <span v-else>—</span>
          </div>
          <div>
            <small>签退</small>
            <time v-if="item.checkOutTime" :datetime="item.checkOutTime">{{ dateTime(item.checkOutTime) }}</time>
            <span v-else>—</span>
          </div>
        </div>
        <div class="attendance-mobile-record__foot">
          <div class="attendance-mobile-record__duration">
            <small>计入时长</small>
            <strong>{{ item.validHours ?? '—' }} 小时</strong>
            <small>有效分钟 {{ item.durationMinutes ?? '—' }}</small>
          </div>
          <div class="attendance-mobile-record__actions">
            <button class="button secondary small" type="button" :disabled="!actionAccess(item).allowed"
              :aria-label="`编辑 ${item.name} ${item.dutyDate} 的记录`" @click="openEdit(item)">
              <Pencil aria-hidden="true" />编辑
            </button>
            <button class="button secondary small attendance-mobile-delete" type="button" :disabled="!actionAccess(item).allowed"
              :aria-label="`删除 ${item.name} ${item.dutyDate} 的记录`" @click="askDelete(item)">
              <Trash2 aria-hidden="true" />删除
            </button>
          </div>
        </div>
        <p v-if="!actionAccess(item).allowed" class="attendance-action-reason">{{ actionAccess(item).reason }}</p>
      </li>
    </ol>
    </template>
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
      <div v-if="editing" class="attendance-editor-context">
        <strong>{{ editing.name }}</strong>
        <span>{{ editing.studentNo }} · {{ editing.dutyDate }}</span>
      </div>
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
          {{ actions.isPending('save') ? '正在保存' : '保存' }}
        </button></template
      >
    </ModalDialog>
    <ConfirmDialog
      :open="unsaved.confirmOpen.value"
      title="放弃未保存修改"
      message="当前值班记录还有未保存的内容，放弃后无法恢复。"
      confirm-label="放弃修改"
      danger
      @cancel="unsaved.cancel"
      @confirm="unsaved.discard"
    />
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="删除值班记录"
      :message="`将删除 ${deleteTarget?.name || ''} 在 ${deleteTarget?.dutyDate || ''} 的值班记录，系统会先自动备份。`"
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
import { computed, provide, ref } from "vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import { useAppearance } from "../../appearance/appearanceStore";
import "../../styles/workspace.css";
import "../../features/attendance/presentation.css";
import QueryStatus from "../../shared/ui/QueryStatus.vue";
provide(memberPresentationKey, true);
const { state: appearance } = useAppearance();
const editorial = computed(() => appearance.active === "EDITORIAL");
const keywordInput = ref<HTMLInputElement | null>(null);
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import AccountPicker from "../../features/accounts/AccountPicker.vue";
import { useAttendanceRecordsWorkspace } from "../../features/attendance/useAttendanceRecordsWorkspace";

const {
  bindTableScroll,
  rememberTableScroll,
  appliedFilters,
  hasAppliedQuery,
  filtersPending,
  retryLoad,
  unsaved,
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
