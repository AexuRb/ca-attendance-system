const TOKEN_KEY = "ca_attendance_token";
type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;
let invalidatedToken = "";

export class ApiError extends Error {
  status: number;
  network: boolean;

  constructor(message: string, status = 0, network = false) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.network = network;
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || "";
}

export function setToken(token: string) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
    invalidatedToken = "";
  }
  else localStorage.removeItem(TOKEN_KEY);
}

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  unauthorizedHandler = handler;
}

export async function api<T = unknown>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const isFormData =
    typeof FormData !== "undefined" && options.body instanceof FormData;
  const headers = new Headers(options.headers);
  if (options.body && !isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(path, { ...options, headers });
  } catch (cause) {
    throw new ApiError(
      "本机服务暂时无法连接，已保留当前输入，请稍后重试",
      0,
      true,
    );
  }
  if (!response.ok) {
    if (response.status === 401 && token) {
      setToken("");
      if (invalidatedToken !== token) {
        invalidatedToken = token;
        unauthorizedHandler?.();
      }
    }
    let message = `请求失败（${response.status}）`;
    try {
      const payload = await response.json();
      message = payload.message || message;
    } catch {
      // Keep the status-based message for non-JSON failures.
    }
    throw new ApiError(message, response.status);
  }
  if (response.status === 204) return null as T;
  const type = response.headers.get("content-type") || "";
  if (type.includes("application/json")) return response.json() as Promise<T>;
  return response.blob() as Promise<T>;
}

export const get = <T = unknown>(path: string) => api<T>(path);
export const post = <T = unknown>(path: string, body?: unknown) =>
  api<T>(path, {
    method: "POST",
    body: body instanceof FormData ? body : JSON.stringify(body ?? {}),
  });
export const put = <T = unknown>(path: string, body?: unknown) =>
  api<T>(path, {
    method: "PUT",
    body: body instanceof FormData ? body : JSON.stringify(body ?? {}),
  });
export const del = <T = unknown>(path: string, body?: unknown) =>
  api<T>(path, {
    method: "DELETE",
    body: body === undefined ? undefined : JSON.stringify(body),
  });

export function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
