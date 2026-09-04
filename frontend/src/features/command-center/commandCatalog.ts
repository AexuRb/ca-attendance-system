import type { Role } from "../../shared/types";
import type { CommandSuggestion } from "./commandTypes";

const MANAGERS: Role[] = ["MINISTER", "PRESIDENT", "ADMIN"];
const LEADERS: Role[] = ["PRESIDENT", "ADMIN"];
const ADMINS: Role[] = ["ADMIN"];

export const commandActions = ["打开", "查看", "查找", "预览", "新建", "导入", "导出", "设置", "创建"] as const;
export type CommandAction = (typeof commandActions)[number];

export interface CommandShortcutDefinition {
  id: string;
  keyword: string;
  command: string;
  description: string;
  roles: Role[];
  execution: CommandSuggestion["execution"];
}

export interface CommandObjectDefinition {
  canonical: string;
  aliases: string[];
  roles: Role[];
  routeName: string;
}

export const commandObjects: CommandObjectDefinition[] = [
  { canonical: "今日概览", aliases: ["今日", "首页", "概览"], roles: MANAGERS, routeName: "today" },
  { canonical: "签到审核", aliases: ["审核", "考勤审核"], roles: MANAGERS, routeName: "reviews" },
  { canonical: "值班记录", aliases: ["考勤", "签到记录", "值班"], roles: MANAGERS, routeName: "attendance" },
  { canonical: "数据统计", aliases: ["统计", "时长统计"], roles: MANAGERS, routeName: "stats" },
  { canonical: "排班管理", aliases: ["排班", "固定周表"], roles: LEADERS, routeName: "schedules" },
  { canonical: "成员名册", aliases: ["成员", "人员", "名册"], roles: LEADERS, routeName: "members" },
  { canonical: "个人资料", aliases: ["我的", "个人", "资料"], roles: MANAGERS, routeName: "profile" },
  { canonical: "维修事务", aliases: ["维修", "维修记录"], roles: MANAGERS, routeName: "repairs" },
  { canonical: "维修协议", aliases: ["协议", "维修协议预览"], roles: MANAGERS, routeName: "repairs" },
  { canonical: "培训记录", aliases: ["培训", "培训场次"], roles: LEADERS, routeName: "trainings" },
  { canonical: "数据与备份", aliases: ["数据", "备份", "数据中心"], roles: LEADERS, routeName: "data" },
  { canonical: "自定义导出", aliases: ["自定义Excel", "自定义 Excel"], roles: LEADERS, routeName: "data" },
  { canonical: "系统设置", aliases: ["设置", "系统参数"], roles: LEADERS, routeName: "settings" },
  { canonical: "操作日志", aliases: ["日志", "审计日志"], roles: ADMINS, routeName: "logs" },
  { canonical: "维修回收站", aliases: ["回收站"], roles: ADMINS, routeName: "data" },
  { canonical: "备份管理", aliases: ["备份列表"], roles: LEADERS, routeName: "data" },
  { canonical: "值班星期", aliases: [], roles: LEADERS, routeName: "settings" },
  { canonical: "值班时段", aliases: [], roles: LEADERS, routeName: "settings" },
  { canonical: "有效时长限制", aliases: ["有效时长规则"], roles: ADMINS, routeName: "settings" },
];

export const dangerousActions = [
  "删除",
  "永久删除",
  "清空",
  "恢复",
  "停用",
  "启用",
  "任命",
  "重置密码",
  "通过审核",
  "驳回审核",
  "批量审核",
  "修改记录",
  "修改角色",
  "修改值班时间",
];

function suggestion(
  id: string,
  command: string,
  description: string,
  roles: Role[],
  execution: CommandSuggestion["execution"] = "navigate",
  keywords: string[] = [],
): CommandSuggestion {
  return { id, command, description, roles, execution, keywords };
}

function parameterSuggestions(
  idPrefix: string,
  commandPrefix: string,
  values: readonly string[],
  description: (value: string) => string,
  roles: Role[],
  execution: CommandSuggestion["execution"] = "navigate",
): CommandSuggestion[] {
  return values.map((value) => suggestion(
    idPrefix + "-" + value,
    commandPrefix + " " + value,
    description(value),
    roles,
    execution,
  ));
}

