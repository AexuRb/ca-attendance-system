import { computed, reactive } from "vue";
import {
  configureTokenStorage,
  ApiError,
  get,
  getToken,
  post,
  setToken,
} from "../shared/api";
import type { AccessContext, UserSession } from "../shared/types";
import { clearPrivateNavigationState } from "../shared/navigation/privateNavigationState";

interface SetupStatus {
  initialized: boolean;
}
interface LoginResponse extends UserSession {
  token: string;
}

const state = reactive({
  ready: false,
  startupPending: false,
  startupError: false,
  startupTarget: "/",
  user: null as UserSession | null,
  access: {
    mode: "LOCAL",
    kioskAvailable: true,
    allowedRemoteRoles: [],
  } as AccessContext,
  setup: { initialized: false } as SetupStatus,
});

let bootPromise: Promise<void> | null = null;

async function bootstrap() {
  if (state.ready) return;
  if (bootPromise) return bootPromise;
  bootPromise = (async () => {
    state.startupPending = true;
    state.startupError = false;
    try {
      const access = await startupGet<AccessContext>("/api/access/context");
      if (access.mode !== "LOCAL" && access.mode !== "REMOTE_ADMIN") throw new Error("Invalid entry context");
      state.access = access;
      configureTokenStorage(state.access.mode);
      if (state.access.mode === "LOCAL") {
        const setup = await startupGet<SetupStatus>("/api/setup/status");
        if (typeof setup.initialized !== "boolean") throw new Error("Invalid setup status");
        state.setup = setup;
      }
      if (getToken()) {
        try {
          const user = await startupGet<UserSession>("/api/auth/me");
          state.user = { ...user, role: user.role as UserSession["role"] };
        } catch (error) {
          if (!(error instanceof ApiError) || ![401, 403].includes(error.status)) throw error;
          clearPrivateNavigationState();
          setToken("");
          state.user = null;
        }
      }
      state.ready = true;
    } catch {
      state.startupError = true;
    }
  })().finally(() => {
    state.startupPending = false;
    bootPromise = null;
  });
  return bootPromise;
}

async function startupGet<T>(path: string) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 5000);
  try { return await get<T>(path, { signal: controller.signal }); }
  finally { clearTimeout(timeout); }
}

async function login(studentNo: string, password: string) {
  const response = await post<LoginResponse>("/api/auth/login", {
    studentNo,
    password,
  });
  clearPrivateNavigationState();
  setToken(response.token);
  state.user = response;
  return response;
}

async function initialize(account: string, name: string, password: string) {
  const response = await post<LoginResponse>("/api/setup/initialize", {
    account,
    name,
    password,
  });
  clearPrivateNavigationState();
  setToken(response.token);
  state.user = response;
  state.setup.initialized = true;
  return response;
}

async function refreshUser() {
  const refreshed = await get<UserSession>("/api/auth/me");
  if (state.user?.id !== refreshed.id || state.user?.role !== refreshed.role) clearPrivateNavigationState();
  state.user = refreshed;
  return state.user;
}

async function logout() {
  try {
    await post("/api/auth/logout");
  } catch {
    // A stale server-side token must not block local sign-out.
  } finally {
    clearPrivateNavigationState();
    setToken("");
    state.user = null;
  }
}

function expireSession() {
  clearPrivateNavigationState();
  setToken("");
  state.user = null;
}

export function useSession() {
  return {
    state,
    user: computed(() => state.user),
    isAuthenticated: computed(() => Boolean(state.user)),
    bootstrap,
    login,
    initialize,
    refreshUser,
    logout,
    expireSession,
  };
}
