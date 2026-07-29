export interface CredentialStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export interface DesktopCredentialApi {
  isDesktop: true;
  loadRememberedCredentials(): Promise<{
    account: string;
    password: string;
  } | null>;
  saveRememberedCredentials(credentials: {
    account: string;
    password: string;
  }): Promise<{ saved: boolean }>;
  clearRememberedCredentials(): Promise<{ cleared: boolean }>;
}

export interface RememberedLogin {
  studentNo: string;
  password: string;
}

const LEGACY_CREDENTIALS_KEY = "ca_remembered_credentials";
const BROWSER_ACCOUNT_KEY = "ca_remembered_account";

export function desktopCredentialApi(): DesktopCredentialApi | null {
  if (typeof window === "undefined") return null;
  const candidate = (
    window as Window & { desktopAPI?: DesktopCredentialApi }
  ).desktopAPI;
  return candidate?.isDesktop ? candidate : null;
}

export function isDesktopCredentialMode(): boolean {
  return Boolean(desktopCredentialApi());
}

export async function loadRememberedLogin(
  storage: CredentialStorage = localStorage,
  desktopApi: DesktopCredentialApi | null = desktopCredentialApi(),
): Promise<RememberedLogin | null> {
  removeLegacyCredentials(storage);
  if (desktopApi) {
    storage.removeItem(BROWSER_ACCOUNT_KEY);
    try {
      const saved = await desktopApi.loadRememberedCredentials();
      if (!saved) return null;
      const studentNo = normalizeAccount(saved.account);
      const password = String(saved.password || "");
      if (!studentNo || !password || password.length > 1024) {
        await desktopApi.clearRememberedCredentials();
        return null;
      }
      return { studentNo, password };
    } catch {
      await desktopApi.clearRememberedCredentials().catch(() => undefined);
      return null;
    }
  }

  const studentNo = normalizeAccount(storage.getItem(BROWSER_ACCOUNT_KEY));
  if (!studentNo) {
    storage.removeItem(BROWSER_ACCOUNT_KEY);
    return null;
  }
  return { studentNo, password: "" };
}

export async function saveRememberedLogin(
  credentials: RememberedLogin,
  storage: CredentialStorage = localStorage,
  desktopApi: DesktopCredentialApi | null = desktopCredentialApi(),
): Promise<void> {
  removeLegacyCredentials(storage);
  const studentNo = normalizeAccount(credentials.studentNo);
  if (!studentNo) throw new Error("账号格式不正确");

  if (desktopApi) {
    storage.removeItem(BROWSER_ACCOUNT_KEY);
    const password = String(credentials.password || "");
    if (!password || password.length > 1024) throw new Error("密码格式不正确");
    await desktopApi.saveRememberedCredentials({
      account: studentNo,
      password,
    });
    return;
  }

  storage.setItem(BROWSER_ACCOUNT_KEY, studentNo);
}

export async function clearRememberedLogin(
  storage: CredentialStorage = localStorage,
  desktopApi: DesktopCredentialApi | null = desktopCredentialApi(),
): Promise<void> {
  removeLegacyCredentials(storage);
  storage.removeItem(BROWSER_ACCOUNT_KEY);
  if (desktopApi) await desktopApi.clearRememberedCredentials();
}

function removeLegacyCredentials(storage: CredentialStorage) {
  storage.removeItem(LEGACY_CREDENTIALS_KEY);
}

function normalizeAccount(value: unknown): string {
  const account = String(value || "").trim();
  return account && account.length <= 128 ? account : "";
}
