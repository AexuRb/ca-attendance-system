import { describe, expect, it, vi } from "vitest";
import { usePendingActions } from "./usePendingActions";

describe("usePendingActions", () => {
  it("prevents the same action from entering twice while allowing other keys", async () => {
    let finish!: (value: string) => void;
    const task = vi.fn(() => new Promise<string>((resolve) => (finish = resolve)));
    const other = vi.fn(() => Promise.resolve("other"));
    const pending = usePendingActions();

    const first = pending.run("save", task);
    const duplicate = pending.run("save", task);
    const parallel = pending.run("export", other);

    expect(pending.isPending("save")).toBe(true);
    expect(task).toHaveBeenCalledTimes(1);
    expect(await duplicate).toBeUndefined();
    expect(await parallel).toBe("other");

    finish("saved");
    expect(await first).toBe("saved");
    expect(pending.isPending("save")).toBe(false);
  });
});
