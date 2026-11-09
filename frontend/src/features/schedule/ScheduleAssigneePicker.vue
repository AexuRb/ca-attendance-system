<template>
  <div class="schedule-assignee-picker member-picker" @keydown.esc="clearOnEscape">
    <div v-if="modelValue.length" class="schedule-assignee-selected member-picker-selected" aria-label="已选排班人员">
      <span class="schedule-assignee-selected__label">已选 {{ modelValue.length }} 人</span>
      <div class="member-picker-tags"><button v-for="person in shownSelected" :key="person.studentNo" type="button" class="member-picker-tag" :title="`移除 ${person.name}`" :aria-label="`移除 ${person.name}`" :disabled="disabled" @click="remove(person.studentNo)"><span>{{ person.name }}</span><small>{{ person.studentNo }}</small><X aria-hidden="true" /></button><button v-if="modelValue.length > 3" class="button secondary" data-button-kind="compact" type="button" @click="showAll = !showAll">{{ showAll ? '收起已选' : `另有 ${modelValue.length - 3} 人` }}</button></div>
    </div>
    <div class="schedule-assignee-search member-picker-search"><Search aria-hidden="true" /><input ref="searchInput" v-model.trim="keyword" name="scheduleAssigneeSearch" aria-label="搜索排班人员" :aria-controls="listId" aria-expanded="true" role="combobox" aria-autocomplete="list" autocomplete="off" placeholder="搜索姓名或学号" :disabled="disabled" @keydown.down.prevent="focusEdge(false)" @keydown.up.prevent="focusEdge(true)" /><button v-if="keyword" class="icon-button" type="button" aria-label="清空人员搜索" @click="clearSearch"><X aria-hidden="true" /></button></div>
    <div class="member-picker-meta"><span>共 {{ visibleCandidates.length }} 人</span><span>已选 {{ modelValue.length }} 人</span></div>
    <div :id="listId" ref="listbox" class="schedule-assignee-options member-picker-options" role="listbox" aria-label="可选排班人员" aria-multiselectable="true">
      <button v-for="person in pagedCandidates" :key="person.studentNo" type="button" role="option" :data-option-id="person.studentNo" :aria-selected="selectedNumbers.has(person.studentNo)" :tabindex="optionTabIndex(person)" :class="{ selected: selectedNumbers.has(person.studentNo) }" :disabled="disabled" @keydown="onOptionKeydown($event, person)" @click="toggle(person)"><span class="schedule-assignee-avatar member-picker-avatar">{{ person.name.slice(0, 1) }}</span><span class="member-picker-identity"><strong>{{ person.name }}</strong><small>{{ person.studentNo }} · {{ roleLabel(person.role) }}</small></span><span class="member-picker-mark square"><Check v-if="selectedNumbers.has(person.studentNo)" aria-hidden="true" /></span></button>
      <div v-if="!visibleCandidates.length" class="member-picker-state" role="status"><Search aria-hidden="true" /><strong>{{ keyword ? '没有匹配的排班人员' : '暂无可选排班人员' }}</strong><button v-if="keyword" class="button secondary" data-button-kind="compact" type="button" @click="clearSearch">清除搜索</button></div>
    </div>
    <div v-if="visibleCandidates.length" class="member-picker-pagination"><span>每页 {{ pageSize }} 人</span><div><button class="icon-button" type="button" aria-label="上一页排班人员" :disabled="disabled || page <= 1" @click="page--"><ChevronLeft aria-hidden="true" /></button><span>{{ page }} / {{ pageCount }}</span><button class="icon-button" type="button" aria-label="下一页排班人员" :disabled="disabled || page >= pageCount" @click="page++"><ChevronRight aria-hidden="true" /></button></div></div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from "vue";
