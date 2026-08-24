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

test('atomically replaces remembered credentials without leaving temporary files', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ca-attendance-credentials-'));
  try {
    const operations = [];
    const observedFs = Object.create(fs);
    observedFs.writeFileSync = (target, ...args) => {
      operations.push(['write', target]);
      return fs.writeFileSync(target, ...args);
    };
    observedFs.renameSync = (source, target) => {
      operations.push(['rename', source, target]);
      return fs.renameSync(source, target);
    };
    const store = createCredentialStore({
      rootDirectory: root,
      safeStorage: fakeSafeStorage(),
      fsModule: observedFs
    });
    store.save({ account: 'first-admin', password: 'first-password' });
    store.save({ account: 'second-admin', password: 'second-password' });

    assert.deepEqual(store.load(), {
      account: 'second-admin',
      password: 'second-password'
    });
    assert.equal(operations[0][0], 'write');
    assert.notEqual(operations[0][1], store.filePath);
    assert.deepEqual(operations[1].slice(0, 1), ['rename']);
    assert.equal(operations[1][2], store.filePath);
    const files = fs.readdirSync(path.dirname(store.filePath));
    assert.deepEqual(files, ['remembered-login.bin']);
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
