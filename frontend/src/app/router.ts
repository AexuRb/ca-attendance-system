import {
  createRouter,
  createWebHashHistory,
  type RouteLocationNormalized,
  type RouteLocationRaw,
  type RouteRecordRaw,
} from "vue-router";
import { useSession } from "./session";
import type { AccessContext, Role, UserSession } from "../shared/types";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    name: "kiosk",
    component: () => import("../pages/kiosk/KioskPage.vue"),
  },
  {
    path: "/login",
    name: "login",
    component: () => import("../pages/auth/LoginPage.vue"),
  },
  {
    path: "/setup",
    name: "setup",
    component: () => import("../pages/auth/SetupPage.vue"),
  },
  {
    path: "/password",
    name: "password",
    component: () => import("../pages/auth/PasswordPage.vue"),
  },
  {
    path: "/admin",
    component: () => import("../layouts/AdminLayout.vue"),
    meta: { auth: true },
    children: [
      { path: "", redirect: "/admin/today" },
      {
        path: "today",
        name: "today",
        component: () => import("../pages/admin/TodayPage.vue"),
        meta: { roles: ["MINISTER", "PRESIDENT", "ADMIN"] },
      },
      {
        path: "reviews",
        name: "reviews",
        component: () => import("../pages/admin/ReviewsPage.vue"),
        meta: { roles: ["MINISTER", "PRESIDENT", "ADMIN"] },
      },
      {
        path: "attendance",
        name: "attendance",
        component: () => import("../pages/admin/AttendancePage.vue"),
        meta: { roles: ["MINISTER", "PRESIDENT", "ADMIN"] },
      },
      {
        path: "stats",
        name: "stats",
        component: () => import("../pages/admin/StatsPage.vue"),
        meta: { roles: ["MINISTER", "PRESIDENT", "ADMIN"] },
      },
      {
        path: "schedules",
        name: "schedules",
        component: () => import("../pages/admin/SchedulePage.vue"),
        meta: { roles: ["PRESIDENT", "ADMIN"] },
      },
      {
        path: "members",
        name: "members",
        component: () => import("../pages/admin/MembersPage.vue"),
        meta: { roles: ["PRESIDENT", "ADMIN"] },
      },
      {
        path: "profile",
        name: "profile",
        component: () => import("../pages/admin/ProfilePage.vue"),
        meta: { roles: ["MEMBER", "MINISTER", "PRESIDENT", "ADMIN"] },
      },
      {
        path: "trainings",
        name: "trainings",
        component: () => import("../pages/admin/TrainingPage.vue"),
        meta: { roles: ["PRESIDENT", "ADMIN"] },
      },
      {
        path: "repairs",
        name: "repairs",
        component: () => import("../pages/admin/RepairsPage.vue"),
        meta: { roles: ["MINISTER", "PRESIDENT", "ADMIN"] },
      },
      {
        path: "data",
        name: "data",
        component: () => import("../pages/admin/DataPage.vue"),
        meta: { roles: ["PRESIDENT", "ADMIN"] },
      },
      {
        path: "settings",
        name: "settings",
        component: () => import("../pages/admin/SettingsPage.vue"),
        meta: { roles: ["PRESIDENT", "ADMIN"] },
      },
      {
        path: "logs",
        name: "logs",
        component: () => import("../pages/admin/LogsPage.vue"),
        meta: { roles: ["ADMIN"] },
      },
    ],
  },
  { path: "/:pathMatch(.*)*", redirect: "/" },
];

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach(async (to) => {
  const session = useSession();
  await session.bootstrap();
  return resolveRouteAccess(to, session.state);
});

interface RouteAccessState {
  setup: { initialized: boolean };
  access: AccessContext;
  user: UserSession | null;
}

interface RouteAccessTarget {
  name: RouteLocationNormalized["name"] | null;
  fullPath: string;
  meta: RouteLocationNormalized["meta"];
}

export function resolveRouteAccess(
  to: RouteAccessTarget,
  state: RouteAccessState,
): RouteLocationRaw | true {
  if (
    !state.setup.initialized &&
    state.access.mode === "LOCAL" &&
    to.name !== "setup"
  )
    return { name: "setup" };
  if (to.name === "kiosk" && !state.access.kioskAvailable)
    return { name: "login" };
  if (to.meta.auth && !state.user)
    return { name: "login", query: { next: to.fullPath } };
  if (state.user?.mustChangePassword && to.name !== "password")
    return { name: "password" };
  const roles = to.meta.roles as Role[] | undefined;
  if (roles && state.user && !roles.includes(state.user.role)) {
    return state.user.role === "MEMBER"
      ? { name: "profile" }
      : { name: "today" };
  }
  if (to.name === "login" && state.user) {
    return state.user.role === "MEMBER"
      ? { name: "profile" }
      : { name: "today" };
  }
  return true;
}

export function safeLoginNext(value: unknown): string | null {
  return typeof value === "string" && /^\/(?!\/)/.test(value) ? value : null;
}
