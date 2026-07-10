const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  ensureStorageLayout,
  isAttendanceHealth,
  resolveAppRoot
} = require('../runtime.cjs');

test('resolves the data root above an installed app directory', () => {
  assert.equal(resolveAppRoot({
    isPackaged: true,
    executablePath: 'C:\\CAAttendance\\app\\CA Attendance System.exe',
    moduleDirectory: 'C:\\repo\\desktop'
  }), 'C:\\CAAttendance');
});

test('keeps a custom install directory from writing into its parent', () => {
  assert.equal(resolveAppRoot({
    isPackaged: true,
    executablePath: 'D:\\Association-System\\CA-Attendance-System.exe',
    moduleDirectory: 'C:\\repo\\desktop'
  }), 'D:\\Association-System');
});

test('allows an explicit root override for development and diagnostics', () => {
  const expected = path.resolve('C:\\Attendance-Test');
  assert.equal(resolveAppRoot({
    isPackaged: false,
    executablePath: '',
    moduleDirectory: 'C:\\repo\\desktop',
    override: 'C:\\Attendance-Test'
  }), expected);
});

test('creates all persistent storage directories outside the program directory', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ca-attendance-desktop-'));
  try {
    const layout = ensureStorageLayout(root);
    for (const name of ['data', 'backups', 'exports', 'logs']) {
      assert.equal(fs.statSync(layout[name]).isDirectory(), true);
    }
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('recognizes only this application with SQLite health metadata', () => {
  assert.equal(isAttendanceHealth({
    statusCode: 200,
    body: {
      status: 'ok',
      application: 'ca-attendance-system',
      databaseType: 'SQLite'
    }
  }), true);
  assert.equal(isAttendanceHealth({
    statusCode: 200,
    body: { status: 'ok', application: 'another-service' }
  }), false);
});