function uniqueCommandPaths(items: CommandSuggestion[]): CommandSuggestion[] {
  const seen = new Set<string>();
  return items.filter((item) => {
    if (seen.has(item.command)) return false;
    seen.add(item.command);
    return true;
  });
}

const ATTENDANCE_RANGES = ["今天", "昨天", "本周", "上周", "本月", "上月"] as const;
const STATS_RANGES = [...ATTENDANCE_RANGES, "本年"] as const;
const REPAIR_STATUSES = ["进行中", "已完成", "已取消"] as const;
const REPAIR_RANGES = STATS_RANGES;
const SCHEDULE_DAYS = ["今天", "周一", "周二", "周三", "周四", "周五", "周六", "周日"] as const;
const MEMBER_STATUSES = ["启用", "停用"] as const;
const TRAINING_RANGES = ["本月", "上月", "本年"] as const;
const LOG_RANGES = ["今天", "昨天", "本周", "本月"] as const;

export const commandSuggestions: CommandSuggestion[] = [
  suggestion("today", "/ 打开 今日概览", "进入今日工作区", MANAGERS, "navigate", ["今日", "首页"]),
  suggestion("reviews", "/ 打开 签到审核", "处理待审核签到与签退", MANAGERS, "navigate", ["审核"]),
  suggestion("attendance-week", "/ 查看 值班记录 本周", "打开本周值班记录", MANAGERS, "navigate", ["考勤", "本周"]),
  suggestion("attendance-pending", "/ 查看 值班记录 待审核", "筛选待审核记录", MANAGERS, "navigate", ["待审核"]),
  suggestion("attendance-open", "/ 查看 值班记录 未签退", "筛选未签退记录", MANAGERS, "navigate", ["未签退"]),
  suggestion("stats-week", "/ 查看 数据统计 本周", "查看本周时长统计", MANAGERS, "navigate", ["统计"]),
  suggestion("stats-export", "/ 导出 数据统计 本周", "预览并导出本周统计", MANAGERS, "confirm", ["统计", "导出"]),
  suggestion("repairs", "/ 打开 维修事务", "进入进行中的维修", MANAGERS, "navigate", ["维修"]),
  suggestion("repairs-new", "/ 新建 维修事务", "打开维修事务创建表单", MANAGERS, "prefill", ["维修", "新建"]),
  suggestion("repairs-completed", "/ 查看 维修事务 已完成", "查看已完成维修", MANAGERS, "navigate", ["维修", "完成"]),
  suggestion("profile", "/ 打开 个人资料", "进入当前账号资料", MANAGERS, "navigate", ["我的", "个人"]),
  suggestion("attendance-new", "/ 新建 值班记录", "打开手动补录表单", LEADERS, "prefill", ["补录"]),
  suggestion("schedules", "/ 打开 排班管理", "进入固定周排班", LEADERS, "navigate", ["排班"]),
  suggestion("schedules-today", "/ 查看 排班管理 今天", "打开今天对应星期", LEADERS, "navigate", ["排班", "今天"]),
  suggestion("schedules-import", "/ 导入 排班管理", "打开排班导入窗口", LEADERS, "prefill", ["排班", "导入"]),
  suggestion("members", "/ 打开 成员名册", "进入成员名册", LEADERS, "navigate", ["成员"]),
  suggestion("members-new", "/ 新建 成员", "打开成员创建表单", LEADERS, "prefill", ["成员", "新建"]),
  suggestion("members-import", "/ 导入 成员名册", "打开成员导入窗口", LEADERS, "prefill", ["成员", "导入"]),
  suggestion("trainings", "/ 打开 培训记录", "进入培训记录", LEADERS, "navigate", ["培训"]),
  suggestion("trainings-new", "/ 新建 培训", "打开培训场次创建表单", LEADERS, "prefill", ["培训", "新建"]),
  suggestion("trainings-import", "/ 导入 培训记录", "打开培训导入窗口", LEADERS, "prefill", ["培训", "导入"]),
  suggestion("repairs-export", "/ 导出 维修事务 本月", "预览并导出本月维修", LEADERS, "confirm", ["维修", "导出"]),
  suggestion("data", "/ 打开 数据与备份", "进入数据与备份", LEADERS, "navigate", ["数据", "备份"]),
  suggestion("custom-export", "/ 打开 自定义导出", "进入自定义导出工作区", LEADERS, "navigate", ["导出"]),
  suggestion("backup-create", "/ 创建 备份", "确认后创建完整备份", LEADERS, "confirm", ["备份"]),
  suggestion("settings", "/ 打开 系统设置", "进入系统设置", LEADERS, "navigate", ["设置"]),
  suggestion("settings-weekdays", "/ 设置 值班星期", "定位值班星期设置", LEADERS, "prefill", ["星期"]),
  suggestion("settings-periods", "/ 设置 值班时段", "定位值班时段设置", LEADERS, "prefill", ["时段"]),
  suggestion("logs", "/ 打开 操作日志", "进入操作日志", ADMINS, "navigate", ["日志"]),
  suggestion("logs-today", "/ 查看 操作日志 今天", "查看今天的操作日志", ADMINS, "navigate", ["日志", "今天"]),
  suggestion("recycle", "/ 打开 维修回收站", "进入维修回收站", ADMINS, "navigate", ["回收站"]),
  suggestion("settings-policy", "/ 设置 有效时长限制", "定位有效时长规则", ADMINS, "prefill", ["规则", "时长"]),
];

