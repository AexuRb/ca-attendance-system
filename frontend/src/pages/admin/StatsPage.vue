<template>
  <RefinedWorkspaceShell class="support-workspace stats-presentation" title="值班统计" description="按日期查看值班与培训时长" section-key="duty" filter-label="筛选值班统计">
    <template #tools><button :ref="captureExportButton" class="button primary" :disabled="actions.isPending('export') || !exportReady" :title="exportReady ? '导出当前统计结果' : '请先完成统计，再导出当前日期范围'" @click="exportExcel">
          <Download />{{ actions.isPending('export') ? '正在导出' : '导出 Excel' }}
        </button></template>
    <template #filters><form class="filter-bar stats-filter" @submit.prevent="loadCustom">
      <div class="segmented">
        <button
          v-for="option in presets"
          :key="option.id"
          type="button"
          :class="{ active: preset === option.id }"
          @click="applyPreset(option.id)"
        >
          {{ option.label }}
        </button>
      </div>
      <label><span>开始日期</span><input v-model="from" name="statsFrom" type="date" /></label
      ><label><span>结束日期</span><input v-model="to" name="statsTo" type="date" /></label
      ><button class="button secondary" type="submit"><BarChart3 />统计</button>
    </form></template>
    <div v-if="displayError" class="inline-alert danger" role="alert">
      <span>{{ displayError }}{{ loadError && loaded ? '；下方保留上次成功结果，重试沿用失败条件' : '' }}</span>
      <button
        v-if="loadError"
        class="button secondary small"
        type="button"
        data-action="retry-stats"
        @click="retry"
      >
        重试
      </button>
    </div>
    <section class="metric-strip compact stats-metrics" aria-label="统计概况">
      <article class="stats-metric-members">
        <span>统计成员</span><strong>{{ loaded ? rows.length : '—' }}</strong
        ><small>人</small>
      </article>
      <article class="workspace-metric-hours">
        <span>总有效时长</span><strong>{{ loaded ? totalHours : '—' }}</strong
        ><small>小时</small>
      </article>
      <article class="stats-metric-attendance">
        <span>有效值班次数</span><strong>{{ loaded ? totalAttendance : '—' }}</strong
        ><small>次</small>
      </article>
      <article class="stats-metric-training">
        <span>培训参与次数</span><strong>{{ loaded ? totalTraining : '—' }}</strong
        ><small>次</small>
      </article>
    </section>
    <section class="stats-results">
      <div v-if="loaded" class="stats-results-context">
        <span aria-live="polite">当前结果 <strong>{{ loadedRange.from }} — {{ loadedRange.to }}</strong></span>
        <QueryStatus :class="{ 'stats-results-context__pending': rangeDirty && !loading && !loadError }" :loading="loading" :failed="Boolean(loadError)" :dirty="rangeDirty" loading-text="正在更新统计…" failed-text="统计未成功，重试将沿用上次请求日期" dirty-text="日期已修改，点击“统计”应用" />
      </div>
      <LoadingBlock v-if="loading && !loaded" />
      <EmptyState v-else-if="loadError && !loaded" title="统计结果暂不可用" description="请使用上方的重试按钮重新获取统计结果" />
      <EmptyState v-else-if="loaded && !hasData" title="该时间段暂无有效统计" />
      <Transition name="stats-result-swap" mode="out-in">
      <div v-if="loaded && hasData" :key="`${loadedPreset}-${loadedRange.from}-${loadedRange.to}`" class="stats-result-content">
      <WeeklyStatsTable
        v-if="loadedPreset === 'week'"
        :detail="weeklyDetail"
        :disabled="loading || Boolean(loadError)"
        @select-member="openMemberById"
      />
      <div v-else class="table-shell stats-ranking-table">
        <table>
        <thead>
          <tr>
            <th>#</th>
            <th>成员</th>
            <th>年级</th>
            <th>角色</th>
            <th>值班时长</th>
            <th>培训时长</th>
            <th>合计时长</th>
            <th>有效记录次数</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(item, index) in rows"
            :key="item.userId || item.studentNo"
          >
            <td>
              <span class="rank" :data-rank="index + 1">{{ index + 1 }}</span>
            </td>
            <td>
              <span class="stats-member-title"><strong>{{ item.name }}</strong><button class="stats-detail-trigger" type="button" :disabled="loading || Boolean(loadError)" @click="openMemberDetail(item)">查看详情</button></span>
              <small>{{ item.studentNo }}</small>
            </td>
            <td>{{ item.grade || "—" }}</td>
            <td>{{ roleLabel(item.role) }}</td>
            <td>{{ number(item.attendanceHours ?? item.dutyHours) }} 小时</td>
            <td>{{ number(item.trainingHours) }} 小时</td>
            <td><span class="stats-ranking-total"><strong class="total-hours">{{ number(item.totalHours) }}</strong><small>小时</small></span></td>
            <td>{{ effectiveDutyCount(item) }}</td>
          </tr>
        </tbody>
        </table>
      </div>
      <ol v-if="loadedPreset !== 'week'" class="stats-ranking-mobile" aria-label="成员时长排行">
        <li v-for="(item, index) in rows" :key="item.userId || item.studentNo" class="stats-ranking-mobile-record">
          <div class="stats-ranking-mobile-record__head">
            <span class="rank" :data-rank="index + 1">{{ index + 1 }}</span>
            <div class="stats-ranking-mobile-record__person"><strong>{{ item.name }}</strong><small>{{ item.studentNo }} · {{ item.grade || '年级未知' }} · {{ roleLabel(item.role) }}</small></div>
            <div class="stats-ranking-mobile-record__total"><strong>{{ number(item.totalHours) }}</strong><small>合计小时</small></div>
          </div>
          <dl class="stats-ranking-mobile-record__breakdown">
            <div><dt>值班</dt><dd>{{ number(item.attendanceHours ?? item.dutyHours) }} 小时</dd></div>
            <div><dt>培训</dt><dd>{{ number(item.trainingHours) }} 小时</dd></div>
            <div><dt>有效记录</dt><dd>{{ effectiveDutyCount(item) }} 次</dd></div>
          </dl>
          <button class="stats-detail-trigger stats-ranking-mobile-record__detail" type="button" :disabled="loading || Boolean(loadError)" @click="openMemberDetail(item)">查看详情</button>
        </li>
      </ol>
      </div>
      </Transition>
    </section>
    <StatsMemberDetail
      :open="!!selectedMember"
      :member="selectedMember"
      :detail="memberDetail"
      :from="loadedRange.from"
      :to="loadedRange.to"
      :loading="memberDetailLoading"
      :error="memberDetail ? '' : memberDetailError"
      @close="closeMemberDetail"
      @retry="fetchMemberDetail"
    />
  </RefinedWorkspaceShell>
