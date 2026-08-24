// @vitest-environment jsdom
import { mount } from "@vue/test-utils";
import { defineComponent } from "vue";
import { describe, expect, it } from "vitest";
import { useLatestRequest } from "./useLatestRequest";

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

describe("useLatestRequest", () => {
  it("aborts the previous request and ignores its late result", async () => {
    const older = deferred<string>();
    const newer = deferred<string>();
    const signals: AbortSignal[] = [];
    let request!: ReturnType<typeof useLatestRequest>;
    const wrapper = mount(
      defineComponent({
        setup() {
          request = useLatestRequest();
          return () => null;
        },
      }),
    );

    const first = request.run((signal) => {
      signals.push(signal);
      return older.promise;
    });
    const second = request.run((signal) => {
      signals.push(signal);
      return newer.promise;
    });

    expect(signals[0]?.aborted).toBe(true);
    newer.resolve("new");
    expect(await second).toBe("new");
    older.resolve("old");
    expect(await first).toBeUndefined();
    expect(request.loading.value).toBe(false);
    wrapper.unmount();
  });

  it("aborts an active request and discards its result after unmount", async () => {
    const pending = deferred<string>();
    let signal!: AbortSignal;
    let request!: ReturnType<typeof useLatestRequest>;
    const wrapper = mount(
      defineComponent({
        setup() {
          request = useLatestRequest();
          return () => null;
        },
      }),
    );

    const result = request.run((currentSignal) => {
      signal = currentSignal;
      return pending.promise;
    });
    wrapper.unmount();
    expect(signal.aborted).toBe(true);
    pending.resolve("late");
    expect(await result).toBeUndefined();
  });

  it("keeps the latest request error available for retry", async () => {
    let request!: ReturnType<typeof useLatestRequest>;
    const wrapper = mount(
      defineComponent({
        setup() {
          request = useLatestRequest();
          return () => null;
        },
      }),
    );

    expect(
      await request.run(() => Promise.reject(new Error("加载失败"))),
    ).toBeUndefined();
    expect(request.error.value).toBe("加载失败");
    expect(request.loading.value).toBe(false);
    wrapper.unmount();
  });
});
