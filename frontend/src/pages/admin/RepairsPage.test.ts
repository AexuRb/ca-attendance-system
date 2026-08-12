import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ref } from "vue";
import RepairsPage from "./RepairsPage.vue";
import type {
  RepairCase,
  RepairPage,
  RepairStatus,
} from "../../features/repairs/repairTypes";

const apiRequest = vi.fn();
const apiGet = vi.fn();

vi.mock("../../shared/api", () => ({
  api: (...args: unknown[]) => apiRequest(...args),
  get: (...args: unknown[]) => apiGet(...args),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  downloadBlob: vi.fn(),
}));

vi.mock("../../app/session", () => ({
  useSession: () => ({
    user: ref({ id: 1, name: "管理员", role: "ADMIN" }),
  }),
}));

afterEach(() => {
  apiRequest.mockReset();
  apiGet.mockReset();
  document.body.innerHTML = "";
});

describe("RepairsPage paging", () => {
  it("loads three 30-row columns and appends only the requested status", async () => {
    apiGet.mockImplementation((url: string) => {
      if (url === "/api/repairs/handler-candidates") return Promise.resolve([]);
      return Promise.resolve([]);
    });
    apiRequest.mockImplementation((url: string) => {
      const params = new URL(url, "http://localhost").searchParams;
      const status = params.get("status") as RepairStatus;
      const page = Number(params.get("page"));
      const total = status === "REPAIRING" ? 31 : status === "COMPLETED" ? 4 : 2;
      const count = status === "REPAIRING" && page === 1 ? 30 : page === 2 ? 1 : total;
      return Promise.resolve(repairPage(status, page, count, total));
    });

    const wrapper = mount(RepairsPage, {
      global: {
        stubs: {
          AgreementDialog: true,
          AccountPicker: true,
          ConfirmDialog: true,
          ModalDialog: true,
        },
      },
    });
    await flushPromises();

    expect(requests()).toEqual([
      { status: "REPAIRING", page: "1", pageSize: "30" },
      { status: "COMPLETED", page: "1", pageSize: "30" },
      { status: "CANCELED", page: "1", pageSize: "30" },
    ]);
    expect(
      wrapper.findAll(".repair-column-head b").map((item) => item.text()),
    ).toEqual(["31", "4", "2"]);
    expect(wrapper.findAll(".repair-card")).toHaveLength(36);

    await wrapper.find(".repair-column-more button").trigger("click");
    await flushPromises();

    expect(requests().at(-1)).toEqual({
      status: "REPAIRING",
      page: "2",
      pageSize: "30",
    });
    expect(wrapper.findAll(".repair-card")).toHaveLength(37);
  });
});

function requests() {
  return apiRequest.mock.calls.map(([url]) => {
    const params = new URL(String(url), "http://localhost").searchParams;
    return {
      status: params.get("status"),
      page: params.get("page"),
      pageSize: params.get("pageSize"),
    };
  });
}

function repairPage(
  status: RepairStatus,
  page: number,
  count: number,
  total: number,
): RepairPage {
  return {
    items: Array.from({ length: count }, (_, index) =>
      repairCase((page - 1) * 30 + index + statusOffset(status), status),
    ),
    total,
    page,
    pageSize: 30,
    hasMore: page * 30 < total,
  };
}

function repairCase(id: number, status: RepairStatus): RepairCase {
  return {
    id,
    caseNo: `PAGE-${status}-${id}`,
    agreementType: "PERSONAL_DEVICE",
    ownerName: `送修人${id}`,
    ownerPhone: "13800000000",
    deviceType: "笔记本电脑",
    faultDescription: "无法开机",
    dataBackupConfirmed: true,
    riskAcknowledged: true,
    privacyAcknowledged: true,
    status,
    receivedAt: "2026-08-12T14:00:00",
    updatedAt: "2026-08-12T14:00:00",
  };
}

function statusOffset(status: RepairStatus) {
  if (status === "COMPLETED") return 10_000;
  if (status === "CANCELED") return 20_000;
  return 1;
}
