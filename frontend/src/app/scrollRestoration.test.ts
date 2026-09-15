// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import { cancelScrollRestoration, restoreScrollPosition } from "./scrollRestoration";

afterEach(() => { cancelScrollRestoration(); vi.unstubAllGlobals(); });
describe("scroll restoration", () => {
  it("waits for content height before restoring a history position", async () => {
    let nextFrame: FrameRequestCallback = () => {};
    vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => { nextFrame = callback; return 1; });
    vi.stubGlobal("cancelAnimationFrame", vi.fn());
    vi.stubGlobal("innerHeight", 900);
    const height = vi.spyOn(document.documentElement, "scrollHeight", "get").mockReturnValue(950);
    const result = restoreScrollPosition({ top: 300, left: 0 });
    nextFrame(0);
    height.mockReturnValue(1600);
    nextFrame(0);
    await expect(result).resolves.toEqual({ top: 300, left: 0, behavior: "instant" });
    height.mockRestore();
  });
  it("abandons pending restoration when the user interacts or another navigation starts", async () => {
    vi.stubGlobal("requestAnimationFrame", () => 1);
    vi.stubGlobal("cancelAnimationFrame", vi.fn());
    const first = restoreScrollPosition({ top: 300, left: 0 });
    window.dispatchEvent(new Event("wheel"));
    await expect(first).resolves.toBe(false);
    const second = restoreScrollPosition({ top: 300, left: 0 });
    cancelScrollRestoration();
    await expect(second).resolves.toBe(false);
  });
});
