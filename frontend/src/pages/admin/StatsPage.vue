<template>
  <div class="page-stack stats-page">
    <PageHeader
      title="值班统计"
      ><template #actions
        ><button :ref="captureExportButton" class="button primary" :disabled="actions.isPending('export') || Boolean(filterError)" @click="exportExcel">
          <Download />导出 Excel
        </button></template
      ></PageHeader
    >
    <form class="filter-bar stats-filter" @submit.prevent="loadCustom">
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
    </form>
    <div v-if="displayError" class="inline-alert danger" role="alert">
      <span>{{ displayError }}</span>
      <button
        v-if="loadError"
        class="button secondary small"
        type="button"
        data-action="retry-stats"
        @click="load"
      >
        重试
      </button>
    </div>
    <section class="metric-strip compact stats-metrics">
      <article class="stats-metric-members">
        <span>统计成员</span><strong>{{ rows.length }}</strong
        ><small>人</small>
      </article>
      <article class="stats-metric-hours">
        <span>总有效时长</span><strong>{{ totalHours }}</strong
        ><small>小时</small>
      </article>
      <article class="stats-metric-attendance">
        <span>值班记录</span><strong>{{ totalAttendance }}</strong
        ><small>次</small>
      </article>
      <article class="stats-metric-training">
        <span>培训记录</span><strong>{{ totalTraining }}</strong
        ><small>次</small>
      </article>
    </section>
    <section class="stats-results">
      <LoadingBlock v-if="loading && !hasData" />
      <EmptyState v-else-if="!hasData && !loadError" title="该时间段暂无有效统计" />
      <WeeklyStatsTable
        v-else-if="preset === 'week'"
        :detail="weeklyDetail"
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
            <th>有效次数</th>
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
              <strong>{{ item.name }}</strong
              ><small>{{ item.studentNo }}</small>
            </td>
            <td>{{ item.grade || "—" }}</td>
            <td>{{ roleLabel(item.role) }}</td>
            <td>{{ number(item.attendanceHours ?? item.dutyHours) }} 小时</td>
            <td>{{ number(item.trainingHours) }} 小时</td>
            <td>
              <strong class="total-hours">{{ number(item.totalHours) }}</strong>
              小时
            </td>
            <td>{{ effectiveDutyCount(item) }}</td>
          </tr>
        </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { BarChart3, Download } from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import WeeklyStatsTable from "../../features/stats/WeeklyStatsTable.vue";
import { effectiveDutyCount } from "../../features/stats/statsSummary";
import { useStatsWorkspace } from "../../features/stats/useStatsWorkspace";

const {
  actions,
  applyPreset,
  captureExportButton,
  displayError,
  exportExcel,
  filterError,
  from,
  hasData,
  load,
  loadCustom,
  loading,
  loadError,
  number,
  preset,
  presets,
  roleLabel,
  rows,
  to,
  totalAttendance,
  totalHours,
  totalTraining,
  weeklyDetail,
} = useStatsWorkspace();
</script>
