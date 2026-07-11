<template>
  <section class="work-section tab-settings">
    <div class="section-head">
      <div><h3>值班设置</h3><span>控制签到台显示的值班星期和值班时间段</span></div>
    </div>

    <div class="settings-stack">
      <section class="settings-card">
        <div class="settings-card-head">
          <div><h4>值班星期</h4><span>关闭某天后，当天记录仍可测试提交，但不会计入有效值班</span></div>
          <button class="ghost-button" :disabled="busy" @click="saveWeekdays"><Save :size="16" />保存星期</button>
        </div>
        <div class="weekday-grid">
          <label v-for="day in weekdays" :key="day.weekday" :class="{ selected: day.enabled }">
            <input v-model="day.enabled" name="enabledWeekdays" type="checkbox" :value="day.weekday" @change="setDirty('weekdays', true)" />
            <CalendarDays :size="18" /><span>{{ day.weekday_name }}</span>
          </label>
        </div>
      </section>

      <section class="settings-card">
        <div class="settings-card-head">
          <div><h4>值班时间段</h4><span>签到台会按后台保存的值班时间段统计部长人数</span></div>
          <div class="settings-card-actions">
            <button class="ghost-button" :disabled="busy" @click="addDutyPeriod"><Plus :size="16" />新增时间段</button>
            <button class="ghost-button" :disabled="busy" @click="saveDutyPeriods"><Save :size="16" />保存时间段</button>
          </div>
        </div>
        <div class="duty-period-list">
          <div v-if="drafts.length === 0" class="duty-period-empty"><Clock3 :size="18" /><strong>暂无值班时间段</strong></div>
          <article v-for="(period, index) in drafts" :key="`${period.startTime}-${period.endTime}-${index}`" class="duty-period-row">
            <label :for="`dutyPeriodStart-${index}`"><span>开始</span><input :id="`dutyPeriodStart-${index}`" v-model="period.startTime" name="startTime" type="time" @change="setDirty('periods', true)" /></label>
            <span>至</span>
            <label :for="`dutyPeriodEnd-${index}`"><span>结束</span><input :id="`dutyPeriodEnd-${index}`" v-model="period.endTime" name="endTime" type="time" @change="setDirty('periods', true)" /></label>
            <button class="ghost-button danger-button" type="button" @click="removeDutyPeriod(index)">删除</button>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { CalendarDays, Clock3, Plus, Save } from '@lucide/vue'
import { api, put } from '../api.js'
import { nextPeriodEnd, normalizeDutyPeriods, parseDutyPeriodDrafts } from '../features/schedule/dutyPeriods.js'

const emit = defineEmits(['notify', 'dirty-change', 'updated'])
const busy = ref(false)
const weekdays = ref([])
const savedPeriods = ref([])
const drafts = ref([])
const dirtyScopes = ref(new Set())

onMounted(loadSettings)
onBeforeUnmount(() => emit('dirty-change', false))

async function loadSettings() {
  await run(async () => {
    const [days, periods] = await Promise.all([api('/api/settings/weekdays'), api('/api/settings/duty-periods')])
    weekdays.value = days
    savedPeriods.value = normalizeDutyPeriods(periods)
    resetDrafts()
  }, false)
}

async function saveWeekdays() {
  await run(async () => {
    await put('/api/settings/weekdays', { enabledWeekdays: weekdays.value.filter(day => day.enabled).map(day => day.weekday) })
    setDirty('weekdays', false)
    notify('值班星期已保存', 'success')
    emit('updated')
  })
}

function addDutyPeriod() {
  const last = drafts.value.at(-1)
  drafts.value.push({ startTime: last?.endTime || '', endTime: last?.endTime ? nextPeriodEnd(last.endTime) : '' })
  setDirty('periods', true)
}

function removeDutyPeriod(index) {
  drafts.value.splice(index, 1)
  setDirty('periods', true)
}

async function saveDutyPeriods() {
  const { periods, error } = parseDutyPeriodDrafts(drafts.value)
  if (error) return notify(error, 'warn')
  await run(async () => {
    savedPeriods.value = normalizeDutyPeriods(await put('/api/settings/duty-periods', { periods }))
    resetDrafts()
    setDirty('periods', false)
    notify('值班时间段已保存', 'success')
    emit('updated')
  })
}

function resetDrafts() {
  drafts.value = savedPeriods.value.map(period => ({ startTime: period.startTime, endTime: period.endTime }))
}

function setDirty(scope, value) {
  const next = new Set(dirtyScopes.value)
  if (value) next.add(scope)
  else next.delete(scope)
  dirtyScopes.value = next
  emit('dirty-change', next.size > 0)
}

async function run(action, showError = true) {
  busy.value = true
  try { await action() } catch (error) { if (showError) notify(error.message, 'error') } finally { busy.value = false }
}

function notify(message, type = 'info') { emit('notify', { message, type }) }
</script>
