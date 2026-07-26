import { computed, reactive } from "vue";
import { get, getToken, post, setToken } from "../shared/api";
import type { AccessContext, UserSession } from "../shared/types";

interface SetupStatus {
  initialized: boolean;
  userCount: number;
}
interface LoginResponse extends UserSession {
  token: string;
}

const state = reactive({
  ready: false,
  user: null as UserSession | null,
  access: {
    mode: "LOCAL",
    kioskAvailable: true,
    allowedRemoteRoles: [],
  } as AccessContext,
  setup: { initialized: true, userCount: 0 } as SetupStatus,
});

let bootPromise: Promise<void> | null = null;

async function bootstrap() {
  if (state.ready) return;
  if (bootPromise) return bootPromise;
  bootPromise = (async () => {
    try {
      state.access = await get<AccessContext>("/api/access/context");
    } catch {
      state.access = {
        mode: "LOCAL",
        kioskAvailable: true,
        allowedRemoteRoles: [],
      };
    }
    if (state.access.mode === "LOCAL") {
      try {
        state.setup = await get<SetupStatus>("/api/setup/status");
      } catch {
        /* initialized servers may reject this */
      }
    }
    if (getToken()) {
      try {
        const user = await get<UserSession>("/api/auth/me");
        state.user = { ...user, role: user.role as UserSession["role"] };
      } catch {
        setToken("");
        state.user = null;
      }
    }
    state.ready = true;
  })().finally(() => {
    bootPromise = null;
  });
  return bootPromise;
}

async function login(studentNo: string, password: string) {
  const response = await post<LoginResponse>("/api/auth/login", {
    studentNo,
    password,
  });
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
  setToken(response.token);
  state.user = response;
  state.setup.initialized = true;
  return response;
}

async function refreshUser() {
  state.user = await get<UserSession>("/api/auth/me");
  return state.user;
}

async function logout() {
  try {
    await post("/api/auth/logout");
  } catch {
    // A stale server-side token must not block local sign-out.
  } finally {
    setToken("");
    state.user = null;
  }
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
  };
}
