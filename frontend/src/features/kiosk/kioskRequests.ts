import { ApiError } from "../../shared/api";

export const KIOSK_READ_TIMEOUT = 8000;
export const KIOSK_WRITE_TIMEOUT = 15000;

export class KioskRequestTimeout extends ApiError {
  constructor() {
    super("请求等待超时", 0, true);
  }
}

export function createKioskRequests() {
  const cancellations = new Set<() => void>();

  async function run<T>(
    request: (signal: AbortSignal) => Promise<T>,
    timeout = KIOSK_READ_TIMEOUT,
  ): Promise<T> {
    const controller = new AbortController();
    let rejectDeadline!: (cause: Error) => void;
    const deadline = new Promise<never>((_, reject) => { rejectDeadline = reject; });
    const cancel = () => {
      rejectDeadline(new ApiError("请求已取消", 0, true));
      controller.abort();
    };
    const timer = window.setTimeout(() => {
      // Settle the deadline before aborting fetch so timeout feedback stays precise.
      rejectDeadline(new KioskRequestTimeout());
      controller.abort();
    }, timeout);
    cancellations.add(cancel);
    try {
      // Cover response decoding as well as headers, even if transport ignores abort.
      return await Promise.race([request(controller.signal), deadline]);
    } finally {
      window.clearTimeout(timer);
      cancellations.delete(cancel);
      controller.abort();
    }
  }

  function cancel() {
    for (const cancellation of cancellations) cancellation();
  }

  return { run, cancel };
}
