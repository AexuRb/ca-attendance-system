<template>
  <RefinedWorkspaceShell class="affairs-workspace repair-presentation" title="维修事务" description="从受理到完成，保留每次处理记录" section-key="work" filter-label="筛选维修事务">
    <template #tools
        ><button
          :ref="captureExportButton"
          v-if="canExport"
          class="button secondary"
          :disabled="exportDisabled"
          title="按已查询的日期与关键词导出全部状态，不受当前页限制"
          @click="exportCases"
        >
          <Download />{{ isPending('export-repairs') ? "正在导出" : "导出全部状态" }}</button
        ><button v-if="canManage" class="button primary" @click="openEditor()">
          <Plus />新建维修
        </button></template>
    <template #filters><form class="filter-bar repair-query-bar" @submit.prevent="load">
      <label class="filter-grow"><span>关键词</span><input v-model.trim="filters.keyword" type="search" name="repair-search" placeholder="编号、联系人、设备或故障" autocomplete="off" /></label>
      <label><span>开始日期</span><input v-model="filters.from" name="repairFrom" type="date" /></label>
      <label><span>结束日期</span><input v-model="filters.to" name="repairTo" type="date" /></label>
      <button class="button secondary" type="submit" :disabled="repairPage.loading"><Search aria-hidden="true" />{{ repairPage.loading ? '查询中…' : filtersPending ? '更新结果' : '查询' }}</button>
    </form></template>
    <div v-if="filterError" class="inline-alert danger" role="alert">
      {{ filterError }}
    </div>

    <div class="repair-query-context">
      <span aria-live="polite"><template v-if="hasAppliedQuery">当前结果：{{ appliedFilters.from || '不限开始日期' }} — {{ appliedFilters.to || '不限结束日期' }}<template v-if="appliedFilters.keyword"> · 关键词：{{ appliedFilters.keyword }}</template></template><template v-else>尚无成功查询结果</template></span>
      <QueryStatus :as="repairPage.loading || repairPage.error ? 'strong' : 'span'" :loading="repairPage.loading" :failed="Boolean(repairPage.error)" :dirty="filtersPending" :loading-text="`正在查询${pendingQuery?.status === 'COMPLETED' ? '已完成' : pendingQuery?.status === 'CANCELED' ? '已取消' : '进行中'}记录`" failed-text="查询未成功，重试将沿用上次请求条件" dirty-text="筛选已修改，查询后生效；翻页和导出仍沿用当前结果条件" />
    </div>

    <RepairStatusTabs
      :active-status="activeStatus"
      :counts="statusCounts"
      @change="setStatus"
    />

    <section
      :id="`repair-panel-${activeStatus}`"
      class="repair-workspace"
      role="tabpanel"
      :aria-labelledby="`repair-tab-${activeStatus}`"
      :aria-busy="repairPage.loading"
      tabindex="0"
    >
      <RepairLedgerTable
        :bind-scroll="bindTableScroll"
        @scroll="rememberTableScroll"
        :items="repairPage.items"
        :status="activeStatus"
        :loading="repairPage.loading"
        :error="repairPage.error && `${repairPage.error}${repairPage.items.length ? '；当前条件与列表仍为上次成功结果' : ''}`"
        :revealed-phones="revealedPhones"
        :can-manage="canManage && !repairPage.loading && !repairPage.error"
        :can-delete="canDelete && !repairPage.loading && !repairPage.error"
        @view="detailTarget = $event"
        @preview="preview"
        @process="openEditor($event, 2)"
        @delete="requestDelete"
        @toggle-phone="togglePhone"
        @retry="retry"
      />

      <footer
        v-if="repairPage.total && !repairPage.error"
        class="repair-workspace-pagination"
      >
        <button
          class="button secondary small"
          type="button"
          :disabled="repairPage.page <= 1 || repairPage.loading"
          @click="setPage(repairPage.page - 1)"
        >
          <ArrowLeft aria-hidden="true" />上一页
        </button>
        <span>第 {{ repairPage.page }} / {{ repairTotalPages }} 页 · 共 {{ repairPage.total }} 项</span>
        <button
          class="button secondary small"
          type="button"
          :disabled="!repairPage.hasMore || repairPage.loading"
          @click="setPage(repairPage.page + 1)"
        >
          下一页<ArrowRight aria-hidden="true" />
        </button>
      </footer>
    </section>

    <RepairDetailDrawer
      :open="Boolean(detailTarget)"
      :item="detailTarget"
      :phone-visible="Boolean(detailTarget && phoneVisible(detailTarget.id))"
      :can-manage="canManage && !repairPage.loading && !repairPage.error"
      :can-delete="canDelete && !repairPage.loading && !repairPage.error"
      :position="detailPosition"
      :can-previous="detailCanPrevious"
      :can-next="detailCanNext"
      @previous="moveDetail(-1)"
      @next="moveDetail(1)"
      @close="detailTarget = null"
      @preview="preview"
      @edit="editFromDetail"
      @process="editFromDetail($event, 2)"
      @delete="requestDelete"
      @toggle-phone="togglePhone"
    />
    <RepairEditorDialog
      :open="editorOpen"
      :form="form"
      :handler="selectedHandler"
      :candidates="handlerCandidates"
      :pending="isPending('save-repair')"
      :initial-step="editorInitialStep"
      @update:handler="selectedHandler = $event"
      @close="closeEditor"
      @save="save"
    />
    <ConfirmDialog
      :open="Boolean(deleteTarget)"
      title="移入维修回收站"
      :message="`将 ${deleteTarget?.caseNo || ''} 移入回收站，管理员可在数据页面恢复。`"
      confirm-label="移入回收站"
      danger
      :pending="isPending('delete-repair')"
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
    <AgreementDialog
      :open="agreementOpen"
      :case-no="agreementTarget?.caseNo"
      :html="agreementHtml"
      :loading="agreementLoading"
      :error="agreementError"
      @close="closeAgreement"
      @retry="loadAgreement"
    />
    <ConfirmDialog
      :open="unsaved.confirmOpen.value"
      title="放弃未保存修改"
      message="当前维修事务还有未保存的内容，放弃后无法恢复。"
      confirm-label="放弃修改"
      danger
      @cancel="unsaved.cancel"
      @confirm="unsaved.discard"
    />
  </RefinedWorkspaceShell>