</template>

<script setup lang="ts">
import { BarChart3, Download } from "@lucide/vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import QueryStatus from "../../shared/ui/QueryStatus.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../styles/workspace.css";
import "../../features/workspaces/presentation.css";
import "../../features/stats/presentation.css";
provide(memberPresentationKey, true);
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import WeeklyStatsTable from "../../features/stats/WeeklyStatsTable.vue";
import StatsMemberDetail from "../../features/stats/StatsMemberDetail.vue";
import { effectiveDutyCount } from "../../features/stats/statsSummary";
import { useStatsWorkspace } from "../../features/stats/useStatsWorkspace";

const {
  actions,
  applyPreset,
  captureExportButton,
  closeMemberDetail,
  displayError,
  exportReady,
  exportExcel,
  fetchMemberDetail,
  from,
  hasData,
  retry,
  loadedPreset,
  loadCustom,
  loading,
  loadError,
  loadedRange,
  memberDetail,
  memberDetailError,
  memberDetailLoading,
  loaded,
  number,
  openMemberById,
  openMemberDetail,
  preset,
  presets,
  rangeDirty,
  roleLabel,
  rows,
  selectedMember,
  to,
  totalAttendance,
  totalHours,
  totalTraining,
  weeklyDetail,
} = useStatsWorkspace();
</script>
