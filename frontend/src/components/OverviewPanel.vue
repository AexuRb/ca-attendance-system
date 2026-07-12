<template>
  <section class="work-section tab-overview">
    <section class="today-attention-board" aria-labelledby="today-attention-title">
      <div class="subsection-head">
        <div>
          <h4 id="today-attention-title"><AlertTriangle :size="17" />今日待处理</h4>
          <span v-if="todayIssues.length">{{ todayIssues.length }} 类事项需要关注</span>
          <span v-else>当前没有需要处理的异常事项</span>
        </div>
      </div>

      <div v-if="todayIssues.length" class="today-issue-grid">
        <component
          :is="issue.actionable ? 'button' : 'article'"
          v-for="issue in todayIssues"
          :key="issue.id"
          :type="issue.actionable ? 'button' : undefined"
          class="today-issue-item"
          :class="[`tone-${issue.tone}`, { actionable: issue.actionable }]"
          @click="openIssue(issue)"
        >
          <span class="today-issue-icon"><component :is="todayIssueIcon(issue.id)" :size="19" /></span>
          <span class="today-issue-copy">
            <strong>{{ issue.title }}</strong>
            <small>{{ issue.detail }}</small>
          </span>
          <strong class="today-issue-count">{{ issue.count }}<small>{{ issue.unit }}</small></strong>
          <ChevronRight v-if="issue.actionable" class="today-issue-arrow" :size="17" />
        </component>
      </div>

      <div v-else class="today-clear-state" role="status">
        <span><CheckCircle2 :size="20" /></span>
        <div>
          <strong>今日暂无待处理事项</strong>
          <small>{{ clearDetail }}</small>
        </div>
      </div>
    </section>

    <div class="admin-home-layout">
      <section class="admin-duty-board" aria-labelledby="admin-duty-title">
        <div class="subsection-head">
          <div>
            <h4 id="admin-duty-title"><CalendarDays :size="17" />今日部长排班</h4>
            <span>按后台设置的值班时段自动分组</span>
          </div>
          <button v-if="canManageSchedules" class="ghost-button" type="button" @click="$emit('select-tab', 'schedules')">
            <Plus :size="16" />安排排班
          </button>
        </div>
        <div v-if="todayPeriodSummary.length" class="admin-duty-timeline">
          <article
            v-for="period in todayPeriodSummary"
            :key="period.key"
            class="admin-duty-period"
            :class="{ active: period.active, missing: period.missing }"
          >
            <time>{{ period.startTime }}</time>
            <span class="admin-duty-node" aria-hidden="true"></span>
            <div class="admin-duty-card">
              <div class="admin-duty-card-head">
                <strong>{{ period.timeText }}</strong>
                <span>{{ period.count }} 人</span>
              </div>
              <div class="admin-duty-members" :class="{ empty: !period.people.length }">
                <span v-for="person in period.people" :key="person.key">{{ person.name }}</span>
                <em v-if="!period.people.length">待安排部长</em>
              </div>
            </div>
          </article>
        </div>
        <div v-else class="admin-duty-empty">
          <Clock3 :size="22" />
          <strong>{{ emptyBoardText }}</strong>
          <span>保存值班时间段和排班后，这里会和签到台同步显示。</span>
        </div>
      </section>

      <section class="overview-panel today-records-panel" aria-labelledby="today-records-title">
        <div class="subsection-head">
          <div>
            <h4 id="today-records-title"><ClipboardList :size="17" />今日值班记录</h4>
            <span>{{ todayRecords.length }} 条</span>
          </div>
          <button v-if="canOpenRecords" class="ghost-button" type="button" @click="$emit('select-tab', 'records')">
            全部记录<ChevronRight :size="16" />
          </button>
        </div>
        <div class="table-wrap compact-table overview-table-wrap">
          <table>
            <thead>
              <tr><th>姓名</th><th>签到</th><th>签退</th><th>状态</th><th>时长</th></tr>
            </thead>
            <tbody>
              <tr v-for="row in todayRecords" :key="row.id">
                <td>{{ row.name }}</td>
                <td>{{ timeText(row.checkInTime) }}</td>
                <td>{{ timeText(row.checkOutTime) }}</td>
                <td><span class="status-badge" :class="row.effectiveStatus?.toLowerCase()">{{ effectiveStatusText(row.effectiveStatus) }}</span></td>
                <td>{{ formatHours(row.validHours) }} h</td>
              </tr>
              <tr v-if="todayRecords.length === 0"><td colspan="5" class="empty">今日暂无值班记录</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import {
  AlertTriangle,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  ClipboardList,
  Clock3,
  ListChecks,
  Plus,
  Wrench
} from '@lucide/vue'

defineProps({
  todayIssues: { type: Array, default: () => [] },
  todayPeriodSummary: { type: Array, default: () => [] },
  todayRecords: { type: Array, default: () => [] },
  emptyBoardText: { type: String, default: '' },
  clearDetail: { type: String, default: '' },
  canManageSchedules: { type: Boolean, default: false },
  canOpenRecords: { type: Boolean, default: false }
})

const emit = defineEmits(['select-tab'])

function openIssue(issue) {
  if (issue?.actionable) emit('select-tab', issue.tab)
}

function todayIssueIcon(issueId) {
  return {
    pending: ListChecks,
    open: Clock3,
    schedule: CalendarDays,
    repairs: Wrench
  }[issueId] || AlertTriangle
}

function effectiveStatusText(status) {
  return {
    PENDING: '待处理',
    VALID: '有效',
    INVALID: '无效',
    INCOMPLETE: '未签退'
  }[status] || status
}

function timeText(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

function formatHours(value) {
  const number = Number(value || 0)
  return Number.isInteger(number) ? String(number) : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}
</script>
