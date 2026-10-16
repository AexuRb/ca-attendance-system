<template>
  <ExpandedWorkspaceShell class="support-workspace logs-presentation overview-logs" current-page="logs">
    <template #heading><h1>操作日志</h1></template>
    <form class="filter-bar logs-filter" aria-label="筛选操作日志" @submit.prevent="applyFilters">
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
    </form>
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
    <div v-if="loaded && (hasAppliedFilters || filtersPending || listLoading || listError)" class="audit-query-context">
      <span aria-live="polite">当前结果：{{ appliedFilters.from || '不限开始日期' }} — {{ appliedFilters.to || '不限结束日期' }} · {{ appliedFilters.actionType ? actionLabel(appliedFilters.actionType) : '全部操作' }}<template v-if="appliedFilters.keyword"> · 关键词：{{ appliedFilters.keyword }}</template></span>
      <QueryStatus :loading="listLoading" :failed="Boolean(listError)" :dirty="filtersPending" loading-text="正在更新，暂示上次结果" failed-text="查询未成功，重试将沿用上次请求条件" dirty-text="筛选已修改，查询后生效；翻页仍沿用当前结果条件" />
    </div>
    <div ref="resultsStart" class="audit-results" tabindex="-1" aria-label="日志查询结果">
    <LoadingBlock v-if="listLoading && !items.length" /><EmptyState
      v-else-if="!items.length && !listError"
      :title="hasAppliedFilters ? '没有符合条件的日志' : '暂无操作日志'"
    />
    <div v-else-if="items.length" class="audit-records" role="list" aria-label="操作日志" :class="{ 'is-refreshing': listLoading }" :aria-busy="listLoading"><div class="audit-record-head" aria-hidden="true"><span>时间</span><span>操作人</span><span>操作内容</span><span>操作说明</span><span>详情</span></div>
      <article v-for="item in items" :key="item.id" role="listitem">

        <div class="log-time">
          <strong>{{ time(item.createdAt) }}</strong
          ><span>{{ date(item.createdAt) }}</span>
        </div>
        <div class="audit-identity"><strong>{{ item.operatorName || "系统" }}</strong><small v-if="item.operatorStudentNo">{{ item.operatorStudentNo }}</small></div>
        <div class="audit-operation">
        <StatusBadge :label="actionLabel(item.actionType)" :tone="actionTone(item.actionType)" />
        <span>{{ targetLabel(item) }}</span></div>
        <p class="audit-reason" :title="auditSummary(item)">{{ auditSummary(item) }}</p>
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
      backdrop-class="logs-refined-backdrop"
      title="操作详情"
      size="lg"
      @close="detail = null"
      ><div v-if="detail" class="log-detail audit-detail-surface">
        <dl>
          <div>
            <dt>操作时间</dt>
            <dd>{{ detail.createdAt.replace('T', ' ').slice(0, 19) }}</dd>
          </div>
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
          <div v-if="detail.reason" class="audit-detail-reason">
            <dt>操作原因</dt>
            <dd>{{ detail.reason }}</dd>
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
        <p v-else class="audit-event-note">{{ detail.actionType.includes('LOGIN') ? '本次为登录事件，没有字段变更。' : '本次操作未记录字段变更。' }}</p>
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
        ><button data-button-kind="entry" class="button primary" @click="detail = null">
          关闭
        </button></template
      ></ModalDialog
    >

  </ExpandedWorkspaceShell>
</template>
<script setup lang="ts">
import {
  ChevronLeft,
  ChevronRight,
  Eye,
  Search,
} from "@lucide/vue";
import ExpandedWorkspaceShell from "../../layouts/ExpandedWorkspaceShell.vue";
import QueryStatus from "../../shared/ui/QueryStatus.vue";
import { nextTick, provide, ref } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/today/overview-presentation.css";
import "../../features/audit/refined-presentation.css";
provide(memberPresentationKey, true);
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ModalDialog from "../../shared/ui/ModalDialog.vue";
import { useAuditLogWorkspace } from "../../features/audit/useAuditLogWorkspace";

const {
  actionLabel, actionTone, auditSummary, applyFilters, auditActionOptions,
  date, detail, detailRows, displayError, filters, filtersPending, appliedFilters, loaded, hasAppliedFilters, items, listError, listLoading,
  page, pretty, retryLoad, setPage: queryPage, targetLabel, time, total, totalPages,
} = useAuditLogWorkspace();

const resultsStart = ref<HTMLElement | null>(null);
async function setPage(target: number) {
  if (!await queryPage(target)) return;
  await nextTick();
  resultsStart.value?.focus({ preventScroll: true });
  resultsStart.value?.scrollIntoView?.({ block: "start", behavior: "instant" });
}
</script>
