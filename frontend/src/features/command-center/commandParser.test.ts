import { describe, expect, it } from "vitest";
import {
  commandActions,
  commandShortcuts,
  commandTreePaths,
} from "./commandCatalog";
import {
  resolveCommand,
  suggestCommands,
} from "./commandParser";

const now = new Date(2026, 7, 28, 10, 0, 0);

describe("command center parser", () => {
  it("normalizes aliases and creates a weekly attendance route", () => {
    expect(resolveCommand("/ 查看 考勤 本周", "MINISTER", now)).toEqual({
      kind: "resolved",
      canonical: "/ 查看 值班记录 本周",
      feedback: "已打开本周值班记录",
      execution: "navigate",
      target: {
        name: "attendance",
        query: { from: "2026-08-24", to: "2026-08-30" },
      },
    });
  });

  it("blocks a minister from leader-only modules", () => {
    expect(resolveCommand("/ 打开 成员名册", "MINISTER", now)).toEqual({
      kind: "forbidden",
      message: "当前角色无权使用“成员名册”。",
    });
  });

  it("never executes dangerous commands", () => {
    expect(resolveCommand("/ 删除 成员 1001", "ADMIN", now)).toEqual({
      kind: "dangerous",
      message: "命令台不直接执行该操作，请前往对应页面核对。",
    });
  });

  it("resolves shortcut commands and safe prefill intents", () => {
    expect(resolveCommand("/ 维修", "MINISTER", now)).toMatchObject({
      kind: "resolved",
      canonical: "/ 打开 维修事务",
      target: { name: "repairs", query: { status: "REPAIRING" } },
    });
    expect(resolveCommand("/ 新建 维修事务", "MINISTER", now)).toMatchObject({
      kind: "resolved",
      execution: "prefill",
      target: { name: "repairs", query: { status: "REPAIRING", intent: "new" } },
    });
  });

  it.each([
    ["/ 未签退", "MINISTER", "attendance", { status: "INCOMPLETE" }],
    ["/ 待审核", "MINISTER", "reviews", undefined],
    ["/ 本周统计", "MINISTER", "stats", { from: "2026-08-24", to: "2026-08-30" }],
    ["/ 进行中维修", "MINISTER", "repairs", { status: "REPAIRING" }],
    ["/ 今日排班", "PRESIDENT", "schedules", { weekday: "5" }],
    ["/ 本月培训", "PRESIDENT", "trainings", { from: "2026-08-01", to: "2026-08-31" }],
    ["/ 备份管理", "PRESIDENT", "data", { tab: "backups" }],
    ["/ 回收站", "ADMIN", "data", { tab: "recycle" }],
  ] as const)("resolves the expanded shortcut %s", (input, role, name, query) => {
    const resolution = resolveCommand(input, role, now);
    expect(resolution).toMatchObject({ kind: "resolved", target: { name } });
    if (query) expect(resolution).toMatchObject({ target: { query } });
  });

  it("keeps shortcut permissions identical to their canonical commands", () => {
    expect(resolveCommand("/ 今日排班", "MINISTER", now)).toEqual({
      kind: "forbidden",
      message: "当前角色无权使用“排班管理”。",
    });
    expect(resolveCommand("/ 回收站", "PRESIDENT", now)).toEqual({
      kind: "forbidden",
      message: "当前角色无权使用“维修回收站”。",
    });
  });

  it("parses repair state and explicit date ranges", () => {
    expect(resolveCommand("/ 导出 维修事务 已完成 2026-08-01..2026-08-28", "PRESIDENT", now)).toMatchObject({
      kind: "resolved",
      execution: "confirm",
      target: {
        name: "repairs",
        query: {
          status: "COMPLETED",
          from: "2026-08-01",
          to: "2026-08-28",
          intent: "export",
        },
      },
    });
  });

  it("parses expanded ranges, statuses, weekdays and exports", () => {
    expect(resolveCommand("/ 查看 值班记录 上月", "MINISTER", now)).toMatchObject({
      kind: "resolved",
      target: { name: "attendance", query: { from: "2026-07-01", to: "2026-07-31" } },
    });
    expect(resolveCommand("/ 查看 数据统计 本年", "MINISTER", now)).toMatchObject({
      kind: "resolved",
      target: { name: "stats", query: { from: "2026-01-01", to: "2026-12-31" } },
    });
    expect(resolveCommand("/ 查看 维修事务 已取消 上月", "MINISTER", now)).toMatchObject({
      kind: "resolved",
      target: {
        name: "repairs",
        query: { status: "CANCELED", from: "2026-07-01", to: "2026-07-31" },
      },
    });
    expect(resolveCommand("/ 查看 排班管理 周日", "PRESIDENT", now)).toMatchObject({
      kind: "resolved",
      target: { name: "schedules", query: { weekday: "7" } },
    });
    expect(resolveCommand("/ 查看 成员名册 停用", "PRESIDENT", now)).toMatchObject({
      kind: "resolved",
      target: { name: "members", query: { status: "DISABLED" } },
    });
    expect(resolveCommand("/ 导出 培训记录 本年", "PRESIDENT", now)).toMatchObject({
      kind: "resolved",
      execution: "confirm",
      target: {
        name: "trainings",
        query: { from: "2026-01-01", to: "2026-12-31", intent: "export" },
      },
    });
    expect(resolveCommand("/ 导出 操作日志 今天", "ADMIN", now)).toMatchObject({
      kind: "resolved",
      execution: "confirm",
      target: {
        name: "logs",
        query: { from: "2026-08-28", to: "2026-08-28", intent: "export" },
      },
    });
  });

  it("keeps catalog identifiers and shortcut keywords unique", () => {
    const pathIds = commandTreePaths.map((item) => item.id);
    const shortcutIds = commandShortcuts.map((item) => item.id);
    const keywords = commandShortcuts.map((item) => item.keyword);

    expect(new Set(pathIds).size).toBe(pathIds.length);
    expect(new Set(shortcutIds).size).toBe(shortcutIds.length);
    expect(new Set(keywords).size).toBe(keywords.length);
    expect(keywords.filter((keyword) => commandActions.some((action) => action === keyword)))
      .toEqual(["设置"]);
  });

  it("filters suggestions before ranking them", () => {
    expect(
      suggestCommands("/ 日志", "PRESIDENT").some((item) => item.id === "logs"),
    ).toBe(false);
    expect(
      suggestCommands("/ 日志", "ADMIN")[0]?.id,
    ).toBe("logs");
  });

  it("rejects inverted or malformed date ranges", () => {
    expect(
      resolveCommand("/ 查看 数据统计 2026-08-28..2026-08-01", "ADMIN", now),
    ).toEqual({
      kind: "invalid",
      message: "开始日期不能晚于结束日期。",
    });
    expect(
      resolveCommand("/ 查看 数据统计 2026-02-30", "ADMIN", now),
    ).toEqual({
      kind: "invalid",
      message: "无法识别日期“2026-02-30”。",
    });
  });
});
