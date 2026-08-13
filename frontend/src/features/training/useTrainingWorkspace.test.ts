import { describe, expect, it } from "vitest";
import {
  parseTrainingWorkspaceQuery,
  serializeTrainingWorkspaceQuery,
  useTrainingWorkspace,
} from "./useTrainingWorkspace";
import type {
  TrainingPage,
  TrainingParticipant,
  TrainingSession,
} from "./trainingTypes";

const defaults = { from: "2026-01-01", to: "2026-08-13" };

describe("training workspace route state", () => {
  it("restores valid URL values and normalizes invalid page numbers", () => {
    const state = parseTrainingWorkspaceQuery(
      {
        sessionId: "42",
        sessionPage: "3",
        participantPage: "oops",
        keyword: "  系统维护  ",
        from: "2026-03-01",
        to: "2026-06-30",
      },
      defaults,
    );

    expect(state).toEqual({
      sessionId: 42,
      sessionPage: 3,
      participantPage: 1,
      participantKeyword: "",
      keyword: "系统维护",
      from: "2026-03-01",
      to: "2026-06-30",
    });
    expect(serializeTrainingWorkspaceQuery(state)).toEqual({
      sessionId: "42",
      sessionPage: "3",
      keyword: "系统维护",
      from: "2026-03-01",
      to: "2026-06-30",
    });
  });

  it("keeps participant search independent from the session directory keyword", async () => {
    const participantKeywords: string[] = [];
    const workspace = useTrainingWorkspace({
      defaults,
      initialQuery: {
        keyword: "场次条件",
        participantKeyword: "名单条件",
      },
      loadSessions: async () => pageResult([session(5)], 1, 1, 20),
      loadParticipants: async ({ keyword }) => {
        participantKeywords.push(keyword);
        return pageResult([], 0, 1, 20);
      },
    });

    await workspace.initialize();
    workspace.participantKeyword.value = "新名单条件";
    await workspace.searchParticipants();

    expect(workspace.filters.keyword).toBe("场次条件");
    expect(participantKeywords).toEqual(["名单条件", "新名单条件"]);
    expect(workspace.participants.page).toBe(1);
    expect(workspace.currentQuery()).toMatchObject({
      keyword: "场次条件",
      participantKeyword: "新名单条件",
    });
  });
});

