import { describe, expect, it, vi } from "vitest";
import {
  parseRepairWorkspaceQuery,
  serializeRepairWorkspaceQuery,
  useRepairWorkspace,
} from "./useRepairWorkspace";
import type {
  RepairCase,
  RepairFilters,
  RepairPage,
  RepairStatus,
  RepairStatusCounts,
} from "./repairTypes";

const defaults: Pick<RepairFilters, "from" | "to"> = {
  from: "2026-01-01",
  to: "2026-12-31",
};

describe("repair workspace", () => {
  it("keeps draft filters out of paging, status switches and mutation refreshes", async () => {
    const loadPage = vi.fn(async ({ status, page }) => repairPage(status, page, 2, 42));
    const workspace = useRepairWorkspace({ defaults, loadPage });
    await workspace.initialize();
    workspace.filters.keyword = "未提交条件";
    workspace.filters.from = "2099-01-01";
    await workspace.setPage(2);
    await workspace.setStatus("COMPLETED");
    await workspace.refreshAfterMutation();
    expect(loadPage.mock.calls.slice(1).every(([request]) => JSON.stringify(request.filters) === JSON.stringify({ keyword: "", ...defaults }))).toBe(true);
    expect(workspace.page.page).toBe(1);
    expect(workspace.filtersPending.value).toBe(true);
    expect(workspace.currentQuery().from).toBe(defaults.from);
  });

  it("keeps the result, counts and URL unchanged on failure and retries the submitted snapshot", async () => {
    const loadPage = vi.fn(async ({ status, page }) => repairPage(status, page, 2, 42));
    const onQueryChange = vi.fn();
    const workspace = useRepairWorkspace({ defaults, loadPage, onQueryChange });
    await workspace.initialize();
    const previousItems = [...workspace.page.items];
    const previousCounts = { ...workspace.counts };
    workspace.filters.keyword = "已提交关键词";
    workspace.filters.from = "2026-03-01";
    loadPage.mockRejectedValueOnce(new Error("查询失败"));
    await workspace.applyFilters();
    expect(workspace.page.items).toEqual(previousItems);
    expect(workspace.counts).toEqual(previousCounts);
    expect(workspace.appliedFilters.from).toBe(defaults.from);
    expect(onQueryChange).toHaveBeenCalledTimes(1);
    workspace.filters.keyword = "后来修改的草稿";
    await workspace.retry();
    expect(loadPage.mock.calls.at(-1)?.[0].filters.keyword).toBe("已提交关键词");
    expect(workspace.appliedFilters.keyword).toBe("已提交关键词");
    expect(onQueryChange.mock.calls.at(-1)?.[1]).toBe("push");
    expect(workspace.currentQuery()).not.toHaveProperty("keyword");
  });

  it("does not relabel the old rows as a failed status and does not lose the keyword on URL restoration", async () => {
    const loadPage = vi.fn(async ({ status, page }) => repairPage(status, page, 2, 42));
    const workspace = useRepairWorkspace({ defaults, initialQuery: { keyword: "笔记本" }, loadPage });
    await workspace.initialize();
    loadPage.mockRejectedValueOnce(new Error("切换失败"));
    await workspace.setStatus("CANCELED");
    expect(workspace.activeStatus.value).toBe("REPAIRING");
    expect(workspace.page.items[0]?.status).toBe("REPAIRING");
    await workspace.retry();
    expect(workspace.activeStatus.value).toBe("CANCELED");
    await workspace.restoreQuery({ status: "COMPLETED", page: "2" });
    expect(loadPage.mock.calls.at(-1)?.[0].filters.keyword).toBe("笔记本");
  });

  it("loads only the active status while receiving all filtered counts", async () => {
    const calls: Array<{ status: RepairStatus; page: number; pageSize: number }> = [];
    const queries: Array<{
      query: Record<string, string>;
      mode: "push" | "replace";
    }> = [];
    const workspace = useRepairWorkspace({
      defaults,
      loadPage: async ({ status, page, pageSize }) => {
        calls.push({ status, page, pageSize });
        return repairPage(status, page, 2, 7, {
          REPAIRING: 7,
          COMPLETED: 48,
          CANCELED: 3,
        });
      },
      onQueryChange: (query, mode) => queries.push({ query, mode }),
    });

    await workspace.initialize();

    expect(calls).toEqual([{ status: "REPAIRING", page: 1, pageSize: 20 }]);
    expect(workspace.page.items).toHaveLength(2);
    expect(workspace.counts).toEqual({
      REPAIRING: 7,
      COMPLETED: 48,
      CANCELED: 3,
    });
    expect(queries.at(-1)).toEqual({
      mode: "replace",
      query: {
        status: "REPAIRING",
        from: defaults.from,
        to: defaults.to,
      },
    });
  });

  it("switches and pages only the selected status", async () => {
    const calls: Array<{ status: RepairStatus; page: number }> = [];
    const modes: Array<"push" | "replace"> = [];
    const workspace = useRepairWorkspace({
      defaults,
      loadPage: async ({ status, page }) => {
        calls.push({ status, page });
        return repairPage(status, page, 2, 42);
      },
      onQueryChange: (_query, mode) => modes.push(mode),
    });
    await workspace.initialize();
    calls.length = 0;

    await workspace.setStatus("COMPLETED");
    await workspace.setPage(2);

    expect(calls).toEqual([
      { status: "COMPLETED", page: 1 },
      { status: "COMPLETED", page: 2 },
    ]);
    expect(workspace.activeStatus.value).toBe("COMPLETED");
    expect(workspace.page.page).toBe(2);
    expect(workspace.page.items.every((item) => item.status === "COMPLETED")).toBe(true);
    expect(modes).toEqual(["replace", "push", "push"]);
  });

  it("does not allow a late response from the previous status to overwrite the current one", async () => {
    const pending: Array<{
      status: RepairStatus;
      resolve: (page: RepairPage) => void;
    }> = [];
    const workspace = useRepairWorkspace({
      defaults,
      loadPage: ({ status }) =>
        new Promise((resolve) => pending.push({ status, resolve })),
    });

    const initializing = workspace.initialize();
    await Promise.resolve();
    const switching = workspace.setStatus("COMPLETED");
    await Promise.resolve();

    pending
      .find((request) => request.status === "COMPLETED")
      ?.resolve(repairPage("COMPLETED", 1, 1, 1, undefined, 200));
    await switching;
    pending
      .find((request) => request.status === "REPAIRING")
      ?.resolve(repairPage("REPAIRING", 1, 1, 1, undefined, 100));
    await initializing;

    expect(workspace.activeStatus.value).toBe("COMPLETED");
    expect(workspace.page.items[0]?.id).toBe(201);
  });

  it("aborts and ignores a page response after disposal", async () => {
    let resolveRequest!: (page: RepairPage) => void;
    let signal!: AbortSignal;
    const workspace = useRepairWorkspace({
      defaults,
      loadPage: ({ signal: requestSignal }) => {
        signal = requestSignal;
        return new Promise((resolve) => {
          resolveRequest = resolve;
        });
      },
    });

    const initialization = workspace.initialize();
    await Promise.resolve();
    workspace.dispose();

    expect(signal.aborted).toBe(true);
    resolveRequest(repairPage("REPAIRING", 1, 1, 1));
    await initialization;

    expect(workspace.page.items).toEqual([]);
    expect(workspace.counts).toEqual({
      REPAIRING: 0,
      COMPLETED: 0,
      CANCELED: 0,
    });
  });

  it("restores filters, status and page from the URL query", async () => {
    const calls: Array<{ status: RepairStatus; page: number; keyword: string }> = [];
    const workspace = useRepairWorkspace({
      defaults,
      initialQuery: {
        status: "CANCELED",
        page: "3",
        keyword: " 显卡 ",
        from: "2026-03-01",
        to: "2026-08-01",
      },
      loadPage: async ({ status, page, filters }) => {
        calls.push({ status, page, keyword: filters.keyword });
        return repairPage(status, page, 3, 63);
      },
    });

    await workspace.initialize();

    expect(calls).toEqual([{ status: "CANCELED", page: 3, keyword: "显卡" }]);
    expect(workspace.filters).toEqual({
      keyword: "显卡",
      from: "2026-03-01",
      to: "2026-08-01",
    });
    expect(workspace.currentQuery()).toEqual({
      status: "CANCELED",
      page: "3",
      from: "2026-03-01",
      to: "2026-08-01",
    });
  });

  it("falls back to the last available page after data shrinks", async () => {
    const calls: number[] = [];
    const workspace = useRepairWorkspace({
      defaults,
      initialQuery: { status: "COMPLETED", page: "3" },
      loadPage: async ({ status, page }) => {
        calls.push(page);
        if (page === 3) return repairPage(status, 3, 0, 35);
        return repairPage(status, 2, 15, 35);
      },
    });

    await workspace.initialize();

    expect(calls).toEqual([3, 2]);
    expect(workspace.page.page).toBe(2);
    expect(workspace.page.items).toHaveLength(15);
  });

  it("refreshes the current page and all counts after a cross-status mutation", async () => {
    let afterMutation = false;
    const calls: RepairStatus[] = [];
    const workspace = useRepairWorkspace({
      defaults,
      loadPage: async ({ status, page }) => {
        calls.push(status);
        return repairPage(status, page, 1, afterMutation ? 4 : 5, afterMutation
          ? { REPAIRING: 4, COMPLETED: 9, CANCELED: 1 }
          : { REPAIRING: 5, COMPLETED: 8, CANCELED: 1 });
      },
    });
    await workspace.initialize();
    afterMutation = true;

    await workspace.refreshAfterMutation("REPAIRING", "COMPLETED");

    expect(calls).toEqual(["REPAIRING", "REPAIRING"]);
    expect(workspace.counts).toEqual({
      REPAIRING: 4,
      COMPLETED: 9,
      CANCELED: 1,
    });
  });
});

