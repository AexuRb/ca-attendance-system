<template>
  <div class="page-stack">
    <PageHeader
      title="维修事务"
      ><template #actions
        ><button
          :ref="captureExportButton"
          v-if="canExport"
          class="button secondary"
          :disabled="isPending('export-repairs') || Boolean(filterError)"
          @click="exportCases"
        >
          <Download />{{ isPending('export-repairs') ? "正在导出" : "导出" }}</button
        ><button v-if="canManage" class="button primary" @click="openEditor()">
          <Plus />新建维修
        </button></template
      ></PageHeader
    >
    <form class="repair-filter-shell" @submit.prevent="load">
      <div class="repair-search-row">
        <Search aria-hidden="true" />
        <label>
          <span class="sr-only">搜索维修事务</span>
          <input
            v-model.trim="filters.keyword"
            type="search"
            name="repair-search"
            placeholder="搜索编号、联系人、设备或故障"
            autocomplete="off"
          />
        </label>
        <button class="button secondary small" type="submit">搜索</button>
        <button
          class="button text small repair-filter-toggle"
          type="button"
          :aria-expanded="filterOpen"
          aria-controls="repair-date-filters"
          @click="filterOpen = !filterOpen"
        >
          <SlidersHorizontal aria-hidden="true" />日期
        </button>
      </div>
      <Transition name="filter-expand">
        <div v-if="filterOpen" id="repair-date-filters" class="repair-date-filters">
          <label><span>开始日期</span><input v-model="filters.from" name="repairFrom" type="date" /></label>
          <label><span>结束日期</span><input v-model="filters.to" name="repairTo" type="date" /></label>
          <button class="button secondary small" type="submit">应用筛选</button>
        </div>
      </Transition>
    </form>
    <div v-if="filterError" class="inline-alert danger" role="alert">
      {{ filterError }}
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
        :items="repairPage.items"
        :status="activeStatus"
        :loading="repairPage.loading"
        :error="repairPage.error"
        :revealed-phones="revealedPhones"
        :can-manage="canManage"
        :can-delete="canDelete"
        @view="detailTarget = $event"
        @preview="preview"
        @edit="openEditor"
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
      :can-manage="canManage"
      :can-delete="canDelete"
      @close="detailTarget = null"
      @preview="preview"
      @edit="editFromDetail"
      @delete="requestDelete"
      @toggle-phone="togglePhone"
    />
    <RepairEditorDialog
      :open="editorOpen"
      :form="form"
      :handler="selectedHandler"
      :candidates="handlerCandidates"
      :pending="isPending('save-repair')"
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
  </div>
</template>
<script setup lang="ts">
import {
  ArrowLeft,
  ArrowRight,
  Download,
  Plus,
  Search,
  SlidersHorizontal,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import AgreementDialog from "../../shared/ui/AgreementDialog.vue";
import RepairEditorDialog from "../../features/repairs/RepairEditorDialog.vue";
import RepairDetailDrawer from "../../features/repairs/RepairDetailDrawer.vue";
import RepairLedgerTable from "../../features/repairs/RepairLedgerTable.vue";
import RepairStatusTabs from "../../features/repairs/RepairStatusTabs.vue";
import { useRepairManagementWorkspace } from "../../features/repairs/useRepairManagementWorkspace";

const {
  activeStatus,
  filters,
  statusCounts,
  repairPage,
  editorOpen,
  filterOpen,
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
