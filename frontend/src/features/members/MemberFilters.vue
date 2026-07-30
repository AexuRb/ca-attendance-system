<template>
  <form class="member-filter-shell" @submit.prevent="$emit('submit')">
    <div class="member-filter-main">
      <label class="member-search-field">
        <span>搜索成员</span>
        <input
          :value="keyword"
          name="memberKeyword"
          type="search"
          autocomplete="off"
          placeholder="姓名、学号、手机号或学院"
          @input="update('keyword', $event)"
        />
      </label>
      <button
        class="button secondary member-filter-toggle"
        type="button"
        :aria-expanded="filtersOpen"
        aria-controls="member-advanced-filters"
        @click="filtersOpen = !filtersOpen"
      >
        <SlidersHorizontal aria-hidden="true" />
        筛选
        <b v-if="activeFilters.length">{{ activeFilters.length }}</b>
      </button>
      <button class="button primary" type="submit">
        <Search aria-hidden="true" />查询
      </button>
    </div>

    <Transition name="filter-expand">
      <div
        v-if="filtersOpen"
        id="member-advanced-filters"
        class="member-advanced-filters"
      >
        <label>
          <span>角色</span>
          <select
            :value="role"
            name="memberRole"
            @change="update('role', $event)"
          >
            <option value="">全部角色</option>
            <option value="MEMBER">成员</option>
            <option value="MINISTER">部长</option>
            <option value="PRESIDENT">会长</option>
            <option value="ADMIN">管理员</option>
          </select>
        </label>
        <label>
          <span>状态</span>
          <select
            :value="status"
            name="memberStatus"
            @change="update('status', $event)"
          >
            <option value="">全部状态</option>
            <option value="ACTIVE">启用</option>
            <option value="DISABLED">停用</option>
          </select>
        </label>
        <label>
          <span>年级</span>
          <select
            :value="grade"
            name="memberGrade"
            @change="update('grade', $event)"
          >
            <option value="">全部年级</option>
            <option v-for="item in grades" :key="item" :value="item">
              {{ item }}
            </option>
          </select>
        </label>
        <button
          v-if="activeFilters.length"
          class="button text member-filter-clear"
          type="button"
          @click="clearAll"
        >
          清除全部筛选
        </button>
      </div>
    </Transition>

    <div
      v-if="activeFilters.length"
      class="active-filter-row"
      aria-label="当前筛选条件"
    >
      <span>当前筛选</span>
      <button
        v-for="filter in activeFilters"
        :key="filter.key"
        class="filter-chip"
        type="button"
        :aria-label="`移除筛选：${filter.label}`"
        @click="clearFilter(filter.key)"
      >
        {{ filter.label }}<X aria-hidden="true" />
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { Search, SlidersHorizontal, X } from "@lucide/vue";

const props = defineProps<{
  keyword: string;
  role: string;
  status: string;
  grade: string;
  grades: string[];
}>();

const emit = defineEmits<{
  "update:keyword": [value: string];
  "update:role": [value: string];
  "update:status": [value: string];
  "update:grade": [value: string];
  submit: [];
}>();

type FilterKey = "role" | "status" | "grade";

const filtersOpen = ref(false);
const activeFilters = computed(() =>
  [
    props.role
      ? {
          key: "role" as const,
          label: `角色：${roleLabel(props.role)}`,
        }
      : null,
    props.status
      ? {
          key: "status" as const,
          label: `状态：${props.status === "ACTIVE" ? "启用" : "停用"}`,
        }
      : null,
    props.grade
      ? { key: "grade" as const, label: `年级：${props.grade}` }
      : null,
  ].filter(Boolean) as Array<{ key: FilterKey; label: string }>,
);

function update(
  field: "keyword" | FilterKey,
  event: Event,
) {
  const value = (event.target as HTMLInputElement | HTMLSelectElement).value;
  if (field === "keyword") emit("update:keyword", value);
  else if (field === "role") emit("update:role", value);
  else if (field === "status") emit("update:status", value);
  else emit("update:grade", value);
}

async function clearFilter(field: FilterKey) {
  if (field === "role") emit("update:role", "");
  else if (field === "status") emit("update:status", "");
  else emit("update:grade", "");
  await nextTick();
  emit("submit");
}

async function clearAll() {
  emit("update:role", "");
  emit("update:status", "");
  emit("update:grade", "");
  await nextTick();
  emit("submit");
}

function roleLabel(role: string) {
  return (
    (
      {
        MEMBER: "成员",
        MINISTER: "部长",
        PRESIDENT: "会长",
        ADMIN: "管理员",
      } as Record<string, string>
    )[role] || role
  );
}
</script>