describe("repair workspace query", () => {
  it("normalizes unsupported status and invalid page values", () => {
    expect(
      parseRepairWorkspaceQuery(
        { status: "UNKNOWN", page: "-2", keyword: [" 主板 "] },
        defaults,
      ),
    ).toEqual({
      status: "REPAIRING",
      page: 1,
      keyword: "主板",
      from: defaults.from,
      to: defaults.to,
    });
    expect(
      serializeRepairWorkspaceQuery({
        status: "COMPLETED",
        page: 2,
        keyword: " 维修 ",
        from: defaults.from,
        to: defaults.to,
      }),
    ).toEqual({
      status: "COMPLETED",
      page: "2",
      from: defaults.from,
      to: defaults.to,
    });
  });
});

function repairPage(
  status: RepairStatus,
  page: number,
  count: number,
  total: number,
  statusCounts: RepairStatusCounts = {
    REPAIRING: status === "REPAIRING" ? total : 0,
    COMPLETED: status === "COMPLETED" ? total : 0,
    CANCELED: status === "CANCELED" ? total : 0,
  },
  offset = 0,
): RepairPage {
  return {
    items: Array.from({ length: count }, (_, index) =>
      repairCase(offset + (page - 1) * 20 + index + 1, status),
    ),
    total,
    page,
    pageSize: 20,
    hasMore: page * 20 < total,
    statusCounts,
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
