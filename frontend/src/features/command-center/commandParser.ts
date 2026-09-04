import type { LocationQueryRaw } from "vue-router";
import type { Role } from "../../shared/types";
import {
  commandActions,
  commandObjects,
  commandShortcuts,
  commandSuggestions,
  dangerousActions,
  defaultCommands,
  type CommandAction,
  type CommandObjectDefinition,
} from "./commandCatalog";
import type {
  CommandExecution,
  CommandResolution,
  CommandSuggestion,
  ResolvedCommand,
} from "./commandTypes";

type Query = Record<string, string>;
interface ParsedObject { definition: CommandObjectDefinition; consumed: number }
interface DateRange { from: string; to: string; label: string }
interface ParseFailure { message: string }

export function suggestCommands(
  input: string,
  role: Role,
  limit = 6,
): CommandSuggestion[] {
  const query = normalizedSearch(input);
  if (!query) return defaultCommands(role).slice(0, limit);
  return commandSuggestions
    .filter((item) => item.roles.includes(role))
    .map((item, index) => ({ item, index, score: suggestionScore(item, query) }))
    .filter((candidate) => candidate.score < 100)
    .sort((left, right) => left.score - right.score || left.index - right.index)
    .slice(0, limit)
    .map((candidate) => candidate.item);
}

export function resolveCommand(
  input: string,
  role: Role,
  now = new Date(),
): CommandResolution {
  const content = commandContent(input);
  if (!content) return invalid("请输入命令。");
  let tokens = tokenize(content);
  if (!tokens.length) return invalid("请输入命令。");
  const shortcut = tokens.length === 1
    ? commandShortcuts.find((item) => item.keyword === tokens[0])
    : undefined;
  if (shortcut) tokens = tokenize(commandContent(shortcut.command));

  if (dangerousActions.some((action) => content.startsWith(action))) {
    return {
      kind: "dangerous",
      message: "命令台不直接执行该操作，请前往对应页面核对。",
    };
  }

  const action = tokens[0] as CommandAction | undefined;
  if (!action || !commandActions.includes(action)) {
    return invalid(
      "无法识别动作“" +
        (tokens[0] || content) +
        "”，可使用打开、查看、查找、新建、导入、导出或设置。",
    );
  }
  const parsedObject = matchObject(tokens.slice(1));
  if (!parsedObject) {
    return invalid(
      "没有找到“" +
        (tokens.slice(1).join(" ") || "目标功能") +
        "”，请从建议中选择现有功能。",
    );
  }
  const object = parsedObject.definition;
  if (!object.roles.includes(role)) {
    return { kind: "forbidden", message: "当前角色无权使用“" + object.canonical + "”。" };
  }
  return resolveAction(action, object, tokens.slice(1 + parsedObject.consumed), now);
}

function resolveAction(
  action: CommandAction,
  object: CommandObjectDefinition,
  args: string[],
  now: Date,
): CommandResolution {
  if (action === "打开") return openCommand(object, args);
  if (action === "查看") return viewCommand(object, args, now);
  if (action === "查找") return searchCommand(object, args);
  if (action === "预览") return previewCommand(object, args);
  if (action === "新建") return intentCommand(object, args, "new", "新建");
  if (action === "导入") return intentCommand(object, args, "import", "导入");
  if (action === "导出") return exportCommand(object, args, now);
  if (action === "设置") return settingCommand(object, args);
  return createCommand(object, args);
}

function openCommand(object: CommandObjectDefinition, args: string[]): CommandResolution {
  if (args.length) return extraArguments(args);
  const query: Query = {};
  if (object.canonical === "维修事务") query.status = "REPAIRING";
  if (object.canonical === "自定义导出") query.tab = "export";
  if (object.canonical === "维修回收站") query.tab = "recycle";
  if (object.canonical === "备份管理") query.tab = "backups";
  return resolved(
    "/ 打开 " + object.canonical,
    "已打开" + object.canonical,
    "navigate",
    object.routeName,
    query,
  );
}

