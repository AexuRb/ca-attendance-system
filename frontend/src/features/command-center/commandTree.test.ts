import { describe, expect, it } from "vitest";
import { commandInputView, completeCommandInput } from "./commandTree";

describe("command tree", () => {
  it("shows only allowed root actions for the current role", () => {
    const minister = commandInputView("/", "MINISTER");
    const admin = commandInputView("/", "ADMIN");

    expect(minister.suggestions.map((item) => item.label)).toContain("查看");
    expect(minister.suggestions.map((item) => item.label)).toContain("查找");
    expect(minister.suggestions.map((item) => item.label)).toContain("预览");
    expect(minister.suggestions.map((item) => item.label)).not.toContain("设置");
    expect(admin.suggestions.map((item) => item.label)).toContain("设置");
    expect(admin.suggestions.map((item) => item.label)).not.toContain("未签退");
  });

  it("reveals matching shortcuts only after typing their prefix", () => {
    const view = commandInputView("/ 未", "MINISTER");

    expect(view.prompt).toBe("选择命令或快捷词");
    expect(view.path).toEqual(["快捷命令"]);
    expect(view.state).toBe("incomplete");
    expect(view.suggestions.map((item) => item.label)).toContain("未签退");
    expect(view.suggestions.find((item) => item.label === "未签退")?.description)
      .toBe("筛选未签退记录");
  });

  it("only suggests direct children of the active path", () => {
    const view = commandInputView("/ 查看 ", "ADMIN");

    expect(view.path).toEqual(["查看", "选择功能"]);
    expect(view.state).toBe("incomplete");
    expect(view.suggestions.map((item) => item.label)).toContain("值班记录");
    expect(view.suggestions.map((item) => item.label)).not.toContain("本周");
  });

  it("offers all registered attendance ranges and states", () => {
    const view = commandInputView("/ 查看 值班记录 ", "MINISTER");
    const labels = view.suggestions.map((item) => item.label);

    expect(labels).toEqual(expect.arrayContaining([
      "今天",
      "昨天",
      "本周",
      "上周",
      "本月",
      "上月",
      "待审核",
      "未签退",
    ]));
  });

  it("continues repair status with a date range", () => {
    const view = commandInputView("/ 查看 维修事务 已完成 ", "MINISTER");

    expect(view.state).toBe("extensible");
    expect(view.statusMessage).toBe("可以执行，也可继续补充范围");
    expect(view.suggestions.map((item) => item.label)).toEqual(expect.arrayContaining([
      "今天",
      "本周",
      "本月",
      "本年",
    ]));
    expect(view.suggestions.find((item) => item.label === "本月")?.description)
      .toBe("查看已完成的本月维修");
  });

  it("distinguishes executable commands from extensible commands", () => {
    const executable = commandInputView("/ 查看 数据统计 本周", "MINISTER");
    const extensible = commandInputView("/ 查看 维修事务 已完成", "MINISTER");
    const invalid = commandInputView("/ 不存在", "ADMIN");

    expect(executable.state).toBe("executable");
    expect(executable.statusMessage).toBe("可以执行");
    expect(extensible.state).toBe("extensible");
    expect(invalid.state).toBe("invalid");
    expect(invalid.statusMessage).toContain("无法识别动作");
  });

  it("shows leader-only weekdays and export parameters to allowed roles", () => {
    const schedule = commandInputView("/ 查看 排班管理 ", "PRESIDENT");
    const training = commandInputView("/ 导出 培训记录 ", "PRESIDENT");
    const ministerSchedule = commandInputView("/ 查看 排班管理 ", "MINISTER");

    expect(schedule.suggestions.map((item) => item.label)).toEqual(expect.arrayContaining([
      "今天",
      "周一",
      "周日",
    ]));
    expect(training.suggestions.map((item) => item.label)).toEqual(["本月", "上月", "本年"]);
    expect(ministerSchedule.suggestions).toEqual([]);
  });

  it("completes the active token without executing the command", () => {
    const view = commandInputView("/ 查", "ADMIN");
    const suggestion = view.suggestions.find((item) => item.label === "查看");

    expect(suggestion).toBeDefined();
    expect(completeCommandInput("/ 查", suggestion!, 3)).toEqual({
      value: "/ 查看 ",
      caret: 5,
    });
  });

  it("keeps a readable space after the slash entry", () => {
    const view = commandInputView("/", "ADMIN");
    const open = view.suggestions.find((item) => item.label === "打开");

    expect(completeCommandInput("/", open!, 1).value).toBe("/ 打开 ");
  });

  it("keeps ordinary text in function search mode", () => {
    const view = commandInputView("维修", "MINISTER");

    expect(view.mode).toBe("search");
    expect(view.suggestions[0]?.label).toContain("维修事务");
  });

  it("leaves manual search parameters ready for private input", () => {
    const view = commandInputView("/ 查找 ", "MINISTER");
    const attendance = view.suggestions.find((item) => item.label === "值班记录");

    expect(attendance?.appendSpace).toBe(true);
    expect(completeCommandInput("/ 查找 ", attendance!, 5).value).toBe("/ 查找 值班记录 ");
  });
});
