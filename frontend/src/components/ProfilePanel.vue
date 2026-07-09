<template>
  <section class="work-section tab-profile">
    <div class="section-head"><h3>个人中心</h3></div>
    <div class="profile-grid">
      <div class="profile-card">
        <div class="subsection-head">
          <h4>个人资料</h4>
        </div>
        <form class="profile-form" @submit.prevent="$emit('save-profile')">
          <label>手机号</label>
          <input v-model.trim="profile.phone" />
          <label>学院</label>
          <input v-model.trim="profile.major" />
          <label>年级</label>
          <select v-model="profile.grade">
            <option value="">未填写</option>
            <option v-for="grade in gradeOptions" :key="grade" :value="grade">{{ grade }}</option>
          </select>
          <label>QQ</label>
          <input v-model.trim="profile.qq" />
          <button class="primary-action" type="submit"><Save :size="18" /><span>保存资料</span></button>
        </form>

        <div class="profile-divider"></div>
        <div class="subsection-head">
          <h4>修改密码</h4>
        </div>
        <form class="profile-form password-form" @submit.prevent="$emit('change-password')">
          <label>原密码</label>
          <input v-model="passwordForm.oldPassword" type="password" autocomplete="current-password" />
          <label>新密码</label>
          <input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" />
          <label>确认新密码</label>
          <input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" />
          <button class="primary-action" type="submit"><Save :size="18" /><span>修改密码</span></button>
        </form>
      </div>

      <div class="records-card">
        <div class="subsection-head">
          <h4>我的值班记录</h4>
          <button class="ghost-button" @click="$emit('load-my-records')"><RefreshCw :size="16" />刷新</button>
        </div>
        <div class="filters">
          <input v-model="myRecordRange.from" type="date" />
          <input v-model="myRecordRange.to" type="date" />
          <button class="ghost-button" @click="$emit('load-my-records')">查询</button>
        </div>
        <div class="mini-summary">
          <div><span>记录数</span><strong>{{ myRecordCount }}</strong></div>
          <div><span>有效时长</span><strong>{{ formatHours(myRecordHours) }} h</strong></div>
        </div>
        <div class="table-wrap compact-table">
          <table>
            <thead>
              <tr><th>日期</th><th>签到</th><th>签退</th><th>审核</th><th>状态</th><th>有效时长</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in myRecords" :key="item.id">
                <td>{{ item.dutyDate }}</td>
                <td>{{ timeText(item.checkInTime) }}</td>
                <td>{{ timeText(item.checkOutTime) }}</td>
                <td>{{ statusText(item.checkInStatus) }} / {{ statusText(item.checkOutStatus) }}</td>
                <td><span class="status-badge" :class="item.effectiveStatus?.toLowerCase()">{{ effectiveStatusText(item.effectiveStatus) }}</span></td>
                <td>{{ formatHours(item.validHours) }} h</td>
              </tr>
              <tr v-if="myRecords.length === 0"><td colspan="6" class="empty">暂无值班记录</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { RefreshCw, Save } from '@lucide/vue'

defineProps({
  profile: { type: Object, required: true },
  passwordForm: { type: Object, required: true },
  myRecordRange: { type: Object, required: true },
  myRecords: { type: Array, default: () => [] },
  myRecordCount: { type: Number, default: 0 },
  myRecordHours: { type: Number, default: 0 },
  gradeOptions: { type: Array, default: () => [] }
})

defineEmits(['save-profile', 'change-password', 'load-my-records'])

function statusText(status) {
  return {
    NOT_SUBMITTED: '未提交',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    AUTO_APPROVED: '自动通过'
  }[status] || status
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
