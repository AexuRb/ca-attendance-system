<template>
  <div class="mw-table-scroll" tabindex="0" aria-label="成员记录，可横向滚动">
    <table class="mw-table" :class="{ 'mw-directory': editorial }">
      <thead>
        <tr v-if="spatial" class="mw-column-groups"><th colspan="2" scope="colgroup">成员身份</th><th colspan="2" scope="colgroup">资料</th><th colspan="3" scope="colgroup">账号与操作</th></tr>
        <tr>
          <th class="mw-selection"><input name="memberPageSelection" type="checkbox" aria-label="选择本页可管理成员" :checked="allSelected" :disabled="!selectableIds.length" @change="$emit('togglePage', $event)"></th>
          <th scope="col">{{ editorial ? '成员身份与账号' : '成员' }}</th>
          <template v-if="!editorial"><th scope="col">联系方式</th><th scope="col">学院 / 年级</th><th scope="col">角色</th><th scope="col">状态</th></template>
          <th v-else scope="col">学籍与联系</th>
          <th scope="col" class="mw-actions-column">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in members" :key="item.id" :class="{ 'is-selected': selected.has(item.id) }">
          <td class="mw-selection"><input :name="`memberSelection-${item.id}`" type="checkbox" :aria-label="`选择 ${item.name}`" :checked="selected.has(item.id)" :disabled="!selectableIds.includes(item.id)" @change="$emit('toggleMember',item.id)"></td>
          <td><div class="mw-identity"><span class="mw-avatar">{{ item.name.slice(0,1) }}</span><div><strong>{{ item.name }}</strong><small>{{ item.studentNo }}</small></div><div v-if="editorial" class="mw-account"><span>{{ roleLabel(item.role) }}</span><span class="mw-badge" :data-tone="item.status==='ACTIVE'?'success':'neutral'">{{ item.status==='ACTIVE'?'启用':'停用' }}</span></div></div></td>
          <template v-if="!editorial"><td>{{ item.phone || '—' }}<small v-if="item.qq">QQ {{ item.qq }}</small></td><td>{{ item.major || '—' }}<small>{{ item.grade || '未填写年级' }}</small></td><td>{{ roleLabel(item.role) }}</td><td><span class="mw-badge" :data-tone="item.status==='ACTIVE'?'success':'neutral'">{{ item.status==='ACTIVE'?'启用':'停用' }}</span></td></template>
          <td v-else><div class="mw-academic">{{ item.major || '—' }} <small>{{ item.grade || '未填写年级' }}</small></div><small>联系方式：{{ item.phone || '—' }}<span v-if="item.qq"> · QQ {{ item.qq }}</span></small></td>
          <td class="mw-actions-column"><slot name="actions" :member="item" /></td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { useAppearance } from "../../appearance/appearanceStore";
import { roleLabel } from "../../app/adminNavigation";
import type { MemberSummary } from "./memberDirectory";
const { state } = useAppearance();
const editorial = computed(()=>state.active==='EDITORIAL');
const spatial = computed(()=>state.active==='SPATIAL');
defineProps<{ members: MemberSummary[]; selected: Set<number>; selectableIds: number[]; allSelected: boolean }>();
defineEmits<{ togglePage: [event: Event]; toggleMember: [id:number] }>();
</script>
