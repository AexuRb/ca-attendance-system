<template>
  <aside class="today-roster" aria-labelledby="today-roster-title">
    <header class="today-roster-heading">
      <h2 id="today-roster-title">当前在场</h2>
      <span v-if="roster && !failed" :key="roster.items.length" class="today-roster-count">{{ roster.items.length }} 人</span>
    </header>
    <div v-if="failed" class="today-roster-state" role="alert">
      <p>暂时无法读取在场名单。</p>
      <button class="button secondary small" data-button-kind="soft" type="button" @click="$emit('retry')">重试</button>
    </div>
    <p v-else-if="loading && !roster" class="today-roster-state" role="status">正在读取在场名单…</p>
    <p v-else-if="!roster?.items.length" class="today-roster-state">当前暂无在场人员</p>
    <div v-else class="today-roster-scroll" tabindex="0" role="region" aria-label="在场人员与考核状态">
      <table class="today-roster-table">
        <thead>
          <tr>
            <th scope="col">成员</th>
            <th v-for="field in memberAssessments" :key="field.key" scope="col">{{ field.shortLabel }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in roster.items" :key="member.id">
            <th scope="row">{{ member.name }}</th>
            <td v-for="field in memberAssessments" :key="field.key">
              <span class="mw-badge" :data-tone="member[field.key] === 'PASSED' ? 'success' : 'neutral'">
                {{ member[field.key] === 'PASSED' ? '通过' : '未通过' }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { memberAssessments } from "../../../features/members/memberDirectory";
import type { TodayRoster } from "./types";

defineProps<{ roster: TodayRoster | null; loading: boolean; failed: boolean }>();
defineEmits<{ retry: [] }>();
</script>