</template>
<script setup lang="ts">
import {
  ArrowLeft,
  ArrowRight,
  Download,
  Plus,
  Search,
} from "@lucide/vue";
import { provide } from "vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import QueryStatus from "../../shared/ui/QueryStatus.vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/repairs/presentation.css";
import "../../features/repairs/repair-page-polish.css";
provide(memberPresentationKey, true);
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import AgreementDialog from "../../shared/ui/AgreementDialog.vue";
import RepairEditorDialog from "../../features/repairs/RepairEditorDialog.vue";
import RepairDetailDrawer from "../../features/repairs/RepairDetailDrawer.vue";
import RepairLedgerTable from "../../features/repairs/RepairLedgerTable.vue";
import RepairStatusTabs from "../../features/repairs/RepairStatusTabs.vue";
import { useRepairManagementWorkspace } from "../../features/repairs/useRepairManagementWorkspace";

const {
  bindTableScroll,
  rememberTableScroll,
  appliedFilters,
  hasAppliedQuery,
  filtersPending,
  pendingQuery,
  exportDisabled,
  editorInitialStep,
  detailPosition,
  detailCanPrevious,
  detailCanNext,
  moveDetail,
  activeStatus,
  filters,
  statusCounts,
  repairPage,
  editorOpen,
  deleteTarget,
  detailTarget,
  agreementOpen,
  agreementTarget,
  agreementHtml,
  agreementLoading,
  agreementError,
  handlerCandidates,
  selectedHandler,
  revealedPhones,
  form,
  canManage,
  canDelete,
  canExport,
  repairTotalPages,
  filterError,
  unsaved,
  isPending,
  load,
  setStatus,
  setPage,
  retry,
  openEditor,
  closeEditor,
  editFromDetail,
  requestDelete,
  save,
  remove,
  preview,
  loadAgreement,
  closeAgreement,
  exportCases,
  phoneVisible,
  togglePhone,
  captureExportButton,
} = useRepairManagementWorkspace();

</script>
