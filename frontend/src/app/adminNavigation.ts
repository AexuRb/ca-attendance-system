import type { Component } from "vue";
import {
  BarChart3,
  CalendarDays,
  ClipboardCheck,
  ClipboardList,
  Database,
  Gauge,
  GraduationCap,
  History,
  Settings2,
  UserRound,
  UsersRound,
  Wrench,
} from "@lucide/vue";
import type { Role } from "../shared/types";

export interface AdminNavItem {
  name: string;
  label: string;
  icon: Component;
  roles: Role[];
}

export interface AdminNavSection {
  key: string;
  label: string;
  icon: Component;
  items: AdminNavItem[];
}

export const adminNavigation: AdminNavSection[] = [
  {
    key: "today",
    label: "今日",
    icon: Gauge,
    items: [
      {
        name: "today",
        label: "今日概览",
        icon: Gauge,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
      {
        name: "reviews",
        label: "签到审核",
        icon: ClipboardCheck,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
    ],
  },
  {
    key: "duty",
    label: "值班",
    icon: ClipboardCheck,
    items: [
      {
        name: "attendance",
        label: "值班记录",
        icon: ClipboardList,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
      {
        name: "stats",
        label: "数据统计",
        icon: BarChart3,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
      {
        name: "schedules",
        label: "排班管理",
        icon: CalendarDays,
        roles: ["PRESIDENT", "ADMIN"],
      },
    ],
  },
  {
    key: "people",
    label: "人员",
    icon: UsersRound,
    items: [
      {
        name: "members",
        label: "成员名册",
        icon: UsersRound,
        roles: ["PRESIDENT", "ADMIN"],
      },
      {
        name: "profile",
        label: "个人资料",
        icon: UserRound,
        roles: ["MEMBER", "MINISTER", "PRESIDENT", "ADMIN"],
      },
    ],
  },
  {
    key: "work",
    label: "事务",
    icon: Wrench,
    items: [
      {
        name: "repairs",
        label: "维修事务",
        icon: Wrench,
        roles: ["MINISTER", "PRESIDENT", "ADMIN"],
      },
      {
        name: "trainings",
        label: "培训记录",
        icon: GraduationCap,
        roles: ["PRESIDENT", "ADMIN"],
      },
    ],
  },
  {
    key: "system",
    label: "系统",
    icon: Settings2,
    items: [
      {
        name: "data",
        label: "数据与备份",
        icon: Database,
        roles: ["PRESIDENT", "ADMIN"],
      },
      {
        name: "settings",
        label: "系统设置",
        icon: Settings2,
        roles: ["PRESIDENT", "ADMIN"],
      },
      {
        name: "logs",
        label: "操作日志",
        icon: History,
        roles: ["ADMIN"],
      },
    ],
  },
];

export function navigationForRole(role?: Role): AdminNavSection[] {
  if (!role) return [];
  return adminNavigation
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => item.roles.includes(role)),
    }))
    .filter((section) => section.items.length > 0);
}

export function roleLabel(role?: Role): string {
  return (
    {
      MEMBER: "成员",
      MINISTER: "部长",
      PRESIDENT: "会长",
      ADMIN: "管理员",
    } as Record<Role, string>
  )[role || "MEMBER"];
}