describe("training workspace requests", () => {
  it("aborts an older session request and keeps the newer result", async () => {
    const requests: Array<{
      keyword: string;
      signal: AbortSignal;
      resolve: (page: TrainingPage<TrainingSession>) => void;
    }> = [];
    const workspace = useTrainingWorkspace({
      defaults,
      loadSessions: ({ filters, signal }) =>
        new Promise((resolve) =>
          requests.push({ keyword: filters.keyword, signal, resolve }),
        ),
      loadParticipants: async () => pageResult([], 0, 1, 30),
    });

    workspace.filters.keyword = "旧条件";
    const older = workspace.applyFilters();
    workspace.filters.keyword = "新条件";
    const newer = workspace.applyFilters();

    expect(requests[0].signal.aborted).toBe(true);
    requests[1].resolve(pageResult([session(2)], 1, 1, 20));
    await newer;
    requests[0].resolve(pageResult([session(1)], 1, 1, 20));
    await older;

    expect(workspace.selected.value?.id).toBe(2);
    expect(workspace.sessions.items.map((item) => item.id)).toEqual([2]);
  });

  it("restores the selected session and both page numbers from URL state", async () => {
    const sessionCalls: number[] = [];
    const participantCalls: Array<{ sessionId: number; page: number }> = [];
    const workspace = useTrainingWorkspace({
      initialQuery: {
        sessionId: "22",
        sessionPage: "2",
        participantPage: "3",
        keyword: "网络",
        from: "2026-02-01",
        to: "2026-07-01",
      },
      defaults,
      loadSessions: async ({ page }) => {
        sessionCalls.push(page);
        return pageResult([session(21), session(22)], 25, page, 20);
      },
      loadParticipants: async ({ sessionId, page }) => {
        participantCalls.push({ sessionId, page });
        return pageResult([participant(301, sessionId)], 65, page, 30);
      },
    });

    await workspace.initialize();

    expect(sessionCalls).toEqual([2]);
    expect(participantCalls).toEqual([{ sessionId: 22, page: 3 }]);
    expect(workspace.selected.value?.id).toBe(22);
    expect(workspace.sessions.page).toBe(2);
    expect(workspace.participants.page).toBe(3);
    expect(workspace.filters.keyword).toBe("网络");
  });

  it("restores a previous URL state after navigation", async () => {
    const calls: Array<{ kind: string; page: number; sessionId?: number }> = [];
    const workspace = useTrainingWorkspace({
      defaults,
      loadSessions: async ({ page }) => {
        calls.push({ kind: "sessions", page });
        return pageResult(
          page === 2 ? [session(22)] : [session(11)],
          21,
          page,
          20,
        );
      },
      loadParticipants: async ({ sessionId, page }) => {
        calls.push({ kind: "participants", page, sessionId });
        return pageResult([participant(sessionId * 10 + page, sessionId)], 61, page, 30);
      },
    });
    await workspace.initialize();

    await workspace.restoreQuery({
      sessionId: "22",
      sessionPage: "2",
      participantPage: "2",
      keyword: "返回条件",
      from: "2026-04-01",
      to: "2026-05-01",
    });

    expect(workspace.selected.value?.id).toBe(22);
    expect(workspace.sessions.page).toBe(2);
    expect(workspace.participants.page).toBe(2);
    expect(workspace.filters.keyword).toBe("返回条件");
    expect(calls.at(-1)).toEqual({
      kind: "participants",
      page: 2,
      sessionId: 22,
    });
  });

  it("aborts the old participant request and ignores its late response", async () => {
    const requests: Array<{
      sessionId: number;
      signal: AbortSignal;
      resolve: (page: TrainingPage<TrainingParticipant>) => void;
    }> = [];
    const workspace = useTrainingWorkspace({
      defaults,
      loadSessions: async () => pageResult([session(1), session(2)], 2, 1, 20),
      loadParticipants: ({ sessionId, signal }) =>
        new Promise((resolve) => requests.push({ sessionId, signal, resolve })),
    });

    const initialization = workspace.initialize();
    await Promise.resolve();
    await Promise.resolve();
    requests[0].resolve(pageResult([], 0, 1, 30));
    await initialization;

    const oldRequest = workspace.selectSession(session(2));
    const newRequest = workspace.selectSession(session(1));
    expect(requests[1].signal.aborted).toBe(true);
    requests[2].resolve(pageResult([participant(11, 1)], 1, 1, 30));
    await newRequest;
    requests[1].resolve(pageResult([participant(22, 2)], 1, 1, 30));
    await oldRequest;

    expect(workspace.selected.value?.id).toBe(1);
    expect(workspace.participants.items.map((item) => item.id)).toEqual([11]);
  });

  it("keeps session data when participants fail and can retry that region", async () => {
    let attempts = 0;
    const workspace = useTrainingWorkspace({
      defaults,
      loadSessions: async () => pageResult([session(7)], 1, 1, 20),
      loadParticipants: async ({ sessionId }) => {
        attempts += 1;
        if (attempts === 1) throw new Error("名单读取失败");
        return pageResult([participant(71, sessionId)], 1, 1, 30);
      },
    });

    await workspace.initialize();
    expect(workspace.sessions.items).toHaveLength(1);
    expect(workspace.participants.error).toBe("名单读取失败");

    await workspace.retryParticipants();
    expect(workspace.sessions.items).toHaveLength(1);
    expect(workspace.participants.error).toBe("");
    expect(workspace.participants.items[0]?.id).toBe(71);
  });

  it("falls back to the last populated participant page after deletion", async () => {
    const pages: number[] = [];
    const queries: Array<Record<string, string>> = [];
    const workspace = useTrainingWorkspace({
      defaults,
      initialQuery: { sessionId: "9", participantPage: "3" },
      onQueryChange: (query) => queries.push(query),
      loadSessions: async () => pageResult([session(9)], 1, 1, 20),
      loadParticipants: async ({ page }) => {
        pages.push(page);
        return page === 3
          ? pageResult([], 60, 3, 30)
          : pageResult([participant(59, 9)], 60, 2, 30);
      },
    });

    await workspace.initialize();

    expect(pages).toEqual([3, 2]);
    expect(workspace.participants.page).toBe(2);
    expect(workspace.participants.items[0]?.id).toBe(59);
    expect(queries.at(-1)?.participantPage).toBe("2");
  });
});

function pageResult<T>(
  items: T[],
  total: number,
  page: number,
  pageSize: number,
): TrainingPage<T> {
  return { items, total, page, pageSize, hasMore: page * pageSize < total };
}

function session(id: number): TrainingSession {
  return {
    id,
    title: `培训 ${id}`,
    trainingDate: "2026-08-01",
    status: "COMPLETED",
    participantCount: 1,
    totalDurationHours: 2,
    createdAt: "2026-08-01T10:00:00",
    updatedAt: "2026-08-01T10:00:00",
  };
}

function participant(id: number, sessionId: number): TrainingParticipant {
  return {
    id,
    sessionId,
    name: `成员 ${id}`,
    durationHours: 2,
  };
}
