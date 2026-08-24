const fs = require('node:fs');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  APP_PORT,
  REMOTE_ADMIN_PORT,
  backendFailureMessage,
  backendLocations,
  detectStartupConflict,
  ensureStorageLayout,
  isAttendanceHealth,
  isRequestedWindowSize,
  isLoopbackPortInUse,
  isKioskUrl,
  isZoomShortcut,
  requestJson,
  restoreApplicationWindow,
  selectSmokeScenario,
  shouldHideWindowOnClose,
  visualZoomLimitsForUrl,
  resolveAppRoot
} = require('../runtime.cjs');

test('rejects an oversized local-service response instead of parsing a truncated body', async () => {
  const server = require('node:http').createServer((_request, response) => {
    response.writeHead(200, { 'Content-Type': 'application/json' });
    response.end(JSON.stringify({ payload: 'x'.repeat(2048) }));
  });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });

  try {
    await assert.rejects(
      requestJson({
        requestPath: '/large',
        port: server.address().port,
        maxResponseBytes: 512
      }),
      /响应数据超过 512 字节上限/
    );
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});

test('reserves a separate loopback port for the remote admin tunnel', () => {
  assert.equal(REMOTE_ADMIN_PORT, 8081);
});

test('prefers the repository Temurin runtime when running the desktop from source', () => {
  const repoRoot = path.join(path.parse(process.cwd()).root, 'test-repo');
  const moduleDirectory = path.join(repoRoot, 'desktop');
  const expectedJava = path.join(repoRoot, 'runtime', 'temurin-21', 'bin', 'java.exe');
  const locations = backendLocations({
    isPackaged: false,
    resourcesPath: '',
    moduleDirectory,
    javaHome: '',
    fileExists: candidate => candidate === expectedJava
  });

  assert.equal(locations.java, expectedJava);
  assert.equal(locations.jar, path.join(repoRoot, 'backend', 'target', 'attendance-backend.jar'));
});

test('selects only explicit desktop smoke scenarios with safe delays', () => {
  assert.deepEqual(selectSmokeScenario({ CA_ATTENDANCE_SMOKE_TRAY_MS: '1800' }), {
    kind: 'tray',
    delayMs: 1800
  });
  assert.deepEqual(selectSmokeScenario({ CA_ATTENDANCE_SMOKE_BACKEND_CRASH_MS: '2200' }), {
    kind: 'backend-crash',
    delayMs: 2200
  });
  assert.deepEqual(selectSmokeScenario({ CA_ATTENDANCE_SMOKE_EXIT_MS: '1500' }), {
    kind: 'exit',
    delayMs: 1500
  });
  assert.deepEqual(selectSmokeScenario({ CA_ATTENDANCE_SMOKE_RESIZE_MS: '1600' }), {
    kind: 'resize',
    delayMs: 1600
  });
  assert.equal(selectSmokeScenario({ CA_ATTENDANCE_SMOKE_EXIT_MS: '999' }), null);
  assert.equal(selectSmokeScenario({ CA_ATTENDANCE_SMOKE_EXIT_MS: 'not-a-number' }), null);
});

test('detects local and remote startup port conflicts before spawning the backend', async () => {
  assert.deepEqual(await detectStartupConflict({
    probeApplicationFn: async () => ({ reachable: true, matches: true }),
    isPortInUseFn: async () => false
  }), { kind: 'APP_ALREADY_RUNNING', port: 8080 });

  assert.deepEqual(await detectStartupConflict({
    probeApplicationFn: async () => ({ reachable: true, matches: false }),
    isPortInUseFn: async () => false
  }), { kind: 'LOCAL_PORT_OCCUPIED', port: 8080 });

  assert.deepEqual(await detectStartupConflict({
    probeApplicationFn: async () => ({ reachable: false, matches: false }),
    isPortInUseFn: async port => port === APP_PORT
  }), { kind: 'LOCAL_PORT_OCCUPIED', port: APP_PORT });

  assert.deepEqual(await detectStartupConflict({
    probeApplicationFn: async () => ({ reachable: false, matches: false }),
    isPortInUseFn: async port => port === REMOTE_ADMIN_PORT
  }), { kind: 'REMOTE_PORT_OCCUPIED', port: REMOTE_ADMIN_PORT });

  assert.equal(await detectStartupConflict({
    probeApplicationFn: async () => ({ reachable: false, matches: false }),
    isPortInUseFn: async () => false
  }), null);
});

test('checks whether a loopback port is accepting connections', async () => {
  const server = net.createServer();
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  const port = server.address().port;

  try {
    assert.equal(await isLoopbackPortInUse(port), true);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }

  assert.equal(await isLoopbackPortInUse(port), false);
});

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

test('hides a normal close but allows an intentional application quit', () => {
  assert.equal(shouldHideWindowOnClose({ allowQuit: false, shuttingDown: false }), true);
  assert.equal(shouldHideWindowOnClose({ allowQuit: true, shuttingDown: false }), false);
  assert.equal(shouldHideWindowOnClose({ allowQuit: false, shuttingDown: true }), false);
});

test('restores and focuses a hidden or minimized application window', () => {
  const calls = [];
  const window = {
    isDestroyed: () => false,
    isMinimized: () => true,
    isVisible: () => false,
    restore: () => calls.push('restore'),
    show: () => calls.push('show'),
    focus: () => calls.push('focus')
  };

  assert.equal(restoreApplicationWindow(window), true);
  assert.deepEqual(calls, ['restore', 'show', 'focus']);
});

test('explains a backend bind failure as a startup port race', () => {
  assert.match(
    backendFailureMessage(
      'org.springframework.boot.web.server.PortInUseException: Port 8080 is already in use',
      'C:\\logs\\backend.log'
    ),
    /端口.*占用/
  );
  assert.match(
    backendFailureMessage('unexpected shutdown', 'C:\\logs\\backend.log'),
    /C:\\logs\\backend\.log/
  );
});

test('locks zoom shortcuts only on the public kiosk route', () => {
  assert.equal(isKioskUrl('http://127.0.0.1:8080/#/'), true);
  assert.equal(isKioskUrl('http://127.0.0.1:8080/#/login'), false);
  assert.equal(isKioskUrl('http://127.0.0.1:8080/#/admin/today'), false);
  assert.equal(isKioskUrl('https://example.com/#/'), false);
  assert.equal(isZoomShortcut({ control: true, key: '+' }), true);
  assert.equal(isZoomShortcut({ control: true, key: '0' }), true);
  assert.equal(isZoomShortcut({ control: false, key: '+' }), false);
});

test('uses positive visual zoom factors so the Electron viewport remains visible', () => {
  assert.deepEqual(visualZoomLimitsForUrl('http://127.0.0.1:8080/#/'), {
    minimumLevel: 1,
    maximumLevel: 1
  });
  assert.deepEqual(visualZoomLimitsForUrl('http://127.0.0.1:8080/#/setup'), {
    minimumLevel: 1,
    maximumLevel: 3
  });
});

test('accepts only operating-system window frame differences within two pixels', () => {
  assert.equal(isRequestedWindowSize(
    { width: 1081, height: 722 },
    { width: 1080, height: 720 }
  ), true);
  assert.equal(isRequestedWindowSize(
    { width: 1083, height: 720 },
    { width: 1080, height: 720 }
  ), false);
});

test('does not try to restore a destroyed application window', () => {
  const window = {
    isDestroyed: () => true
  };

  assert.equal(restoreApplicationWindow(window), false);
  assert.equal(restoreApplicationWindow(null), false);
});
