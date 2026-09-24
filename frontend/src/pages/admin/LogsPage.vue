<template>
  <RefinedWorkspaceShell class="support-workspace logs-presentation" title="操作日志" description="追溯系统内的重要操作" section-key="system" filter-label="筛选操作日志">
    <template #tools><button class="button secondary" :disabled="actions.isPending('export')" title="按当前列表的筛选条件导出" @click="exportLogs">
          <Download />导出日志</button
        ><button class="button danger" :disabled="actions.isPending('clear')" @click="clearOpen = true">
          <Trash2 />清空日志
        </button></template>
    <template #filters><form class="filter-bar" @submit.prevent="applyFilters">
      <label class="filter-grow"
        ><span>关键词</span
        ><input
          v-model.trim="filters.keyword"
          name="logKeyword"
          type="search"
          autocomplete="off"
          placeholder="操作人、对象或原因" /></label
      ><label
        ><span>操作类型</span
        ><select v-model="filters.actionType" name="logActionType">
          <option value="">全部操作</option>
          <option
            v-for="option in auditActionOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select></label
      ><label
        ><span>开始日期</span
        ><input v-model="filters.from" name="logFrom" type="date" /></label
      ><label
        ><span>结束日期</span><input v-model="filters.to" name="logTo" type="date" /></label
      ><button class="button secondary" type="submit"><Search />查询</button>
    </form></template>
    <div v-if="displayError" class="inline-alert danger" role="alert">
      <span>{{ displayError }}</span>
      <button
        v-if="listError"
        class="button secondary small"
        type="button"
        data-action="retry-logs"
        @click="retryLoad"
      >
        重试
      </button>
    </div>
    <LoadingBlock v-if="listLoading && !items.length" /><EmptyState
      v-else-if="!items.length && !listError"
      :title="hasAppliedFilters && !justCleared ? '没有符合条件的日志' : '暂无操作日志'"
    />
    <div v-else-if="items.length" class="audit-records" :class="{ 'is-refreshing': listLoading }" :aria-busy="listLoading"><div class="audit-record-head" aria-hidden="true"><span>时间</span><span>操作人 / 对象</span><span>操作类型</span><span>操作说明</span><span>详情</span></div>
      <article v-for="item in items" :key="item.id">

        <div class="log-time">
          <strong>{{ time(item.createdAt) }}</strong
          ><span>{{ date(item.createdAt) }}</span>
        </div>
        <div class="audit-identity"><strong>{{ item.operatorName || "系统" }}</strong><span>{{ targetLabel(item) }}</span><small v-if="item.operatorStudentNo">{{ item.operatorStudentNo }}</small></div>
        <StatusBadge :label="actionLabel(item.actionType)" :tone="actionTone(item.actionType)" />
        <p class="audit-reason">{{ item.reason || "未填写操作原因" }}</p>
        <button
          class="icon-button"
          :title="`查看${actionLabel(item.actionType)}详情`"
          :aria-label="`查看变更详情：${item.operatorName || '系统'}，${date(item.createdAt)} ${time(item.createdAt)}，${actionLabel(item.actionType)}`"
          type="button"
          @click="detail = item"
        >
          <Eye aria-hidden="true" />
        </button>
      </article>
    </div>
    <div v-if="total" class="pagination">
      <span>共 {{ total }} 条日志</span>
      <div>
        <button
          class="button secondary small"
          :disabled="page <= 1 || listLoading"
          @click="setPage(page - 1)"
        >
          <ChevronLeft />上一页</button
        ><span>第 {{ page }} / {{ totalPages }} 页</span
        ><button
          class="button secondary small"
          :disabled="page >= totalPages || listLoading"
          @click="setPage(page + 1)"
        >
          下一页<ChevronRight />
        </button>
      </div>
    </div>
    <ModalDialog
      :open="Boolean(detail)"
      title="操作详情"
      size="lg"
      @close="detail = null"
      ><div v-if="detail" class="log-detail audit-detail-surface">
        <dl>
          <div>
            <dt>操作人</dt>
            <dd>
              {{ detail.operatorName || "系统" }}（{{
                detail.operatorStudentNo || "—"
              }}）
            </dd>
          </div>
          <div>
            <dt>操作类型</dt>
            <dd>{{ actionLabel(detail.actionType) }}</dd>
          </div>
          <div>
            <dt>对象</dt>
            <dd>{{ targetLabel(detail) }}</dd>
          </div>
          <div>
            <dt>操作原因</dt>
            <dd>{{ detail.reason || "—" }}</dd>
          </div>
        </dl>
        <div v-if="detailRows.length" class="audit-diff-table" role="table">
          <div class="audit-diff-head" role="row">
            <strong role="columnheader">字段</strong>
            <strong role="columnheader">修改前</strong>
            <strong role="columnheader">修改后</strong>
          </div>
          <div v-for="row in detailRows" :key="row.key" role="row">
            <strong role="cell">{{ row.label }}</strong>
            <span role="cell" data-label="修改前">{{ row.before }}</span>
            <span role="cell" class="after-value" data-label="修改后">{{ row.after }}</span>
          </div>
        </div>
        <EmptyState v-else title="这次操作没有可比较的字段" />
        <details class="audit-raw-details">
          <summary>查看原始数据</summary>
          <div class="diff-grid">
            <section>
              <h3>变更前</h3>
              <pre>{{ pretty(detail.beforeData) }}</pre>
            </section>
            <section>
              <h3>变更后</h3>
              <pre>{{ pretty(detail.afterData) }}</pre>
            </section>
          </div>
        </details>
      </div>
      <template #footer
        ><button class="button primary" @click="detail = null">
          关闭
        </button></template
      ></ModalDialog
    >
    <ConfirmDialog
      :open="clearOpen"
      title="清空操作日志"
      message="清空前系统会自动创建安全备份，该操作仅影响日志记录。"
      confirm-label="备份并清空"
      danger
      :pending="actions.isPending('clear')"
      @cancel="clearOpen = false"
      @confirm="clearLogs"
    />
  </RefinedWorkspaceShell>
</template>
<script setup lang="ts">
import {
  ChevronLeft,
  ChevronRight,
  Download,
  Eye,
  Search,
  Trash2,
} from "@lucide/vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../features/members/presentation.css";
import "../../features/workspaces/presentation.css";
provide(memberPresentationKey, true);
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import ConfirmDialog from "../../shared/ui/ConfirmDialog.vue";
import { useAuditLogWorkspace } from "../../features/audit/useAuditLogWorkspace";

const {
  actionLabel, actionTone, actions, applyFilters, auditActionOptions, clearLogs, clearOpen,
  date, detail, detailRows, displayError, exportLogs, filters, hasAppliedFilters, items, justCleared, listError, listLoading,
  page, pretty, retryLoad, setPage, targetLabel, time, total, totalPages,
} = useAuditLogWorkspace();
</script>
