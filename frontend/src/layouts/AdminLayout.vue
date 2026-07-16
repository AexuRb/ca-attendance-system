<template>
  <div class="admin-layout" :class="{ 'nav-open': navOpen }">
    <aside class="admin-sidebar">
      <div class="sidebar-brand">
        <div class="brand-emblem">CA</div>
        <div><strong>计协后台</strong><span>LOCAL CONSOLE</span></div>
      </div>

      <nav class="admin-nav" aria-label="后台导航">
        <div
          v-for="group in visibleGroups"
          :key="group.label"
          class="nav-group"
        >
          <p>{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.name"
            :to="{ name: item.name }"
            @click="navOpen = false"
          >
            <component :is="item.icon" aria-hidden="true" /><span>{{
              item.label
            }}</span>
            <span v-if="item.dot" class="nav-dot"></span>
          </RouterLink>
        </div>
      </nav>

      <div class="sidebar-foot">
        <button
          type="button"
          class="sidebar-credit"
          @click="creditsOpen = true"
        >
          <HeartHandshake aria-hidden="true" />鸣谢
        </button>
        <div class="sidebar-user">
          <span class="avatar">{{ user?.name?.slice(0, 1) }}</span>
          <div>
            <strong>{{ user?.name }}</strong
            ><span>{{ roleLabel(user?.role) }}</span>
          </div>
          <button
            class="icon-button ghost"
            type="button"
            title="退出登录"
            aria-label="退出登录"
            @click="signOut"
          >
            <LogOut />
          </button>
        </div>
      </div>
    </aside>

    <button
      v-if="navOpen"
      class="nav-scrim"
      aria-label="关闭导航"
      @click="navOpen = false"
    ></button>

    <div class="admin-stage">
      <header class="admin-topbar">
        <button
          class="icon-button mobile-menu"
          type="button"
          aria-label="打开导航"
          @click="navOpen = true"
        >
          <Menu />
        </button>
        <div class="topbar-context">
          <span>{{ currentSection }}</span
          ><strong>{{ currentTitle }}</strong>
        </div>
        <div class="topbar-tools">
          <label
            v-if="canManageTerms && termState.terms.length"
            class="term-select"
          >
            <CalendarRange aria-hidden="true" />
            <span class="sr-only">当前查看学期</span>
            <select
              v-model.number="termState.selectedId"
              @change="syncTermQuery"
            >
              <option
                v-for="term in termState.terms"
                :key="term.id"
                :value="term.id"
              >
                {{ term.name }}
              </option>
            </select>
          </label>
          <StatusBadge
            v-if="selectedTerm"
            :label="termStatusLabel(selectedTerm.status)"
            :tone="termTone(selectedTerm.status)"
          />
          <span class="local-clock">{{ clock }}</span>
        </div>
      </header>
      <main class="admin-content"><RouterView /></main>
    </div>
    <CreditsDialog :open="creditsOpen" @close="creditsOpen = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import {
  BarChart3,
  CalendarClock,
  CalendarDays,
  CalendarRange,
  ClipboardCheck,
  ClipboardList,
  Database,
  Gauge,
  GraduationCap,
  HeartHandshake,
  History,
  LogOut,
  Menu,
  Settings2,
  UserRound,
  UsersRound,
  Wrench,
} from "@lucide/vue";
import CreditsDialog from "../shared/ui/CreditsDialog.vue";
import StatusBadge from "../shared/ui/StatusBadge.vue";
import { useSession } from "../app/session";
import { useTerms } from "../shared/composables/useTerms";
import type { Role } from "../shared/types";

const { user, logout } = useSession();
const { state: termState, selectedTerm, loadTerms } = useTerms();
const route = useRoute();
const router = useRouter();
const navOpen = ref(false);
const creditsOpen = ref(false);
const clock = ref("");
let timer = 0;