function viewCommand(
  object: CommandObjectDefinition,
  args: string[],
  now: Date,
): CommandResolution {
  if (object.canonical === "值班记录") {
    const status = attendanceStatus(args[0]);
    if (status) {
      if (args.length > 1) return extraArguments(args.slice(1));
      return resolved(
        "/ 查看 值班记录 " + args[0],
        "已筛选" + args[0] + "值班记录",
        "navigate",
        object.routeName,
        { status },
      );
    }
    const range = optionalRange(args, now, "本周");
    return "message" in range ? invalid(range.message) : rangeRoute(object, "查看", range);
  }
  if (object.canonical === "数据统计") {
    const range = optionalRange(args, now, "本周");
    return "message" in range ? invalid(range.message) : rangeRoute(object, "查看", range);
  }
  if (object.canonical === "维修事务") {
    const query: Query = { status: "REPAIRING" };
    const remaining = [...args];
    const status = repairStatus(remaining[0]);
    if (status) {
      query.status = status;
      remaining.shift();
    }
    let rangeLabel = "";
    if (remaining.length) {
      const range = exactRange(remaining, now);
      if ("message" in range) return invalid(range.message);
      query.from = range.from;
      query.to = range.to;
      rangeLabel = range.label;
    }
    const label = repairStatusLabel(query.status!);
    return resolved(
      "/ 查看 维修事务 " + label + (rangeLabel ? " " + rangeLabel : ""),
      "已打开" + (rangeLabel || "") + label + "维修事务",
      "navigate",
      object.routeName,
      query,
    );
  }
  if (object.canonical === "排班管理") {
    if (args.length !== 1) return invalid("查看排班需要“今天”或具体星期。");
    const weekday = weekdayValue(args[0], now);
    if (!weekday) return invalid("无法识别星期“" + args[0] + "”。");
    return resolved(
      "/ 查看 排班管理 " + weekday.label,
      "已打开" + weekday.label + "排班",
      "navigate",
      object.routeName,
      { weekday: String(weekday.value) },
    );
  }
  if (object.canonical === "成员名册") {
    const status = memberStatus(args[0]);
    if (args.length !== 1 || !status) return invalid("成员状态只支持启用或停用。");
    return resolved(
      "/ 查看 成员名册 " + args[0],
      "已筛选" + args[0] + "成员",
      "navigate",
      object.routeName,
      { status },
    );
  }
  if (object.canonical === "培训记录" || object.canonical === "操作日志") {
    const fallback = object.canonical === "培训记录" ? "本月" : "今天";
    const range = optionalRange(args, now, fallback);
    return "message" in range ? invalid(range.message) : rangeRoute(object, "查看", range);
  }
  if (object.canonical === "备份管理") {
    if (args.length) return extraArguments(args);
    return resolved(
      "/ 查看 备份管理",
      "已打开备份列表",
      "navigate",
      object.routeName,
      { tab: "backups" },
    );
  }
  return invalid("暂不支持“查看 " + object.canonical + "”。");
}

function searchCommand(object: CommandObjectDefinition, args: string[]): CommandResolution {
  if (!args.length) return invalid("“查找" + object.canonical + "”需要关键词。");
  if (!["值班记录", "维修事务", "成员名册", "培训记录", "操作日志"].includes(object.canonical)) {
    return invalid("暂不支持在“" + object.canonical + "”中查找。");
  }
  const keyword = args.join(" ").trim();
  const query: Query = { keyword };
  if (object.canonical === "维修事务") query.status = "REPAIRING";
  return resolved(
    "/ 查找 " + object.canonical + " “" + keyword + "”",
    "已查找“" + keyword + "”",
    "navigate",
    object.routeName,
    query,
  );
}

function previewCommand(object: CommandObjectDefinition, args: string[]): CommandResolution {
  if (object.canonical !== "维修协议") return invalid("暂不支持预览“" + object.canonical + "”。");
  if (args.length !== 1) return invalid("预览维修协议需要完整维修编号。");
  return resolved(
    "/ 预览 维修协议 " + args[0],
    "正在查找维修协议 " + args[0],
    "navigate",
    object.routeName,
    { status: "REPAIRING", keyword: args[0] || "", intent: "preview-agreement" },
  );
}

function intentCommand(
  object: CommandObjectDefinition,
  args: string[],
  intent: "new" | "import",
  actionLabel: string,
): CommandResolution {
  if (args.length) return extraArguments(args);
  const supported = intent === "new"
    ? ["维修事务", "值班记录", "成员名册", "培训记录"]
    : ["排班管理", "成员名册", "培训记录"];
  if (!supported.includes(object.canonical)) {
    return invalid("暂不支持“" + actionLabel + " " + object.canonical + "”。");
  }
  const query: Query = { intent };
  if (object.canonical === "维修事务") query.status = "REPAIRING";
  const label = displayObjectForIntent(object.canonical);
  return resolved(
    "/ " + actionLabel + " " + label,
    "已打开" + label + (actionLabel === "新建" ? "表单" : "导入"),
    "prefill",
    object.routeName,
    query,
  );
}

