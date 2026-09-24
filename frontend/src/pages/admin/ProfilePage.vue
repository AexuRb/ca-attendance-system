<template>
  <RefinedWorkspaceShell class="support-workspace profile-presentation" title="个人资料" description="查看个人信息与时长记录" section-key="people" filter-label="筛选个人资料">
    <div v-if="pageError" class="inline-alert danger" role="alert">
      <span>{{ pageError }}</span>
      <button class="button secondary small" type="button" @click="retryFailedLoad">重试</button>
    </div>

    <div class="profile-summary">
      <div class="profile-summary-main">
        <div class="profile-identity">
          <span class="avatar profile-avatar">{{ user?.name?.slice(0, 1) }}</span>
          <div>
            <h2>{{ user?.name }}</h2>
            <p>{{ user?.studentNo }} · {{ roleLabel(user?.role) }}</p>
          </div>
        </div>
        <button class="button secondary profile-password-action" type="button" @click="passwordOpen = true">
          <KeyRound />修改密码
        </button>
      </div>
      <p class="profile-range-note" aria-live="polite">
        {{ appliedRange ? `统计范围 ${appliedRange.from} 至 ${appliedRange.to}` : recordsError ? '时长记录读取失败' : '正在读取时长记录' }}<span v-if="recordsLoading && recordsReady"> · 更新中</span><span v-else-if="recordsError && recordsReady"> · 更新失败，显示上次结果</span>
      </p>
      <div class="profile-totals">
        <div class="profile-stat">
          <strong>{{ recordsReady ? number(attendanceHours) : '—' }}</strong>
          <span>值班小时</span>
        </div>
        <div class="profile-stat">
          <strong>{{ recordsReady ? number(trainingHours) : '—' }}</strong>
          <span>培训小时</span>
        </div>
        <div class="profile-stat profile-stat-total">
          <strong>{{ recordsReady ? number(totalHours) : '—' }}</strong>
          <span>合计小时</span>
        </div>
      </div>
    </div>

    <div class="profile-workspace">
      <section class="panel profile-contact-panel">
        <div class="section-heading">
          <div>
            <h2>联系信息</h2>
          </div>
        </div>
        <form :ref="captureProfileForm" class="form-grid" novalidate @submit.prevent="save">
          <label class="field">
            <span>手机</span>
            <input
              v-model.trim="profile.phone"
              name="phone"
              autocomplete="tel"
              maxlength="64"
              :aria-invalid="Boolean(profileErrors.phone)"
            />
            <small v-if="profileErrors.phone" class="field-error" role="alert">{{ profileErrors.phone }}</small>
          </label>
          <label class="field">
            <span>QQ</span>
            <input v-model.trim="profile.qq" name="qq" inputmode="numeric" maxlength="32" :aria-invalid="Boolean(profileErrors.qq)" />
            <small v-if="profileErrors.qq" class="field-error" role="alert">{{ profileErrors.qq }}</small>
          </label>
          <label class="field">
            <span>学院</span>
            <input v-model.trim="profile.major" name="college" maxlength="128" :aria-invalid="Boolean(profileErrors.college)" />
            <small v-if="profileErrors.college" class="field-error" role="alert">{{ profileErrors.college }}</small>
          </label>
          <label class="field">
            <span>年级</span>
            <input :value="profile.grade || '未设置'" name="grade" readonly aria-describedby="profile-grade-note" />
            <small id="profile-grade-note" class="profile-readonly-note">年级为只读信息</small>
          </label>
          <div class="form-actions">
            <button class="button primary" type="submit" :disabled="busy">
              <Save />{{ busy ? '保存中…' : '保存资料' }}
            </button>
          </div>
        </form>
      </section>

      <section class="panel profile-record-panel">
        <div class="section-heading profile-record-heading">
          <h2>个人记录</h2>
        </div>
        <div class="profile-record-toolbar">
          <div class="segmented page-tabs" role="group" aria-label="记录类型">
            <button
              type="button"
              :class="{ active: activeRecordTab === 'attendance' }"
              :aria-pressed="activeRecordTab === 'attendance'"
              @click="activeRecordTab = 'attendance'"
            >
              <CalendarCheck />值班 {{ attendanceRecords.length }}
            </button>
            <button
              type="button"
              :class="{ active: activeRecordTab === 'training' }"
              :aria-pressed="activeRecordTab === 'training'"
              @click="activeRecordTab = 'training'"
            >
              <GraduationCap />培训 {{ trainingRecords.length }}
            </button>
          </div>
          <form class="profile-record-filter" @submit.prevent="loadRecords">
            <label>
              <span>开始日期</span>
              <input v-model="from" name="profileRecordFrom" type="date" required />
            </label>
            <label>
              <span>结束日期</span>
              <input v-model="to" name="profileRecordTo" type="date" required />
            </label>
            <button class="button secondary small" type="submit" :disabled="recordsLoading || Boolean(filterError)">
              <Search />{{ recordsLoading ? '查询中…' : '查询' }}
            </button>
          </form>
        </div>
        <p v-if="filterError" class="form-error" role="alert">{{ filterError }}</p>
        <p v-if="rangeChanged || (recordsLoading && recordsReady)" class="profile-record-context" aria-live="polite">
          {{ rangeChanged ? '日期已修改，查询后生效' : '正在更新记录' }}
        </p>
        <div v-if="recordsError" class="inline-alert danger profile-record-error" role="alert">
          <span>{{ recordsError }}{{ recordsReady ? '；下方保留上次结果。' : '' }}</span>
          <button class="button secondary small" type="button" @click="loadRecords">重试</button>
        </div>

        <LoadingBlock v-if="recordsLoading && !recordsReady" />
        <EmptyState
          v-else-if="!activeRecords.length && !recordsError"
          :title="
            activeRecordTab === 'attendance'
              ? '该时间段暂无值班记录'
              : '该时间段暂无培训记录'
          "
        />
        <Transition name="profile-record-swap" mode="out-in">
        <div v-if="activeRecords.length && !(recordsLoading && !recordsReady)" :key="activeRecordTab" class="profile-record-scroll">
          <table v-if="activeRecordTab === 'attendance'">
            <thead>
              <tr>
                <th>日期</th>
                <th>签到 / 签退</th>
                <th>原始时长</th>
                <th>有效时长</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="record in attendanceRecords" :key="record.id">
                <td>
                  <strong>{{ record.dutyDate }}</strong>
                  <small>{{ sourceLabel(record.source) }}</small>
                </td>
                <td>
                  {{ clock(record.checkInTime) }}–{{ clock(record.checkOutTime) }}
                </td>
                <td>{{ record.durationMinutes || 0 }} 分钟</td>
                <td><strong class="profile-valid-hours">{{ number(record.validHours) }}</strong> 小时</td>
                <td>
                  <StatusBadge
                    :label="
                      attendanceStatusMeta(record.effectiveStatus).label
                    "
                    :tone="attendanceStatusMeta(record.effectiveStatus).tone"
                  />
                  <small v-if="attendanceNote(record)">
                    {{ attendanceNote(record) }}
                  </small>
                </td>
              </tr>
            </tbody>
          </table>

          <table v-else>
            <thead>
              <tr>
                <th>培训</th>
                <th>日期 / 时间</th>
                <th>地点 / 主讲</th>
                <th>时长</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="record in trainingRecords"
                :key="record.participantId"
              >
                <td>
                  <strong>{{ record.title }}</strong>
                  <small v-if="record.remark">{{ record.remark }}</small>
                </td>
                <td>
                  {{ record.trainingDate }}
                  <small>
                    {{ shortClock(record.startTime) }}–{{
                      shortClock(record.endTime)
                    }}
                  </small>
                </td>
                <td>
                  {{ record.location || "—" }}
                  <small>{{ record.speaker || "未填写主讲人" }}</small>
                </td>
                <td>{{ number(record.durationHours) }} 小时</td>
              </tr>
            </tbody>
          </table>
        </div>
        </Transition>
        <Transition name="profile-record-swap" mode="out-in">
        <ul
          v-if="activeRecords.length"
          :key="activeRecordTab"
          class="profile-mobile-records"
          :aria-label="activeRecordTab === 'attendance' ? '值班记录' : '培训记录'"
        >
          <template v-if="activeRecordTab === 'attendance'">
            <li v-for="record in attendanceRecords" :key="record.id" class="profile-mobile-record">
              <div class="profile-mobile-record-head">
                <strong>{{ record.dutyDate }}</strong>
                <StatusBadge :label="attendanceStatusMeta(record.effectiveStatus).label" :tone="attendanceStatusMeta(record.effectiveStatus).tone" />
              </div>
              <p>{{ clock(record.checkInTime) }}–{{ clock(record.checkOutTime) }} <span>· {{ sourceLabel(record.source) }}</span></p>
              <div class="profile-mobile-record-facts">
                <span>有效 <strong>{{ number(record.validHours) }} 小时</strong></span>
                <span>原始 {{ record.durationMinutes || 0 }} 分钟</span>
              </div>
              <p v-if="attendanceNote(record)" class="profile-mobile-record-note">{{ attendanceNote(record) }}</p>
            </li>
          </template>
          <template v-else>
            <li v-for="record in trainingRecords" :key="record.participantId" class="profile-mobile-record">
              <div class="profile-mobile-record-head">
                <strong>{{ record.title }}</strong>
                <span class="profile-mobile-record-hours">{{ number(record.durationHours) }} 小时</span>
              </div>
              <p>{{ record.trainingDate }} · {{ shortClock(record.startTime) }}–{{ shortClock(record.endTime) }}</p>
              <div class="profile-mobile-record-facts">
                <span>{{ record.location || '地点未填写' }}</span>
                <span>{{ record.speaker || '主讲人未填写' }}</span>
              </div>
              <p v-if="record.remark" class="profile-mobile-record-note">{{ record.remark }}</p>
            </li>
          </template>
        </ul>
        </Transition>
      </section>
    </div>

    <ProfilePasswordDialog
      :open="passwordOpen"
      @close="passwordOpen = false"
      @changed="passwordChanged"
    />
  </RefinedWorkspaceShell>
</template>

<script setup lang="ts">
import {
  CalendarCheck,
  GraduationCap,
  KeyRound,
  Save,
  Search,
} from "@lucide/vue";
import RefinedWorkspaceShell from "../../layouts/RefinedWorkspaceShell.vue";
import { provide } from "vue";
import { memberPresentationKey } from "../../shared/ui/presentation";
import "../../features/members/presentation.css";
import "../../features/workspaces/presentation.css";
import "../../features/profile/presentation.css";
provide(memberPresentationKey, true);
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ProfilePasswordDialog from "../../features/profile/ProfilePasswordDialog.vue";
import { useProfileWorkspace } from "../../features/profile/useProfileWorkspace";

const {
  activeRecordTab, activeRecords, appliedRange, attendanceHours, attendanceNote, attendanceRecords,
  attendanceStatusMeta, busy, captureProfileForm, clock, filterError, from, loadRecords,
  number, pageError, passwordChanged, passwordOpen, profile, profileErrors, rangeChanged, recordsError,
  recordsLoading, recordsReady, retryFailedLoad, roleLabel, save, shortClock, sourceLabel, to, totalHours,
  trainingHours, trainingRecords, user,
} = useProfileWorkspace();
</script>