export const commandShortcuts: CommandShortcutDefinition[] = [
  { id: "shortcut-today", keyword: "今日", command: "/ 打开 今日概览", description: "进入今日工作区", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-reviews", keyword: "审核", command: "/ 打开 签到审核", description: "处理待审核签到与签退", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-attendance", keyword: "考勤", command: "/ 查看 值班记录 本周", description: "查看本周值班记录", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-stats", keyword: "统计", command: "/ 查看 数据统计 本周", description: "查看本周时长统计", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-repairs", keyword: "维修", command: "/ 打开 维修事务", description: "进入进行中的维修", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-schedules", keyword: "排班", command: "/ 打开 排班管理", description: "进入固定周排班", roles: LEADERS, execution: "navigate" },
  { id: "shortcut-members", keyword: "成员", command: "/ 打开 成员名册", description: "进入成员名册", roles: LEADERS, execution: "navigate" },
  { id: "shortcut-profile", keyword: "我的", command: "/ 打开 个人资料", description: "进入当前账号资料", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-trainings", keyword: "培训", command: "/ 打开 培训记录", description: "进入培训记录", roles: LEADERS, execution: "navigate" },
  { id: "shortcut-data", keyword: "数据", command: "/ 打开 数据与备份", description: "进入数据与备份", roles: LEADERS, execution: "navigate" },
  { id: "shortcut-settings", keyword: "设置", command: "/ 打开 系统设置", description: "进入系统设置", roles: LEADERS, execution: "navigate" },
  { id: "shortcut-logs", keyword: "日志", command: "/ 打开 操作日志", description: "进入操作日志", roles: ADMINS, execution: "navigate" },
  { id: "shortcut-incomplete", keyword: "未签退", command: "/ 查看 值班记录 未签退", description: "筛选未签退记录", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-pending", keyword: "待审核", command: "/ 打开 签到审核", description: "处理待审核签到与签退", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-week-stats", keyword: "本周统计", command: "/ 查看 数据统计 本周", description: "查看本周时长统计", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-active-repairs", keyword: "进行中维修", command: "/ 查看 维修事务 进行中", description: "查看进行中的维修", roles: MANAGERS, execution: "navigate" },
  { id: "shortcut-today-schedule", keyword: "今日排班", command: "/ 查看 排班管理 今天", description: "打开今天对应星期", roles: LEADERS, execution: "navigate" },
  { id: "shortcut-month-training", keyword: "本月培训", command: "/ 查看 培训记录 本月", description: "查看本月培训记录", roles: LEADERS, execution: "navigate" },
  { id: "shortcut-backups", keyword: "备份管理", command: "/ 查看 备份管理", description: "打开备份列表", roles: LEADERS, execution: "navigate" },
  { id: "shortcut-recycle", keyword: "回收站", command: "/ 打开 维修回收站", description: "进入维修回收站", roles: ADMINS, execution: "navigate" },
];

const repairViewPaths = REPAIR_STATUSES.flatMap((status) => [
  suggestion("repairs-view-" + status, "/ 查看 维修事务 " + status, "查看" + status + "维修", MANAGERS),
  ...parameterSuggestions(
    "repairs-view-" + status + "-range",
    "/ 查看 维修事务 " + status,
    REPAIR_RANGES,
    (range) => "查看" + status + "的" + range + "维修",
    MANAGERS,
  ),
]);

const repairExportPaths = REPAIR_STATUSES.flatMap((status) => [
  suggestion("repairs-export-" + status, "/ 导出 维修事务 " + status, "导出" + status + "维修", LEADERS, "confirm"),
  ...parameterSuggestions(
    "repairs-export-" + status + "-range",
    "/ 导出 维修事务 " + status,
    REPAIR_RANGES,
    (range) => "导出" + status + "的" + range + "维修",
    LEADERS,
    "confirm",
  ),
]);

export const commandTreePaths: CommandSuggestion[] = uniqueCommandPaths([
  ...commandSuggestions,
  ...parameterSuggestions("attendance-view-range", "/ 查看 值班记录", ATTENDANCE_RANGES, (range) => "查看" + range + "值班记录", MANAGERS),
  suggestion("attendance-view-pending", "/ 查看 值班记录 待审核", "筛选待审核记录", MANAGERS),
  suggestion("attendance-view-incomplete", "/ 查看 值班记录 未签退", "筛选未签退记录", MANAGERS),
  suggestion("attendance-search-path", "/ 查找 值班记录", "输入姓名、学号或关键词", MANAGERS),
  ...parameterSuggestions("stats-view-range", "/ 查看 数据统计", STATS_RANGES, (range) => "查看" + range + "时长统计", MANAGERS),
  ...parameterSuggestions("stats-export-range", "/ 导出 数据统计", STATS_RANGES, (range) => "导出" + range + "时长统计", MANAGERS, "confirm"),
  ...parameterSuggestions("repairs-view-range", "/ 查看 维修事务", REPAIR_RANGES, (range) => "查看进行中的" + range + "维修", MANAGERS),
  ...repairViewPaths,
  suggestion("repairs-search-path", "/ 查找 维修事务", "输入完整维修编号或关键词", MANAGERS),
  suggestion("agreement-preview-path", "/ 预览 维修协议", "输入完整维修编号", MANAGERS),
  ...parameterSuggestions("schedule-view-day", "/ 查看 排班管理", SCHEDULE_DAYS, (day) => "打开" + day + "排班", LEADERS),
  ...parameterSuggestions("members-view-status", "/ 查看 成员名册", MEMBER_STATUSES, (status) => "查看" + status + "成员", LEADERS),
  suggestion("members-search-path", "/ 查找 成员名册", "输入姓名或学号", LEADERS),
  ...parameterSuggestions("trainings-view-range", "/ 查看 培训记录", TRAINING_RANGES, (range) => "查看" + range + "培训记录", LEADERS),
  suggestion("trainings-search-path", "/ 查找 培训记录", "输入培训主题或主讲人", LEADERS),
  ...parameterSuggestions("trainings-export-range", "/ 导出 培训记录", TRAINING_RANGES, (range) => "导出" + range + "培训记录", LEADERS, "confirm"),
  ...parameterSuggestions("repairs-export-range", "/ 导出 维修事务", REPAIR_RANGES, (range) => "导出进行中的" + range + "维修", LEADERS, "confirm"),
  ...repairExportPaths,
  suggestion("backups-view-path", "/ 查看 备份管理", "打开备份列表", LEADERS),
  ...parameterSuggestions("logs-view-range", "/ 查看 操作日志", LOG_RANGES, (range) => "查看" + range + "操作日志", ADMINS),
  suggestion("logs-search-path", "/ 查找 操作日志", "输入操作类型或关键词", ADMINS),
  ...parameterSuggestions("logs-export-range", "/ 导出 操作日志", LOG_RANGES, (range) => "导出" + range + "操作日志", ADMINS, "confirm"),
]);

const defaultIds: Record<Exclude<Role, "MEMBER">, string[]> = {
  MINISTER: ["reviews", "attendance-week", "repairs", "stats-week", "repairs-new"],
  PRESIDENT: ["reviews", "schedules-today", "members", "repairs", "trainings"],
  ADMIN: ["settings", "logs", "data", "members", "repairs"],
};

export function defaultCommands(role: Role): CommandSuggestion[] {
  if (role === "MEMBER") return [];
  const ids = defaultIds[role];
  return ids
    .map((id) => commandSuggestions.find((item) => item.id === id))
    .filter((item): item is CommandSuggestion => Boolean(item));
}
