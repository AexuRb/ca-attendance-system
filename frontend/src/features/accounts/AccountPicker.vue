<template>
  <div class="account-picker member-picker" @keydown.esc="closeOptions">
    <div v-if="modelValue" class="account-picker-current member-picker-current">
      <span class="member-picker-avatar">{{ modelValue.name.slice(0, 1) }}</span>
      <div class="member-picker-identity"><strong>{{ modelValue.name }}</strong><small>{{ modelValue.studentNo || "原账号不可用" }} · {{ accountRoleLabel(modelValue.role) }}</small><em v-if="modelValue.inactive">原账号不可用</em></div>
      <button v-if="compactSelection" ref="changeButton" class="button secondary" data-button-kind="compact" type="button" :disabled="disabled" @click="openOptions">更换</button>
      <button class="icon-button" type="button" title="清除选择" aria-label="清除选择" :disabled="disabled" @click="select(null)"><X aria-hidden="true" /></button>
    </div>
    <div v-if="!compactSelection || optionsOpen || !modelValue" class="member-picker-expanded">
      <div class="account-picker-search member-picker-search">
        <Search aria-hidden="true" />
        <input ref="searchInput" v-model.trim="keyword" :name="inputName" :aria-label="ariaLabel" :aria-invalid="invalid || undefined" :aria-describedby="describedBy" :aria-expanded="true" :aria-controls="listId" role="combobox" aria-autocomplete="list" :placeholder="placeholder" autocomplete="off" :disabled="disabled" @focus="optionsOpen = true" @input="optionsOpen = true" @keydown.down.prevent="focusFirst(false)" @keydown.up.prevent="focusFirst(true)" />
        <button v-if="keyword" class="icon-button" type="button" aria-label="清空搜索" :disabled="disabled" @click="clearSearch"><X aria-hidden="true" /></button>
      </div>
      <div class="member-picker-meta"><span>{{ loading ? '正在加载成员' : `共 ${visibleCandidates.length} 人` }}</span><span>姓名 · 学号 · 角色</span></div>
      <div :id="listId" ref="listbox" class="account-picker-options member-picker-options" role="listbox" :aria-label="`${ariaLabel}候选列表`" :aria-busy="loading || undefined">
        <div v-if="loading" class="member-picker-state" role="status"><LoaderCircle class="spin" aria-hidden="true" /><strong>正在加载成员…</strong></div>
        <div v-else-if="error" class="member-picker-state" role="alert"><CircleAlert aria-hidden="true" /><strong>成员名单加载失败</strong><p>{{ error }}</p><button class="button secondary" data-button-kind="compact" type="button" :disabled="disabled" @click="$emit('retry')">重新加载</button></div>
        <template v-else>
          <button v-for="candidate in pagedCandidates" :key="candidate.id" type="button" role="option" :data-option-id="candidate.id" :aria-selected="candidate.id === modelValue?.id" :tabindex="optionTabIndex(candidate)" :class="{ selected: candidate.id === modelValue?.id, unavailable: candidate.inactive }" :disabled="disabled || candidate.inactive" @keydown="onOptionKeydown($event, candidate)" @click="select(candidate)">
            <span class="member-picker-avatar">{{ candidate.name.slice(0, 1) }}</span><span class="member-picker-identity"><strong>{{ candidate.name }}</strong><small>{{ candidate.studentNo || "原账号不可用" }} · {{ accountRoleLabel(candidate.role) }}</small></span><span class="member-picker-mark"><Check v-if="candidate.id === modelValue?.id" aria-hidden="true" /></span>
          </button>
          <div v-if="!visibleCandidates.length" class="member-picker-state" role="status"><Search aria-hidden="true" /><strong>{{ keyword ? '没有匹配的成员' : '暂无可选成员' }}</strong><p>{{ keyword ? '试试姓名或完整学号。' : '当前范围没有可选的启用账号。' }}</p><button v-if="keyword" class="button secondary" data-button-kind="compact" type="button" @click="clearSearch">清除搜索</button></div>
        </template>
      </div>
      <div v-if="!loading && !error && visibleCandidates.length" class="member-picker-pagination"><span>每页 {{ pageSize }} 人</span><div><button class="icon-button" type="button" aria-label="上一页成员" :disabled="disabled || page <= 1" @click="page--"><ChevronLeft aria-hidden="true" /></button><span>{{ page }} / {{ pageCount }}</span><button class="icon-button" type="button" aria-label="下一页成员" :disabled="disabled || page >= pageCount" @click="page++"><ChevronRight aria-hidden="true" /></button></div></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from "vue";