import { Check, Search, X, ChevronLeft, ChevronRight } from "@lucide/vue";
import { filterScheduleAssignees, mergeScheduleAssignees, roleLabel, type ScheduleAssigneeOption } from "./scheduleAssignees";
const props = defineProps<{ candidates: ScheduleAssigneeOption[]; modelValue: ScheduleAssigneeOption[]; open?: boolean; disabled?: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: ScheduleAssigneeOption[]] }>();
const keyword = ref(""), page = ref(1), showAll = ref(false), activeStudentNo = ref<string | null>(null);
const listbox = ref<HTMLElement | null>(null), searchInput = ref<HTMLInputElement | null>(null), listId = useId(), pageSize = 6;
let suppressModelReset = false;
const allCandidates = computed(() => mergeScheduleAssignees(props.candidates, props.modelValue));
const visibleCandidates = computed(() => filterScheduleAssignees(allCandidates.value, keyword.value));
const pageCount = computed(() => Math.max(1, Math.ceil(visibleCandidates.value.length / pageSize)));
const pagedCandidates = computed(() => visibleCandidates.value.slice((page.value - 1) * pageSize, page.value * pageSize));
const selectedNumbers = computed(() => new Set(props.modelValue.map(p => p.studentNo)));
const shownSelected = computed(() => showAll.value ? props.modelValue : props.modelValue.slice(0, 3));
watch(keyword, () => { page.value = 1; });
watch(pageCount, n => { page.value = Math.min(page.value, n); });
watch(pagedCandidates, list => { if (!list.some(p => p.studentNo === activeStudentNo.value)) activeStudentNo.value = list[0]?.studentNo ?? null; }, { immediate: true });
function resetPicker() { keyword.value = ""; page.value = 1; activeStudentNo.value = null; showAll.value = false; }
watch(() => props.open, open => { if (open) resetPicker(); });
watch(() => props.modelValue, () => { if (suppressModelReset) { suppressModelReset = false; return; } resetPicker(); });
function clearSearch() { keyword.value = ""; void nextTick(() => searchInput.value?.focus()); }
function clearOnEscape(event: KeyboardEvent) { if (keyword.value) { event.preventDefault(); event.stopPropagation(); clearSearch(); } }
function optionTabIndex(p: ScheduleAssigneeOption) { return (activeStudentNo.value ?? pagedCandidates.value[0]?.studentNo) === p.studentNo ? 0 : -1; }
function focusOption(p: ScheduleAssigneeOption) { activeStudentNo.value = p.studentNo; void nextTick(() => { Array.from(listbox.value?.querySelectorAll<HTMLButtonElement>('[data-option-id]') || []).find(e => e.dataset.optionId === p.studentNo)?.focus(); }); }
function focusEdge(last: boolean) { const p = last ? pagedCandidates.value.at(-1) : pagedCandidates.value[0]; if (p) focusOption(p); }
function onOptionKeydown(event: KeyboardEvent, p: ScheduleAssigneeOption) {
  if (["Enter", " ", "Spacebar"].includes(event.key)) { event.preventDefault(); toggle(p); return; }
  if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;
  const list = pagedCandidates.value, index = list.findIndex(x => x.studentNo === p.studentNo); if (index < 0 || !list.length) return; event.preventDefault();
  const i = event.key === "Home" ? 0 : event.key === "End" ? list.length - 1 : (index + (event.key === "ArrowDown" ? 1 : -1) + list.length) % list.length;
  focusOption(list[i]!);
}
function toggle(p: ScheduleAssigneeOption) {
  if (props.disabled) return; if (selectedNumbers.value.has(p.studentNo)) { remove(p.studentNo); return; }
  activeStudentNo.value = p.studentNo; suppressModelReset = true; emit("update:modelValue", [...props.modelValue, p]);
  void nextTick(() => { suppressModelReset = false; });
}
function remove(studentNo: string) {
  if (props.disabled) return; suppressModelReset = true; emit("update:modelValue", props.modelValue.filter(p => p.studentNo !== studentNo));
  void nextTick(() => { suppressModelReset = false; });
}
</script>
