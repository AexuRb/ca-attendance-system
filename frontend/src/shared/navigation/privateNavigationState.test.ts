import { describe, expect, it } from "vitest";
import { clearPrivateNavigationState, createPrivateNavigationState } from "./privateNavigationState";

describe("private navigation memory", () => {
  it("bounds recent entries and invalidates reads and writes from the old session", () => {
    const memory = createPrivateNavigationState<string>(2);
    const previous = memory.scope();
    previous.set("a", "合成甲");
    previous.set("b", "合成乙");
    previous.set("c", "合成丙");
    expect(previous.get("a")).toBeUndefined();
    expect(previous.get("b")).toBe("合成乙");
    clearPrivateNavigationState();
    previous.set("d", "迟到请求");
    expect(previous.get("b")).toBeUndefined();
    expect(memory.scope().get("d")).toBeUndefined();
  });
});