function exportCommand(
  object: CommandObjectDefinition,
  args: string[],
  now: Date,
): CommandResolution {
  if (["数据统计", "培训记录", "操作日志"].includes(object.canonical)) {
    const fallback = object.canonical === "数据统计" ? "本周" : "本月";
    const range = optionalRange(args, now, fallback);
    return "message" in range ? invalid(range.message) : exportRangeRoute(object, range);
  }
  if (object.canonical === "维修事务") {
    const remaining = [...args];
    const query: Query = { status: "REPAIRING", intent: "export" };
    const status = repairStatus(remaining[0]);
    if (status) {
      query.status = status;
      remaining.shift();
    }
    const range = optionalRange(remaining, now, "本月");
    if ("message" in range) return invalid(range.message);
    query.from = range.from;
    query.to = range.to;
    const statusLabel = repairStatusLabel(query.status!);
    return resolved(
      "/ 导出 维修事务 " + statusLabel + " " + range.label,
      "已准备" + statusLabel + "维修导出",
      "confirm",
      object.routeName,
      query,
    );
  }
  return invalid("暂不支持导出“" + object.canonical + "”。");
}

function settingCommand(object: CommandObjectDefinition, args: string[]): CommandResolution {
  if (args.length) return extraArguments(args);
  const sections: Record<string, string> = {
    值班星期: "weekdays",
    值班时段: "periods",
    有效时长限制: "policy",
  };
  const section = sections[object.canonical];
  if (!section) return invalid("暂不支持设置“" + object.canonical + "”。");
  return resolved(
    "/ 设置 " + object.canonical,
    "已定位" + object.canonical,
    "prefill",
    object.routeName,
    { section },
  );
}

function createCommand(object: CommandObjectDefinition, args: string[]): CommandResolution {
  if (args.length) return extraArguments(args);
  if (object.canonical !== "数据与备份") return invalid("暂不支持创建“" + object.canonical + "”。");
  return resolved(
    "/ 创建 备份",
    "已打开备份确认",
    "confirm",
    object.routeName,
    { tab: "backups", intent: "create-backup" },
  );
}

function rangeRoute(
  object: CommandObjectDefinition,
  action: string,
  range: DateRange,
): ResolvedCommand {
  return resolved(
    "/ " + action + " " + object.canonical + " " + range.label,
    "已打开" + range.label + object.canonical,
    "navigate",
    object.routeName,
    { from: range.from, to: range.to },
  );
}

function exportRangeRoute(
  object: CommandObjectDefinition,
  range: DateRange,
): ResolvedCommand {
  return resolved(
    "/ 导出 " + object.canonical + " " + range.label,
    "已准备" + range.label + object.canonical + "导出",
    "confirm",
    object.routeName,
    { from: range.from, to: range.to, intent: "export" },
  );
}

function resolved(
  canonical: string,
  feedback: string,
  execution: CommandExecution,
  name: string,
  query: Query = {},
): ResolvedCommand {
  const target = Object.keys(query).length
    ? { name, query: query as LocationQueryRaw }
    : { name };
  return { kind: "resolved", canonical, feedback, execution, target };
}

function matchObject(tokens: string[]): ParsedObject | null {
  const candidates = commandObjects.flatMap((definition) =>
    [definition.canonical, ...definition.aliases].map((alias) => ({
      definition,
      alias: normalizeKey(alias),
    })),
  );
  for (let consumed = Math.min(3, tokens.length); consumed >= 1; consumed -= 1) {
    const key = normalizeKey(tokens.slice(0, consumed).join(""));
    const candidate = candidates.find((item) => item.alias === key);
    if (candidate) return { definition: candidate.definition, consumed };
  }
  return null;
}

function commandContent(input: string): string {
  return input.trim().replace(/^\/\s*/, "");
}

function tokenize(input: string): string[] {
  const tokens: string[] = [];
  const matcher = /["“”']([^"“”']+)["“”']|(\S+)/g;
  let match: RegExpExecArray | null;
  while ((match = matcher.exec(input))) {
    const value = match[1] || match[2];
    if (value) tokens.push(value);
  }
  return tokens;
}

function normalizedSearch(input: string): string {
  return commandContent(input).replace(/\s+/g, "").toLowerCase();
}

function suggestionScore(item: CommandSuggestion, query: string): number {
  const command = normalizedSearch(item.command);
  const keywords = item.keywords.map(normalizeKey);
  if (command === query) return 0;
  if (keywords.includes(query)) return 1;
  if (command.startsWith(query)) return 2;
  if (command.includes(query)) return 3;
  if (keywords.some((keyword) => keyword.includes(query))) return 4;
  return 100;
}

function normalizeKey(value: string): string {
  return value.replace(/\s+/g, "").toLowerCase();
}

function optionalRange(
  args: string[],
  now: Date,
  fallback: string,
): DateRange | ParseFailure {
  if (args.length > 1) return { message: "无法识别参数“" + args.slice(1).join(" ") + "”。" };
  return dateRange(args[0] || fallback, now);
}

