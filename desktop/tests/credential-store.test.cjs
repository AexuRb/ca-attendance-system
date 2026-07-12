const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const { createCredentialStore } = require('../credential-store.cjs');

function fakeSafeStorage() {
  return {
    isEncryptionAvailable: () => true,
    encryptString: value => Buffer.from(`encrypted:${Buffer.from(value, 'utf8').toString('base64')}`, 'utf8'),
    decryptString: value => {
      const text = value.toString('utf8');
      if (!text.startsWith('encrypted:')) throw new Error('invalid ciphertext');
      return Buffer.from(text.slice('encrypted:'.length), 'base64').toString('utf8');
    }
  };
}

test('encrypts remembered credentials before writing them to disk', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ca-attendance-credentials-'));
  try {
    const store = createCredentialStore({ rootDirectory: root, safeStorage: fakeSafeStorage() });
    store.save({ account: 'test-admin', password: 'private-password' });

    const diskValue = fs.readFileSync(store.filePath, 'utf8');
    assert.equal(diskValue.includes('test-admin'), false);
    assert.equal(diskValue.includes('private-password'), false);
    assert.deepEqual(store.load(), { account: 'test-admin', password: 'private-password' });
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('removes credentials that cannot be decrypted after migration', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ca-attendance-credentials-'));
  try {
    const store = createCredentialStore({ rootDirectory: root, safeStorage: fakeSafeStorage() });
    fs.mkdirSync(path.dirname(store.filePath), { recursive: true });
    fs.writeFileSync(store.filePath, 'not-encrypted');

    assert.equal(store.load(), null);
    assert.equal(fs.existsSync(store.filePath), false);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('refuses to save when operating-system encryption is unavailable', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ca-attendance-credentials-'));
  try {
    const store = createCredentialStore({
      rootDirectory: root,
      safeStorage: {
        isEncryptionAvailable: () => false
      }
    });

    assert.throws(
      () => store.save({ account: 'test-admin', password: 'private-password' }),
      /系统加密服务不可用/
    );
    assert.equal(fs.existsSync(store.filePath), false);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
