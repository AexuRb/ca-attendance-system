<template>
  <section class="work-section tab-stats">
    <div class="section-head">
      <h3>统计与导出</h3>
      <button v-if="canExport" class="ghost-button" :disabled="busy" @click="exportExcel"><Download :size="16" />导出</button>
    </div>

    <div class="range-presets" aria-label="快捷时间范围">
      <button v-for="item in presets" :key="item.id" class="ghost-button" :class="{ active: preset === item.id }" @click="applyPreset(item.id)">
        {{ item.label }}
      </button>
    </div>

    <div class="filters stats-filters">
      <label class="filter-field" for="statsFrom"><span>开始日期</span><input id="statsFrom" v-model="range.from" name="from" type="date" @change="preset = 'custom'" /></label>
      <label class="filter-field" for="statsTo"><span>结束日期</span><input id="statsTo" v-model="range.to" name="to" type="date" @change="preset = 'custom'" /></label>
      <button class="ghost-button" :disabled="busy" @click="loadStats">查询</button>
    </div>

    <div class="stat-grid">
      <div><span>总人数</span><strong>{{ stats.length }}</strong></div>
      <div><span>总时长</span><strong>{{ formatHours(totalHours) }}</strong></div>
      <div><span>总次数</span><strong>{{ totalCount }}</strong></div>
    </div>

    <div v-if="preset === 'week'" class="weekly-detail-panel">
      <div class="subsection-head">
        <h4><CalendarDays :size="17" />本周值班日明细</h4>
        <span>{{ range.from }} 至 {{ range.to }}</span>
      </div>
      <div class="table-wrap weekly-matrix-wrap">
        <table class="weekly-matrix-table">
          <thead>
            <tr>
              <th>值班人员</th>
              <th v-for="day in weeklyDetail.days" :key="day.dutyDate"><span class="matrix-day-title">{{ day.weekdayName }}</span><small>{{ day.dutyDate }}</small></th>
              <th v-if="weeklyDetail.days.length === 0">暂无值班日</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in weeklyDetail.users" :key="user.userId">
              <td class="matrix-person-cell"><strong>{{ user.name }}</strong><span>{{ user.studentNo }}</span></td>
              <td v-for="day in weeklyDetail.days" :key="`${day.dutyDate}-${user.userId}`" class="matrix-hour" :class="{ filled: weeklyCell(day.dutyDate, user.userId) > 0 }">
                {{ formatHours(weeklyCell(day.dutyDate, user.userId)) }} h
              </td>
              <td v-if="weeklyDetail.days.length === 0" class="matrix-hour">0 h</td>
            </tr>
            <tr v-if="weeklyDetail.users.length === 0"><td :colspan="Math.max(2, weeklyDetail.days.length + 1)" class="empty">本周暂无值班人员</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="table-wrap">
      <table>
        <thead><tr><th>排行</th><th>姓名</th><th>学号</th><th>年级</th><th>次数</th><th>总时长</th></tr></thead>
        <tbody>
          <tr v-for="(row, index) in stats" :key="row.userId">
            <td class="mono">{{ index + 1 }}</td><td>{{ row.name }}</td><td class="mono">{{ row.studentNo }}</td><td>{{ row.grade || '-' }}</td><td>{{ row.dutyCount }}</td><td>{{ formatHours(row.totalHours) }} h</td>
          </tr>
          <tr v-if="stats.length === 0"><td colspan="6" class="empty">暂无有效统计</td></tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CalendarDays, Download } from '@lucide/vue'
import { api } from '../api.js'
import { compactQuery, queryDate, queryOneOf } from '../features/navigation/queryState.js'
import { formatLocalDate, resolveStatsRange } from '../features/stats/statsRange.js'

const props = defineProps({ currentUser: { type: Object, required: true } })
const emit = defineEmits(['notify'])
const route = useRoute()
const router = useRouter()

const now = new Date()
const todayValue = formatLocalDate(now)
const yearStart = `${now.getFullYear()}-01-01`
const presets = [
  { id: 'week', label: '本周' },
  { id: 'month', label: '本月' },
  { id: 'schoolYear', label: '本学年' }
]

const busy = ref(false)
const stats = ref([])
const weeklyDetail = ref(emptyWeeklyDetail())
const preset = ref('custom')
const lastAppliedRoute = ref('')
const range = reactive({ from: yearStart, to: todayValue })

const canExport = computed(() => ['PRESIDENT', 'ADMIN'].includes(props.currentUser.role))
const totalHours = computed(() => stats.value.reduce((sum, row) => sum + Number(row.totalHours || 0), 0))
const totalCount = computed(() => stats.value.reduce((sum, row) => sum + Number(row.dutyCount || 0), 0))

onMounted(async () => {
  hydrateRouteQuery()
  await loadStats(false)
})

watch(() => route.fullPath, async fullPath => {
  if (!route.path.endsWith('/stats') || fullPath === lastAppliedRoute.value) return
  hydrateRouteQuery()
  await loadStats(false)
})

function hydrateRouteQuery() {
  preset.value = queryOneOf(route.query, 'preset', ['custom', ...presets.map(item => item.id)], 'custom')
  range.from = queryDate(route.query, 'from', yearStart)
  range.to = queryDate(route.query, 'to', todayValue)
}

async function syncRouteQuery() {
  const location = { path: route.path, query: compactQuery({ preset: preset.value, from: range.from, to: range.to }) }
  const resolved = router.resolve(location)
  if (resolved.fullPath === route.fullPath) return
  lastAppliedRoute.value = resolved.fullPath
  await router.replace(location)
}

async function loadStats(syncRoute = true) {
  await run(async () => {
    const [summary, detail] = await Promise.all([
      api(`/api/stats/summary?from=${range.from}&to=${range.to}`),
      preset.value === 'week' ? api(`/api/stats/weekly-detail?from=${range.from}&to=${range.to}`) : Promise.resolve(emptyWeeklyDetail())
    ])
    stats.value = summary
    weeklyDetail.value = detail
    if (syncRoute) await syncRouteQuery()
  }, false)
}

async function applyPreset(value) {
  preset.value = value
  Object.assign(range, resolveStatsRange(value))
  await loadStats()
}

function weeklyCell(dutyDate, userId) {
  return Number(weeklyDetail.value.cells?.[dutyDate]?.[String(userId)] || 0)
}

async function exportExcel() {
  await run(async () => {
    const blob = await api(`/api/stats/export?from=${range.from}&to=${range.to}`)
    downloadBlob(blob, `值班记录_${range.from}_${range.to}.xlsx`)
  })
}

async function run(action, showError = true) {
  busy.value = true
  try { await action() } catch (error) { if (showError) notify(error.message, 'error') } finally { busy.value = false }
}

function notify(message, type = 'info') { emit('notify', { message, type }) }
function emptyWeeklyDetail() { return { days: [], users: [], cells: {} } }
function formatHours(value) { const number = Number(value || 0); return Number.isInteger(number) ? String(number) : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '') }
function downloadBlob(blob, filename) { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url) }
</script>
