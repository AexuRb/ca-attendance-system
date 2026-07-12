<template>
  <section class="work-section tab-reviews">
    <div class="section-head">
      <h3>待审核记录</h3>
      <button class="ghost-button" type="button" data-action="refresh-reviews" :disabled="busy" @click="loadPending">
        <RefreshCw :size="16" />刷新
      </button>
    </div>
    <div v-if="pendingRecords.length" class="review-bulk-actions">
      <button class="ghost-button" type="button" :disabled="busy" @click="bulkReview('CHECK_IN')">全部通过签到</button>
      <button class="ghost-button" type="button" :disabled="busy" @click="bulkReview('CHECK_OUT')">全部通过签退</button>
      <button class="ghost-button" type="button" data-action="approve-all" :disabled="busy" @click="bulkReview('ALL')">全部通过</button>
    </div>
    <div v-if="pendingRecords.length === 0" class="empty-state">
      <ListChecks :size="34" />
      <strong>当前没有待审核记录</strong>
      <span>成员或部长提交后，需要处理的签到/签退会出现在这里。</span>
      <button class="ghost-button" type="button" :disabled="busy" @click="loadPending"><RefreshCw :size="16" />再检查一次</button>
    </div>
    <div v-else class="table-wrap">
      <table>
        <thead>
          <tr><th>姓名</th><th>学号</th><th>日期</th><th>签到</th><th>签退</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in pendingRecords" :key="item.id">
            <td>{{ item.name }}</td>
            <td class="mono">{{ item.studentNo }}</td>
            <td>{{ item.dutyDate }}</td>
            <td>{{ statusText(item.checkInStatus) }}</td>
            <td>{{ statusText(item.checkOutStatus) }}</td>
            <td class="actions">
              <button v-if="item.checkInStatus === 'PENDING'" type="button" data-action="approve-check-in" :disabled="busy" @click="review(item.id, 'CHECK_IN', 'APPROVE')">通过签到</button>
              <button v-if="item.checkInStatus === 'PENDING'" class="danger" type="button" :disabled="busy" @click="review(item.id, 'CHECK_IN', 'REJECT')">驳回签到</button>
              <button v-if="item.checkOutStatus === 'PENDING'" type="button" :disabled="busy" @click="review(item.id, 'CHECK_OUT', 'APPROVE')">通过签退</button>
              <button v-if="item.checkOutStatus === 'PENDING'" class="danger" type="button" :disabled="busy" @click="review(item.id, 'CHECK_OUT', 'REJECT')">驳回签退</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ListChecks, RefreshCw } from '@lucide/vue'
import { api, post } from '../api.js'
import { requestConfirmation, requestTextInput } from '../shared/confirm.js'

const emit = defineEmits(['notify', 'updated'])
const pendingRecords = ref([])
const pendingOperations = ref(0)
const busy = computed(() => pendingOperations.value > 0)

onMounted(loadPending)

async function loadPending() {
  await run(async () => {
    pendingRecords.value = await api('/api/attendance/reviews/pending')
  }, false)
}

async function review(id, part, action) {
  const reason = action === 'REJECT'
    ? await requestTextInput({
        title: '驳回签到记录',
        message: '请填写成员能够理解的具体原因。',
        inputLabel: '驳回原因',
        inputPlaceholder: '例如：签到时间填写错误',
        confirmLabel: '确认驳回'
      })
    : '审核通过'
  if (action === 'REJECT' && !reason) return
  await run(async () => {
    await post(`/api/attendance/${id}/review`, { part, action, reason })
    emit('notify', { message: '审核已处理', type: 'success' })
    await loadPending()
    emit('updated')
  })
}

async function bulkReview(part) {
  if (!pendingRecords.value.length) {
    emit('notify', { message: '当前没有待审核记录', type: 'warn' })
    return
  }
  const label = part === 'CHECK_IN' ? '签到' : part === 'CHECK_OUT' ? '签退' : '签到和签退'
  const confirmed = await requestConfirmation({
    title: '确认操作',
    message: `确认将当前列表中可处理的${label}全部通过？`,
    confirmLabel: '确认',
    requiredText: '确认'
  })
  if (!confirmed) return
  await run(async () => {
    const result = await post('/api/attendance/reviews/bulk', {
      ids: pendingRecords.value.map(item => item.id),
      part
    })
    emit('notify', {
      message: `批量审核完成：通过 ${result.reviewed} 项，跳过 ${result.skipped} 条`,
      type: result.errors?.length ? 'warn' : 'success'
    })
    await loadPending()
    emit('updated')
  })
}

async function run(action, showError = true) {
  pendingOperations.value += 1
  try {
    await action()
  } catch (error) {
    if (showError) emit('notify', { message: error.message, type: 'error' })
  } finally {
    pendingOperations.value = Math.max(0, pendingOperations.value - 1)
  }
}

function statusText(status) {
  return {
    NOT_SUBMITTED: '未提交',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    AUTO_APPROVED: '自动通过'
  }[status] || status
}
</script>
