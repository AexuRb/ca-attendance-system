// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import { workspaceEntrance } from "./workspaceEntrance";

describe("workspaceEntrance", () => {
  it("cancels an interrupted entrance without leaving inline styles", () => {
    const target = document.createElement("main");
    const cancel = vi.fn();
    target.animate = vi.fn().mockReturnValue({ cancel });
    const stop = workspaceEntrance(target, "classic", false);
    expect(target.animate).toHaveBeenCalledWith(expect.any(Array), expect.objectContaining({ duration: 160, fill: "none" }));
    stop();
    expect(cancel).toHaveBeenCalledOnce();
    expect(target.style.cssText).toBe("");
  });
  it("does not start animations when reduced motion is requested", () => {
    const target = document.createElement("main");
    target.animate = vi.fn();
    workspaceEntrance(target, "spatial", true)();
    expect(target.animate).not.toHaveBeenCalled();
  });
});
