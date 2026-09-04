<template>
  <div class="page-stack profile-page">
    <PageHeader
      title="个人资料"
    >
      <template #actions>
        <button class="button secondary" @click="passwordOpen = true">
          <KeyRound />修改密码
        </button>
      </template>
    </PageHeader>
    <div v-if="pageError" class="inline-alert danger" role="alert">
      <span>{{ pageError }}</span>
      <button class="button secondary small" type="button" @click="retryFailedLoad">重试</button>
    </div>

    <div class="profile-summary">
      <span class="avatar profile-avatar">{{ user?.name?.slice(0, 1) }}</span>
      <div>
        <h2>{{ user?.name }}</h2>
        <p>{{ user?.studentNo }} · {{ roleLabel(user?.role) }}</p>
      </div>
      <div class="profile-stat">
        <strong>{{ number(attendanceHours) }}</strong>
        <span>值班小时</span>
      </div>
      <div class="profile-stat">
        <strong>{{ number(trainingHours) }}</strong>
        <span>培训小时</span>
      </div>
      <div class="profile-stat">
        <strong>{{ number(totalHours) }}</strong>
        <span>合计小时</span>
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
            <input :value="profile.grade || '未设置'" name="grade" readonly />
          </label>
          <div class="form-actions">
            <button class="button primary" type="submit" :disabled="busy">
              <Save />保存资料
            </button>
          </div>
        </form>
      </section>

      <section class="panel profile-record-panel">
        <div class="section-heading profile-record-heading">
          <h2>个人记录</h2>
        </div>
        <div class="profile-record-toolbar">
          <div class="segmented page-tabs" aria-label="记录类型">
            <button
              type="button"
              :class="{ active: activeRecordTab === 'attendance' }"
              @click="activeRecordTab = 'attendance'"
            >
              <CalendarCheck />值班 {{ attendanceRecords.length }}
            </button>
            <button
              type="button"
              :class="{ active: activeRecordTab === 'training' }"
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
            <button class="button secondary small" type="submit">
              <Search />查询
            </button>
          </form>
        </div>
        <p v-if="filterError" class="form-error" role="alert">{{ filterError }}</p>

        <LoadingBlock v-if="recordsLoading && !activeRecords.length" />
        <EmptyState
          v-else-if="!activeRecords.length && !recordsError"
          :title="
            activeRecordTab === 'attendance'
              ? '该时间段暂无值班记录'
              : '该时间段暂无培训记录'
          "
        />
        <div v-else class="profile-record-scroll">
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
                <td>{{ number(record.validHours) }} 小时</td>
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
      </section>
    </div>

    <ProfilePasswordDialog
      :open="passwordOpen"
      @close="passwordOpen = false"
      @changed="passwordChanged"
    />
  </div>
</template>

<script setup lang="ts">
import {
  CalendarCheck,
  GraduationCap,
  KeyRound,
  Save,
  Search,
} from "@lucide/vue";
import PageHeader from "../../shared/ui/PageHeader.vue";
import EmptyState from "../../shared/ui/EmptyState.vue";
import LoadingBlock from "../../shared/ui/LoadingBlock.vue";
import StatusBadge from "../../shared/ui/StatusBadge.vue";
import ProfilePasswordDialog from "../../features/profile/ProfilePasswordDialog.vue";
import { useProfileWorkspace } from "../../features/profile/useProfileWorkspace";

const {
  activeRecordTab, activeRecords, attendanceHours, attendanceNote, attendanceRecords,
  attendanceStatusMeta, busy, captureProfileForm, clock, filterError, from, loadRecords,
  number, pageError, passwordChanged, passwordOpen, profile, profileErrors, recordsError,
  recordsLoading, retryFailedLoad, roleLabel, save, shortClock, sourceLabel, to, totalHours,
  trainingHours, trainingRecords, user,
} = useProfileWorkspace();
</script>
