// @vitest-environment jsdom
import { afterEach, expect, it, vi } from "vitest";
import { get } from "../../shared/api";
import { createKioskRequests, KioskRequestTimeout } from "./kioskRequests";

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

it("bounds the whole response including a JSON body that never completes", async () => {
  vi.useFakeTimers();
  const json = vi.fn(() => new Promise(() => {}));
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, headers: new Headers({ "content-type": "application/json" }), json });
  vi.stubGlobal("fetch", fetchMock);
  const pending = createKioskRequests().run(signal => get("/synthetic", { signal }));
  const outcome = expect(pending).rejects.toBeInstanceOf(KioskRequestTimeout);
  await vi.advanceTimersByTimeAsync(8000);
  await outcome;
  expect(json).toHaveBeenCalledOnce();
  expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(true);
  expect(vi.getTimerCount()).toBe(0);
});