import { Check, Search, X, ChevronLeft, ChevronRight, CircleAlert, LoaderCircle } from "@lucide/vue";
import { accountRoleLabel, filterAccountCandidates, mergeAccountCandidates, type AccountCandidate } from "./accountCandidates";
const props = withDefaults(defineProps<{
  candidates: AccountCandidate[]; modelValue: AccountCandidate | null; ariaLabel?: string; placeholder?: string;
  inputName?: string; invalid?: boolean; describedBy?: string; open?: boolean; compactSelection?: boolean;
  loading?: boolean; error?: string; disabled?: boolean;
}>(), { ariaLabel: "选择账号", placeholder: "搜索姓名或学号", open: true, compactSelection: false });
const emit = defineEmits<{ "update:modelValue": [value: AccountCandidate | null]; retry: [] }>();
const keyword = ref(""), page = ref(1), activeId = ref<number | null>(null);
const optionsOpen = ref(!props.modelValue), listbox = ref<HTMLElement | null>(null);
const searchInput = ref<HTMLInputElement | null>(null), changeButton = ref<HTMLButtonElement | null>(null);
const listId = useId(), pageSize = 6;
let suppressModelReset = false;
const visibleCandidates = computed(() => {
  const all = filterAccountCandidates(mergeAccountCandidates(props.candidates, props.modelValue), keyword.value);
  return props.compactSelection && props.modelValue ? all.filter(c => c.id !== props.modelValue?.id) : all;
});
const pageCount = computed(() => Math.max(1, Math.ceil(visibleCandidates.value.length / pageSize)));
const pagedCandidates = computed(() => visibleCandidates.value.slice((page.value - 1) * pageSize, page.value * pageSize));
watch(keyword, () => { page.value = 1; });
watch(pageCount, n => { page.value = Math.min(page.value, n); });
watch(pagedCandidates, list => { if (!list.some(c => c.id === activeId.value && !c.inactive)) activeId.value = list.find(c => !c.inactive)?.id ?? null; }, { immediate: true });
function resetPicker() { keyword.value = ""; page.value = 1; activeId.value = null; optionsOpen.value = !props.modelValue; }
watch(() => props.open, open => { if (open) resetPicker(); });
watch(() => props.modelValue, () => { if (suppressModelReset) { suppressModelReset = false; return; } resetPicker(); });
function clearSearch() { keyword.value = ""; optionsOpen.value = true; void nextTick(() => searchInput.value?.focus()); }
function openOptions() { if (props.disabled) return; optionsOpen.value = true; void nextTick(() => searchInput.value?.focus()); }
function closeOptions(event: KeyboardEvent) {
  if (!props.compactSelection || !optionsOpen.value) return;
  if (props.modelValue) { event.preventDefault(); event.stopPropagation(); optionsOpen.value = false; void nextTick(() => changeButton.value?.focus()); }
  else if (keyword.value) { event.preventDefault(); event.stopPropagation(); clearSearch(); }
}
function optionTabIndex(c: AccountCandidate) { return !c.inactive && (activeId.value ?? pagedCandidates.value.find(c => !c.inactive)?.id) === c.id ? 0 : -1; }
function focusOption(c: AccountCandidate) { activeId.value = c.id; void nextTick(() => { listbox.value?.querySelector<HTMLButtonElement>(`[data-option-id="${c.id}"]`)?.focus(); }); }
function focusFirst(last: boolean) { const choices = pagedCandidates.value.filter(c => !c.inactive); const c = last ? choices.at(-1) : choices[0]; if (c) focusOption(c); }
function onOptionKeydown(event: KeyboardEvent, c: AccountCandidate) {
  if (["Enter", " ", "Spacebar"].includes(event.key)) { event.preventDefault(); select(c); return; }
  if (!["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) return;
  const choices = pagedCandidates.value.filter(c => !c.inactive), index = choices.findIndex(x => x.id === c.id);
  if (index < 0 || !choices.length) return; event.preventDefault();
  const i = event.key === "Home" ? 0 : event.key === "End" ? choices.length - 1 : (index + (event.key === "ArrowDown" ? 1 : -1) + choices.length) % choices.length;
  focusOption(choices[i]!);
}
function select(c: AccountCandidate | null) {
  if (props.disabled || c?.inactive) return;
  if (c) activeId.value = c.id;
  if (props.compactSelection) { keyword.value = ""; optionsOpen.value = !c; }
  suppressModelReset = true; emit("update:modelValue", c);
  void nextTick(() => { suppressModelReset = false; if (props.compactSelection) (c ? changeButton.value : searchInput.value)?.focus(); });
}
</script>