function exactRange(args: string[], now: Date): DateRange | ParseFailure {
  if (args.length !== 1) return { message: "日期范围只能指定一次。" };
  return dateRange(args[0] || "", now);
}

function dateRange(value: string, now: Date): DateRange | ParseFailure {
  const current = startOfDay(now);
  if (value === "今天") return singleDay(current, "今天");
  if (value === "昨天") return singleDay(addDays(current, -1), "昨天");
  if (value === "本周" || value === "上周") {
    const monday = addDays(current, -((current.getDay() + 6) % 7));
    const from = value === "上周" ? addDays(monday, -7) : monday;
    return { from: formatDate(from), to: formatDate(addDays(from, 6)), label: value };
  }
  if (value === "本月" || value === "上月") {
    const offset = value === "上月" ? -1 : 0;
    const from = new Date(current.getFullYear(), current.getMonth() + offset, 1);
    const to = new Date(from.getFullYear(), from.getMonth() + 1, 0);
    return { from: formatDate(from), to: formatDate(to), label: value };
  }
  if (value === "本年") {
    return { from: String(current.getFullYear()) + "-01-01", to: String(current.getFullYear()) + "-12-31", label: value };
  }
  const yearMatch = /^(\d{4})年$/.exec(value);
  if (yearMatch?.[1]) {
    return { from: yearMatch[1] + "-01-01", to: yearMatch[1] + "-12-31", label: value };
  }
  const monthMatch = /^(\d{4})年(\d{1,2})月$/.exec(value);
  if (monthMatch?.[1] && monthMatch[2]) {
    const year = Number(monthMatch[1]);
    const month = Number(monthMatch[2]);
    if (month < 1 || month > 12) return { message: "无法识别日期“" + value + "”。" };
    const from = new Date(year, month - 1, 1);
    const to = new Date(year, month, 0);
    return { from: formatDate(from), to: formatDate(to), label: value };
  }
  const rangeMatch = /^(\d{4}-\d{2}-\d{2})\.\.(\d{4}-\d{2}-\d{2})$/.exec(value);
  if (rangeMatch?.[1] && rangeMatch[2]) {
    const from = parseDate(rangeMatch[1]);
    const to = parseDate(rangeMatch[2]);
    if (!from || !to) return { message: "无法识别日期“" + value + "”。" };
    if (from > to) return { message: "开始日期不能晚于结束日期。" };
    return { from: rangeMatch[1], to: rangeMatch[2], label: value };
  }
  const exact = parseDate(value);
  if (exact) return singleDay(exact, value);
  return { message: "无法识别日期“" + value + "”。" };
}

function parseDate(value: string): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match?.[1] || !match[2] || !match[3]) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
    ? date
    : null;
}

function singleDay(value: Date, label: string): DateRange {
  const date = formatDate(value);
  return { from: date, to: date, label };
}

function startOfDay(value: Date): Date {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate());
}

function addDays(value: Date, days: number): Date {
  const next = new Date(value);
  next.setDate(next.getDate() + days);
  return next;
}

function formatDate(value: Date): string {
  return [
    value.getFullYear(),
    String(value.getMonth() + 1).padStart(2, "0"),
    String(value.getDate()).padStart(2, "0"),
  ].join("-");
}

function attendanceStatus(value?: string): string | null {
  return value === "待审核" ? "PENDING" : value === "未签退" ? "INCOMPLETE" : null;
}

function repairStatus(value?: string): string | null {
  if (value === "进行中") return "REPAIRING";
  if (value === "已完成") return "COMPLETED";
  if (value === "已取消" || value === "取消") return "CANCELED";
  return null;
}

function repairStatusLabel(value: string): string {
  return value === "COMPLETED" ? "已完成" : value === "CANCELED" ? "已取消" : "进行中";
}

function memberStatus(value?: string): string | null {
  return value === "启用" ? "ACTIVE" : value === "停用" ? "DISABLED" : null;
}

function weekdayValue(
  value: string | undefined,
  now: Date,
): { value: number; label: string } | null {
  if (!value) return null;
  if (value === "今天") {
    const day = now.getDay() || 7;
    return { value: day, label: "今天" };
  }
  const normalized = value.replace(/^星期|^礼拜/, "周");
  const index = "一二三四五六日".indexOf(normalized.slice(-1));
  return index >= 0
    ? { value: index + 1, label: "周" + ("一二三四五六日"[index] || "") }
    : null;
}

function displayObjectForIntent(value: string): string {
  if (value === "成员名册") return "成员";
  if (value === "培训记录") return "培训";
  return value;
}

function invalid(message: string): CommandResolution {
  return { kind: "invalid", message };
}

function extraArguments(args: string[]): CommandResolution {
  return invalid("无法识别参数“" + args.join(" ") + "”。");
}
