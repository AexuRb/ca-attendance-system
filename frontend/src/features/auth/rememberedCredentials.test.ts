import { describe, expect, it, vi } from "vitest";
import {
  clearRememberedLogin,
  loadRememberedLogin,
  saveRememberedLogin,
  type CredentialStorage,
  type DesktopCredentialApi,
} from "./rememberedCredentials";

function memoryStorage(initial: Record<string, string> = {}): CredentialStorage {
  const values = new Map(Object.entries(initial));
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}

function desktopApi(
  loaded: { account: string; password: string } | null = null,
): DesktopCredentialApi {
  return {
    isDesktop: true,
    loadRememberedCredentials: vi.fn(async () => loaded),
    saveRememberedCredentials: vi.fn(async () => ({ saved: true })),
    clearRememberedCredentials: vi.fn(async () => ({ cleared: true })),
  };
}

describe("remembered credentials", () => {
  it("removes the legacy plaintext payload and loads only the browser account", async () => {
    const storage = memoryStorage({
      ca_remembered_credentials:
        '{"studentNo":"9900000001","password":"plaintext"}',
      ca_remembered_account: "9900000001",
    });

    const remembered = await loadRememberedLogin(storage, null);

    expect(remembered).toEqual({ studentNo: "9900000001", password: "" });
    expect(storage.getItem("ca_remembered_credentials")).toBeNull();
  });

  it("never writes a password to browser storage", async () => {
    const storage = memoryStorage();

    await saveRememberedLogin(
      { studentNo: "9900000001", password: "Test!credential-1" },
      storage,
      null,
    );

    expect(storage.getItem("ca_remembered_account")).toBe("9900000001");
    expect(storage.getItem("ca_remembered_credentials")).toBeNull();
    expect(JSON.stringify(storage)).not.toContain("Test!credential-1");
  });

  it("loads desktop credentials through the encrypted IPC bridge", async () => {
    const storage = memoryStorage({
      ca_remembered_credentials:
        '{"studentNo":"9900000001","password":"plaintext"}',
      ca_remembered_account: "stale-browser-account",
    });
    const api = desktopApi({
      account: "9900000001",
      password: "encrypted-at-rest",
    });

    const remembered = await loadRememberedLogin(storage, api);

    expect(remembered).toEqual({
      studentNo: "9900000001",
      password: "encrypted-at-rest",
    });
    expect(api.loadRememberedCredentials).toHaveBeenCalledOnce();
    expect(storage.getItem("ca_remembered_credentials")).toBeNull();
    expect(storage.getItem("ca_remembered_account")).toBeNull();
  });

  it("saves desktop credentials without copying them to localStorage", async () => {
    const storage = memoryStorage();
    const api = desktopApi();

    await saveRememberedLogin(
      { studentNo: "9900000001", password: "Test!credential-1" },
      storage,
      api,
    );

    expect(api.saveRememberedCredentials).toHaveBeenCalledWith({
      account: "9900000001",
      password: "Test!credential-1",
    });
    expect(storage.getItem("ca_remembered_account")).toBeNull();
    expect(storage.getItem("ca_remembered_credentials")).toBeNull();
  });

  it("clears both browser and desktop remembered login state", async () => {
    const storage = memoryStorage({
      ca_remembered_credentials: "legacy",
      ca_remembered_account: "9900000001",
    });
    const api = desktopApi();

    await clearRememberedLogin(storage, api);

    expect(storage.getItem("ca_remembered_credentials")).toBeNull();
    expect(storage.getItem("ca_remembered_account")).toBeNull();
    expect(api.clearRememberedCredentials).toHaveBeenCalledOnce();
  });
});
