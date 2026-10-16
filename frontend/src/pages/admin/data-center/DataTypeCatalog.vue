<template>
  <div class="data-type-catalog">
    <header class="import-catalog-lead"><h2>选择数据类型</h2></header>
    <div class="import-catalog-list">
      <section v-for="item in items" :key="item.id" class="import-task data-type-card" :aria-labelledby="`data-type-${mode}-${item.id}`">
        <header class="import-task-summary">
          <div class="import-kind-identity"><span class="import-kind-icon"><component :is="icons[item.id] || FileSpreadsheet" aria-hidden="true" /></span><div><h3 :id="`data-type-${mode}-${item.id}`">{{ item.label }}</h3><p>{{ item.description }}</p></div></div>
          <div class="import-task-actions"><slot name="actions" :item="item" /><button class="button secondary" data-button-kind="soft" type="button" :data-source="item.id" :disabled="busy" @click="$emit('choose', item.id)">开始{{ mode === 'import' ? '导入' : '导出' }}</button></div>
        </header>
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">
import type { Component } from "vue";
import { UsersRound, CalendarDays, GraduationCap, ClipboardList, Wrench, History, FileSpreadsheet } from "@lucide/vue";
export interface DataTypeChoice { id: string; label: string; description: string }
defineProps<{ mode: 'import' | 'export'; items: DataTypeChoice[]; busy?: boolean }>();
defineEmits<{ choose: [id: string] }>();
const icons: Record<string, Component> = { members: UsersRound, schedules: CalendarDays, schedule: CalendarDays, training: GraduationCap, attendance: ClipboardList, repairs: Wrench, logs: History };
</script>
