import { describe, expect, it } from "vitest";
import {
  auditActionLabel,
  auditTargetLabel,
  buildAuditDiff,
} from "./logDisplay";

describe("audit log display", () => {
  it("translates compound operation and target names", () => {
    expect(auditActionLabel("CREATE_DUTY_SCHEDULE")).toBe("新增排班");
    expect(auditActionLabel("UPDATE_ATTENDANCE_POLICY")).toBe(
      "调整有效时长规则",
    );
    expect(auditActionLabel("UPDATE_UI_APPEARANCE")).toBe("切换全局界面");
    expect(
      auditTargetLabel({ targetType: "duty_schedule_slots", targetId: 12 }),
    ).toBe("固定排班 #12");
    expect(auditActionLabel("EXPORT_DATA")).toBe("导出业务数据");
    expect(auditActionLabel("LOCAL_LOGIN_LOCKED")).toBe("本机登录锁定");
    expect(auditActionLabel("REMOTE_LOGIN_LOCKED")).toBe("远程登录锁定");
    expect(auditTargetLabel({ targetType: "authentication" })).toBe(
      "登录认证",
    );
    expect(auditTargetLabel({ targetType: "data_exports" })).toBe(
      "数据导出",
    );
    expect(auditActionLabel("BULK_REVIEW_ATTENDANCE")).toBe("批量审核值班记录");
    expect(auditActionLabel("ATTENDANCE_STATS")).toBe("导出值班统计");
    expect(auditActionLabel("CUSTOM_MEMBERS")).toBe("自定义导出");
    expect(auditActionLabel("UNKNOWN_ACTION")).toBe("业务操作");
  });

  it("renders export audit filters and details in readable language", () => {
    const rows = buildAuditDiff(
      undefined,
      JSON.stringify({
        exportType: "TRAINING_SUMMARY",
        operatorRole: "ADMIN",
        filters: { from: "2026-08-01", to: "2026-08-10" },
        rows: 12,
        details: { sessionRows: 5, memberRows: 7 },
        filename: "培训统计.xlsx",
      }),
    );

    expect(rows).toContainEqual({
      key: "filters",
      label: "筛选条件",
      before: "—",
      after: "开始日期：2026-08-01；结束日期：2026-08-10",
    });
    expect(rows).toContainEqual({
      key: "details",
      label: "导出详情",
      before: "—",
      after: "培训场次行数：5；成员统计行数：7",
    });
  });

  it("labels attendance policy changes in plain language", () => {
    expect(
      buildAuditDiff(
        JSON.stringify({ requireDutyDay: false, requireDutyPeriod: false }),
        JSON.stringify({ requireDutyDay: true, requireDutyPeriod: false }),
      ),
    ).toEqual([
      {
        key: "requireDutyDay",
        label: "强制值班日",
        before: "否",
        after: "是",
      },
    ]);
  });

  it("shows only changed fields for an update", () => {
    const rows = buildAuditDiff(
      JSON.stringify({ title: "值班", location: "活动室", enabled: true }),
      JSON.stringify({ title: "部长值班", location: "活动室", enabled: true }),
    );

    expect(rows).toEqual([
      {
        key: "title",
        label: "排班名称",
        before: "值班",
        after: "部长值班",
      },
    ]);
  });

  it("formats time arrays and assignee lists for created records", () => {
    const rows = buildAuditDiff(
      undefined,
      JSON.stringify({
        startTime: [16, 0],
        endTime: [18, 0],
        assignees: [{ name: "陈晨" }, { name: "林舟" }],
      }),
    );

    expect(rows).toEqual([
      { key: "startTime", label: "开始时间", before: "—", after: "16:00" },
      { key: "endTime", label: "结束时间", before: "—", after: "18:00" },
      {
        key: "assignees",
        label: "值班人员",
        before: "—",
        after: "陈晨、林舟",
      },
    ]);
  });

  it("keeps historical Jackson date arrays readable", () => {
    const rows = buildAuditDiff(
      undefined,
      JSON.stringify({
        trainingDate: [2026, 8, 21],
        receivedAt: [2026, 8, 21, 14, 30],
      }),
    );

    expect(rows).toEqual([
      {
        key: "trainingDate",
        label: "培训日期",
        before: "—",
        after: "2026-08-21",
      },
      {
        key: "receivedAt",
        label: "受理时间",
        before: "—",
        after: "2026-08-21 14:30",
      },
    ]);
  });

  it("omits duplicate and technical metadata from a business diff", () => {
    const rows = buildAuditDiff(
      undefined,
      JSON.stringify({
        id: 12,
        weekday: 6,
        weekdayName: "星期六",
        dutyDate: null,
        title: "部长值班",
        createdByName: "系统管理员",
        createdAt: [2026, 8, 9, 21, 55, 39],
      }),
    );

    expect(rows).toEqual([
      { key: "weekdayName", label: "星期", before: "—", after: "星期六" },
      { key: "title", label: "排班名称", before: "—", after: "部长值班" },
    ]);
  });
});