const groups = [
  {
    label: "值班",
    items: [
      {
        name: "today",
        label: "今日",
        icon: Gauge,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
      {
        name: "reviews",
        label: "签到审核",
        icon: ClipboardCheck,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
      {
        name: "attendance",
        label: "值班记录",
        icon: ClipboardList,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
      {
        name: "stats",
        label: "统计",
        icon: BarChart3,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
      {
        name: "schedules",
        label: "排班",
        icon: CalendarDays,
        roles: ["PRESIDENT", "ADMIN"],
      },
    ],
  },
  {
    label: "人员",
    items: [
      {
        name: "members",
        label: "成员",
        icon: UsersRound,
        roles: ["PRESIDENT", "ADMIN"],
      },
      {
        name: "profile",
        label: "个人",
        icon: UserRound,
        roles: ["MEMBER", "MINISTER", "PRESIDENT", "ADMIN"],
      },
    ],
  },
  {
    label: "事务",
    items: [
      {
        name: "trainings",
        label: "培训",
        icon: GraduationCap,
        roles: ["PRESIDENT", "ADMIN"],
      },
      {
        name: "repairs",
        label: "维修",
        icon: Wrench,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
    ],
  },
  {
    label: "系统",
    items: [
      {
        name: "terms",
        label: "学期与结算",
        icon: CalendarClock,
        roles: ["PRESIDENT", "ADMIN"],
      },
      {
        name: "data",
        label: "数据与备份",
        icon: Database,
        roles: ["PRESIDENT", "ADMIN"],
      },
      {
        name: "settings",
        label: "设置",
        icon: Settings2,
        roles: ["PRESIDENT", "ADMIN"],
      },
      { name: "logs", label: "操作日志", icon: History, roles: ["ADMIN"] },
    ],
  },
];

const role = computed(() => user.value?.role as Role | undefined);
const visibleGroups = computed(() =>
  groups
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) => role.value && item.roles.includes(role.value),
      ),
    }))
    .filter((group) => group.items.length),
);
const activeItem = computed(() =>
  groups
    .flatMap((group) => group.items)
    .find((item) => item.name === route.name),
);
const currentTitle = computed(() => activeItem.value?.label || "后台");
const currentSection = computed(
  () =>
    groups.find((group) => group.items.some((item) => item.name === route.name))
      ?.label || "系统",
);
const canManageTerms = computed(
  () => role.value === "PRESIDENT" || role.value === "ADMIN",
);

onMounted(async () => {
  if (canManageTerms.value) {
    try {
      await loadTerms();
      const queryTerm = Number(route.query.termId);
      if (queryTerm && termState.terms.some((item) => item.id === queryTerm))
        termState.selectedId = queryTerm;
    } catch {
      /* pages still work before the first term is created */
    }
  }
  updateClock();
  timer = window.setInterval(updateClock, 30_000);
});
onBeforeUnmount(() => window.clearInterval(timer));

function updateClock() {
  clock.value = new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date());
}
function syncTermQuery() {
  router.replace({
    query: { ...route.query, termId: termState.selectedId || undefined },
  });
}
async function signOut() {
  await logout();
  router.replace({ name: "login" });
}
function roleLabel(value?: Role) {
  return (
    (
      {
        MEMBER: "成员",
        MINISTER: "部长",
        PRESIDENT: "会长",
        ADMIN: "管理员",
      } as Record<string, string>
    )[value || ""] || ""
  );
}
function termStatusLabel(status: string) {
  return (
    (
      {
        DRAFT: "草稿",
        ACTIVE: "进行中",
        SETTLING: "结算中",
        SEALED: "已封存",
      } as Record<string, string>
    )[status] || status
  );
}
function termTone(status: string): "neutral" | "success" | "warning" | "info" {
  return status === "ACTIVE"
    ? "success"
    : status === "SETTLING"
      ? "warning"
      : status === "SEALED"
        ? "neutral"
        : "info";
}
</script>
