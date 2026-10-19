import { describe, expect, it } from "vitest";
import { commandObjects, commandTreePaths, commandShortcuts } from "./commandCatalog";
import { resolveCommand } from "./commandParser";
import { commandInputView } from "./commandTree";
const now = new Date(2026, 9, 15, 12);
describe("data center command handoff", () => {
  it.each([["成员名册", "members"], ["排班管理", "schedules"], ["培训记录", "training"]])("opens the central %s import without a legacy page", (object, kind) => {
    expect(resolveCommand(`/ 导入 ${object}`, "PRESIDENT", now)).toMatchObject({ kind:"resolved", execution:"prefill", target:{name:"data", query:{tab:"import",kind}} });
  });
  it.each([["数据中心","import"], ["数据与备份","import"], ["数据导入","import"], ["数据导出","export"], ["自定义导出","export"], ["自定义 Excel","export"], ["备份与恢复","backups"]])("opens %s in its current mode", (object, tab) => {
    expect(resolveCommand(`/ 打开 ${object}`, "ADMIN", now)).toMatchObject({kind:"resolved",target:{name:"data",query:{tab}}});
  });
  it.each([["培训记录","training","PRESIDENT"], ["操作日志","logs","ADMIN"]] as const)("preserves dates for %s exports", (object, source, role) => {
    expect(resolveCommand(`/ 导出 ${object} 2026-08-01..2026-08-28`, role, now)).toMatchObject({kind:"resolved",execution:"confirm",target:{name:"data",query:{tab:"export",source,from:"2026-08-01",to:"2026-08-28"}}});
  });
  it.each([["进行中","REPAIRING"],["已完成","COMPLETED"],["已取消","CANCELED"]])("preserves repair status %s", (label,status) => {
    expect(resolveCommand(`/ 导出 维修事务 ${label} 本月`,"PRESIDENT",now)).toMatchObject({kind:"resolved",target:{name:"data",query:{tab:"export",source:"repairs",status,from:"2026-10-01",to:"2026-10-31"}}});
  });
  it("retains backup creation confirmation and rejects invalid ranges", () => {
    expect(resolveCommand("/ 导出 统计汇总 本周","PRESIDENT",now)).toMatchObject({kind:"invalid"});
    expect(resolveCommand("/ 查看 数据统计 本周","PRESIDENT",now)).toMatchObject({kind:"resolved",target:{name:"stats"}});
    expect(resolveCommand("/ 创建 备份","PRESIDENT",now)).toMatchObject({kind:"resolved",execution:"confirm",target:{name:"data",query:{tab:"backups",intent:"create-backup"}}});
    expect(resolveCommand("/ 导出 数据统计 2026-08-28..2026-08-01","ADMIN",now).kind).toBe("invalid");
  });
  it("keeps minister export deferred and president log export unavailable", () => {
    expect(resolveCommand("/ 导出 数据统计 本周","MINISTER",now).kind).toBe("forbidden");
    expect(resolveCommand("/ 新建 值班记录","MINISTER",now).kind).toBe("forbidden");
    expect(commandInputView("/ 导出 ","MINISTER").suggestions).toEqual([]);
    expect(resolveCommand("/ 导出 操作日志 今天","PRESIDENT",now).kind).toBe("forbidden");
    expect(resolveCommand("/ 导入 成员名册","MINISTER",now).kind).toBe("forbidden");
  });
  it("keeps every registered path executable only for its declared roles", () => {
    for (const item of [...commandTreePaths,...commandShortcuts]) for (const role of item.roles) {
      const command = /^\/ (查找|预览) \S+$/.test(item.command) ? item.command + " 测试编号" : item.command;
      expect(resolveCommand(command,role,now).kind, `${role}: ${command}`).toBe("resolved");
    }
    const objectNames=commandObjects.flatMap(object=>[object.canonical,...object.aliases]);
    expect(new Set(objectNames).size).toBe(objectNames.length);
  });
});
