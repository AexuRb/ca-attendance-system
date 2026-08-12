import { describe, expect, it } from "vitest";
import { useRepairBoard } from "./useRepairBoard";
import type {
  RepairCase,
  RepairFilters,
  RepairPage,
  RepairStatus,
} from "./repairTypes";

const filters: RepairFilters = {
  keyword: "",
  from: "2026-01-01",
  to: "2026-12-31",
};

describe("repair board paging", () => {
  it("loads and appends each status independently", async () => {
    const calls: Array<{ status: RepairStatus; page: number }> = [];
    const board = useRepairBoard(async ({ status, page }) => {
      calls.push({ status, page });
      return repairPage(status, page, page === 1 ? 30 : 5, 35);
    });

    await board.loadAll(filters);
    await board.loadMore("REPAIRING");

    expect(board.columns.REPAIRING.items).toHaveLength(35);
    expect(board.columns.REPAIRING.page).toBe(2);
    expect(board.columns.REPAIRING.hasMore).toBe(false);
    expect(board.columns.COMPLETED.items).toHaveLength(30);
    expect(calls.filter((call) => call.status === "REPAIRING")).toEqual([
      { status: "REPAIRING", page: 1 },
      { status: "REPAIRING", page: 2 },
    ]);
  });

  it("does not let an older filter response overwrite a newer query", async () => {
    const pending: Array<{
      keyword: string;
      resolve: (value: RepairPage) => void;
    }> = [];
    const board = useRepairBoard(
      ({ status, filters: requestFilters }) =>
        new Promise((resolve) => {
          pending.push({ keyword: requestFilters.keyword, resolve });
        }),
    );

    const first = board.loadAll({ ...filters, keyword: "旧条件" });
    const second = board.loadAll({ ...filters, keyword: "新条件" });
    pending
      .filter((item) => item.keyword === "新条件")
      .forEach((item) => item.resolve(repairPage("REPAIRING", 1, 1, 1, 200)));
    await second;
    pending
      .filter((item) => item.keyword === "旧条件")
      .forEach((item) => item.resolve(repairPage("REPAIRING", 1, 1, 1, 100)));
    await first;

    expect(board.columns.REPAIRING.items[0]?.id).toBe(201);
  });

  it("refreshes only affected columns and retains the loaded page count", async () => {
    const calls: Array<{ status: RepairStatus; page: number }> = [];
    const board = useRepairBoard(async ({ status, page }) => {
      calls.push({ status, page });
      return repairPage(status, page, 2, 4, 0, 2);
    }, 2);
    await board.loadAll(filters);
    await board.loadMore("COMPLETED");
    calls.length = 0;

    await board.refresh(["REPAIRING", "COMPLETED"], filters);

    expect(calls).toEqual([
      { status: "REPAIRING", page: 1 },
      { status: "COMPLETED", page: 1 },
      { status: "COMPLETED", page: 2 },
    ]);
    expect(board.columns.COMPLETED.items).toHaveLength(4);
    expect(board.columns.CANCELED.items).toHaveLength(2);
  });
});

function repairPage(
  status: RepairStatus,
  page: number,
  count: number,
  total: number,
  offset = 0,
  pageSize = 30,
): RepairPage {
  return {
    items: Array.from({ length: count }, (_, index) =>
      repairCase(offset + (page - 1) * pageSize + index + 1, status),
    ),
    total,
    page,
    pageSize,
    hasMore: page * pageSize < total,
  };
}

function repairCase(id: number, status: RepairStatus): RepairCase {
  return {
    id,
    caseNo: `TEST-${id}`,
    agreementType: "PERSONAL_DEVICE",
    ownerName: `成员${id}`,
    ownerPhone: "13800000000",
    deviceType: "笔记本电脑",
    faultDescription: "测试故障",
    dataBackupConfirmed: true,
    riskAcknowledged: true,
    privacyAcknowledged: true,
    status,
    receivedAt: "2026-08-12T14:00:00",
  };
}
