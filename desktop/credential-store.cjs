const fs = require('node:fs');
const path = require('node:path');

const MAX_CREDENTIAL_FILE_BYTES = 64 * 1024;

function normalizeCredentials(credentials) {
  const account = String(credentials?.account || '').trim();
  const password = String(credentials?.password || '');
  if (!account || account.length > 128 || !password || password.length > 1024) {
    throw new Error('记住的登录信息无效');
  }
  return { account, password };
}

function createCredentialStore({ rootDirectory, safeStorage, fsModule = fs }) {
  if (!rootDirectory) throw new Error('缺少应用数据目录');

  const filePath = path.join(rootDirectory, 'data', 'remembered-login.bin');

  function assertEncryptionAvailable() {
    if (!safeStorage?.isEncryptionAvailable?.()) {
      throw new Error('系统加密服务不可用');
    }
  }

  function clear() {
    fsModule.rmSync(filePath, { force: true });
    return { cleared: true };
  }

  function save(credentials) {
    assertEncryptionAvailable();
    const normalized = normalizeCredentials(credentials);
    const plaintext = JSON.stringify({ version: 1, ...normalized });
    const encrypted = safeStorage.encryptString(plaintext);
    if (!Buffer.isBuffer(encrypted) || encrypted.length === 0) {
      throw new Error('系统未能加密登录信息');
    }

    fsModule.mkdirSync(path.dirname(filePath), { recursive: true });
    fsModule.writeFileSync(filePath, encrypted, { mode: 0o600 });
    return { saved: true };
  }

  function load() {
    if (!fsModule.existsSync(filePath)) return null;
    assertEncryptionAvailable();

    try {
      const stats = fsModule.statSync(filePath);
      if (stats.size <= 0 || stats.size > MAX_CREDENTIAL_FILE_BYTES) {
        clear();
        return null;
      }
      const encrypted = fsModule.readFileSync(filePath);
      const decoded = JSON.parse(safeStorage.decryptString(encrypted));
      if (decoded?.version !== 1) throw new Error('unsupported credential version');
      return normalizeCredentials(decoded);
    } catch {
      clear();
      return null;
    }
  }

  return Object.freeze({ filePath, load, save, clear });
}

module.exports = { createCredentialStore };
