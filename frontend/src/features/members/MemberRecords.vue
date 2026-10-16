<template>
  <div :ref="bindScroll" class="mw-table-scroll" tabindex="0" aria-label="成员记录，可横向滚动" @scroll.passive="$emit('tableScroll')">
    <table class="mw-table">
      <thead>
        <tr>
          <th class="mw-selection">
            <label class="mw-check-control mw-select-all" :class="{ busy: selectionBusy }">
              <input name="memberPageSelection" type="checkbox" :aria-label="allSelected ? '清除当前筛选结果中的全部选择' : '选择当前筛选结果中的全部可管理成员'" :checked="allSelected" :disabled="selectionBusy" @change="$emit('toggleAll', $event)">
              <span class="mw-checkmark" aria-hidden="true"></span><span>全选</span>
            </label>
          </th>
          <th scope="col" class="mw-person-column">姓名 / 学号</th>
          <th scope="col" class="mw-role-column">角色</th>
          <th scope="col" class="mw-academic-column">学院 / 年级</th>
          <th scope="col" class="mw-contact-column">联系方式</th>
          <th scope="col" class="mw-status-column">状态</th>

          <th scope="col" class="mw-actions-column">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in members" :key="item.id" :class="{ 'is-selected': selected.has(item.id) }">
          <td class="mw-selection">
            <label class="mw-check-control mw-row-check">
              <input :name="`memberSelection-${item.id}`" type="checkbox" :aria-label="`选择 ${item.name}`" :checked="selected.has(item.id)" :disabled="selectionBusy || !selectableIds.includes(item.id)" @change="$emit('toggleMember',item.id)">
              <span class="mw-checkmark" aria-hidden="true"></span>
            </label>
          </td>
          <td class="mw-person-column"><div class="mw-identity"><span class="mw-avatar">{{ item.name.slice(0,1) }}</span><div><strong>{{ item.name }}</strong><small>{{ item.studentNo }}</small></div></div></td>
          <td class="mw-role-column">{{ roleLabel(item.role) }}</td>
          <td class="mw-academic-column">{{ item.major || '—' }}<small>{{ item.grade || '未填写年级' }}</small></td>
          <td class="mw-contact-column">{{ item.phone || '—' }}<small v-if="item.qq">QQ {{ item.qq }}</small></td>
          <td class="mw-status-column"><span class="mw-badge" :data-tone="item.status==='ACTIVE'?'success':'neutral'">{{ item.status==='ACTIVE'?'启用':'停用' }}</span></td>
          <td class="mw-actions-column"><slot name="actions" :member="item" /></td>
        </tr>
      </tbody>
    </table>
  </div>
  <div class="mw-mobile-records" aria-label="成员记录">
    <div class="mw-mobile-select-all">
      <label class="mw-check-control mw-select-all" :class="{ busy: selectionBusy }">
        <input name="memberMobilePageSelection" type="checkbox" :aria-label="allSelected ? '清除当前筛选结果中的全部选择' : '选择当前筛选结果中的全部可管理成员'" :checked="allSelected" :disabled="selectionBusy" @change="$emit('toggleAll', $event)">
        <span class="mw-checkmark" aria-hidden="true"></span><span>全选</span>
      </label>
      <span>筛选结果</span>
    </div>
    <div class="mw-mobile-list">
      <article v-for="item in members" :key="item.id" class="mw-mobile-record" :class="{ 'is-selected': selected.has(item.id) }" :aria-label="`${item.name}的成员记录`">
        <div class="mw-mobile-record-head">
          <label class="mw-check-control mw-row-check">
            <input :name="`memberMobileSelection-${item.id}`" type="checkbox" :aria-label="`选择 ${item.name}`" :checked="selected.has(item.id)" :disabled="selectionBusy || !selectableIds.includes(item.id)" @change="$emit('toggleMember',item.id)">
            <span class="mw-checkmark" aria-hidden="true"></span>
          </label>
          <span class="mw-avatar" aria-hidden="true">{{ item.name.slice(0,1) }}</span>
          <div class="mw-mobile-person"><strong>{{ item.name }}</strong><small>{{ item.studentNo }}</small></div>
          <span class="mw-badge" :data-tone="item.status==='ACTIVE'?'success':'neutral'">{{ item.status==='ACTIVE'?'启用':'停用' }}</span>
        </div>
        <div class="mw-mobile-record-body">
          <div class="mw-mobile-detail"><small>角色</small><strong>{{ roleLabel(item.role) }}</strong></div>
          <div class="mw-mobile-detail mw-mobile-academic"><small>学院 / 年级</small><span>{{ item.major || '未填写学院' }}<em>{{ item.grade || '未填写年级' }}</em></span></div>
          <div class="mw-mobile-detail mw-mobile-contact"><small>联系</small><span>{{ item.phone || '未填写手机号' }}<em v-if="item.qq">QQ {{ item.qq }}</em></span></div>
        </div>
        <div class="mw-mobile-record-foot"><span>账号操作</span><slot name="actions" :member="item" /></div>
      </article>
    </div>
  </div>
</template>
<script setup lang="ts">
import type { ComponentPublicInstance } from "vue";
import { roleLabel } from "../../app/adminNavigation";
import type { MemberSummary } from "./memberDirectory";
defineProps<{ members: MemberSummary[]; selected: Set<number>; selectableIds: number[]; allSelected: boolean; selectionBusy?: boolean; bindScroll?: (element: Element | ComponentPublicInstance | null) => void }>();
defineEmits<{ toggleAll: [event: Event]; toggleMember: [id:number]; tableScroll: [] }>();
</script>
