const fs = require('node:fs');
const http = require('node:http');
const net = require('node:net');
const path = require('node:path');

const APP_HOST = '127.0.0.1';
const APP_PORT = 8080;
const REMOTE_ADMIN_PORT = 8081;
const APP_ORIGIN = `http://${APP_HOST}:${APP_PORT}`;
const HEALTH_PATH = '/api/health';
const MAX_JSON_RESPONSE_BYTES = 1024 * 1024;

function resolveAppRoot({ isPackaged, executablePath, moduleDirectory, override }) {
  if (override && override.trim()) {
    return path.resolve(override.trim());
  }
  if (isPackaged) {
    const executableDirectory = path.dirname(path.resolve(executablePath));
    return path.basename(executableDirectory).toLowerCase() === 'app'
      ? path.dirname(executableDirectory)
      : executableDirectory;
  }
  return path.resolve(moduleDirectory, '..');
}

function ensureStorageLayout(appRoot) {
  const directories = ['data', 'backups', 'exports', 'logs'];
  for (const directory of directories) {
    fs.mkdirSync(path.join(appRoot, directory), { recursive: true });
  }
  return Object.fromEntries(directories.map(directory => [directory, path.join(appRoot, directory)]));
}

function shouldHideWindowOnClose({ allowQuit = false, shuttingDown = false } = {}) {
  return !allowQuit && !shuttingDown;
}

function selectSmokeScenario(environment = {}) {
  const candidates = [
    ['tray', environment.CA_ATTENDANCE_SMOKE_TRAY_MS],
    ['backend-crash', environment.CA_ATTENDANCE_SMOKE_BACKEND_CRASH_MS],
    ['resize', environment.CA_ATTENDANCE_SMOKE_RESIZE_MS],
    ['exit', environment.CA_ATTENDANCE_SMOKE_EXIT_MS]
  ];
  for (const [kind, rawDelay] of candidates) {
    const delayMs = Number(rawDelay || 0);
    if (Number.isFinite(delayMs) && delayMs >= 1000) {
      return { kind, delayMs };
    }
  }
  return null;
}

function restoreApplicationWindow(window) {
  if (!window || window.isDestroyed()) {
    return false;
  }
  if (window.isMinimized()) {
    window.restore();
  }
  if (!window.isVisible()) {
    window.show();
  }
  window.focus();
  return true;
}

function isRequestedWindowSize(bounds, requested, tolerance = 2) {
  return Boolean(bounds && requested)
    && Math.abs(bounds.width - requested.width) <= tolerance
    && Math.abs(bounds.height - requested.height) <= tolerance;
}

function backendLocations({
  isPackaged,
  resourcesPath,
  moduleDirectory,
  javaHome = process.env.JAVA_HOME,
  fileExists = fs.existsSync
}) {
  if (isPackaged) {
    return {
      java: path.join(resourcesPath, 'runtime', 'bin', 'java.exe'),
      jar: path.join(resourcesPath, 'backend', 'attendance-backend.jar')
    };
  }

  const repoRoot = path.resolve(moduleDirectory, '..');
  const bundledJava = path.join(repoRoot, 'runtime', 'temurin-21', 'bin', 'java.exe');
  const configuredJavaHome = String(javaHome || '').trim();
  return {
    java: fileExists(bundledJava)
      ? bundledJava
      : configuredJavaHome
        ? path.join(configuredJavaHome, 'bin', 'java.exe')
        : 'java',
    jar: path.join(repoRoot, 'backend', 'target', 'attendance-backend.jar')
  };
}

function requestJson({
  method = 'GET',
  requestPath,
  body,
  token,
  timeoutMs = 3000,
  hostname = APP_HOST,
  port = APP_PORT,
  maxResponseBytes = MAX_JSON_RESPONSE_BYTES
}) {
  return new Promise((resolve, reject) => {
    let settled = false;
    const settle = (callback, value) => {
      if (settled) return;
      settled = true;
      callback(value);
    };
    const payload = body === undefined ? null : Buffer.from(JSON.stringify(body), 'utf8');
    const headers = { Accept: 'application/json' };
    if (payload) {
      headers['Content-Type'] = 'application/json; charset=utf-8';
      headers['Content-Length'] = payload.length;
    }
    if (token) {
      headers['X-Desktop-Control-Token'] = token;
    }

    const request = http.request({
      hostname,
      port,
      path: requestPath,
      method,
      headers,
      timeout: timeoutMs
    }, response => {
      const chunks = [];
      let size = 0;
      response.on('data', chunk => {
        size += chunk.length;
        if (size > maxResponseBytes) {
          settle(reject, new Error(`本机服务响应数据超过 ${maxResponseBytes} 字节上限`));
          response.destroy();
          return;
        }
        chunks.push(chunk);
      });
      response.on('end', () => {
        if (settled) return;
        const text = Buffer.concat(chunks).toString('utf8');
        let parsed = null;
        if (text) {
          try {
            parsed = JSON.parse(text);
          } catch {
            parsed = null;
          }
        }
        settle(resolve, { statusCode: response.statusCode ?? 0, body: parsed, text });
      });
      response.on('error', error => settle(reject, error));
      response.on('aborted', () => settle(reject, new Error('本机服务响应意外中断')));
    });

    request.on('timeout', () => request.destroy(new Error('本机服务响应超时')));
    request.on('error', error => settle(reject, error));
    if (payload) {
      request.write(payload);
    }
    request.end();
  });
}

function isKioskUrl(value) {
  try {
    const target = new URL(value);
    const route = (target.hash.replace(/^#/, '') || '/').split('?')[0];
    return target.origin === APP_ORIGIN && route === '/';
  } catch {
    return false;
  }
}

function visualZoomLimitsForUrl(value) {
  return isKioskUrl(value)
    ? { minimumLevel: 1, maximumLevel: 1 }
    : { minimumLevel: 1, maximumLevel: 3 };
}

function isZoomShortcut(input = {}) {
  return Boolean(input.control || input.meta)
    && ['+', '=', '-', '0', 'Add', 'Subtract'].includes(input.key);
}

function backendFailureMessage(logText, logPath) {
  const portConflict = /(?:BindException|PortInUseException|Address already in use|端口.*(?:占用|使用))/i
    .test(String(logText || ''));
  if (portConflict) {
    return `服务端口在启动过程中被其他程序占用，请关闭占用程序后重试。详细信息：${logPath}`;
  }
  return `后端服务意外退出，请查看日志：${logPath}`;
}

function isAttendanceHealth(response) {
  return response?.statusCode === 200
    && response.body?.status === 'ok'
    && response.body?.application === 'ca-attendance-system'
    && response.body?.databaseType === 'SQLite';
}

async function probeApplication() {
  try {
    const response = await requestJson({ requestPath: HEALTH_PATH, timeoutMs: 1200 });
    return { reachable: true, matches: isAttendanceHealth(response), response };
  } catch (error) {
    return { reachable: false, matches: false, error };
  }
}

function isLoopbackPortInUse(port, { timeoutMs = 800 } = {}) {
  return new Promise(resolve => {
    const socket = net.createConnection({ host: APP_HOST, port });
    let settled = false;

    function finish(inUse) {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve(inUse);
    }

    socket.setTimeout(timeoutMs);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
  });
}

async function detectStartupConflict({
  probeApplicationFn = probeApplication,
  isPortInUseFn = isLoopbackPortInUse
} = {}) {
  const existing = await probeApplicationFn();
  if (existing.reachable) {
    return existing.matches
      ? { kind: 'APP_ALREADY_RUNNING', port: APP_PORT }
      : { kind: 'LOCAL_PORT_OCCUPIED', port: APP_PORT };
  }
  if (await isPortInUseFn(APP_PORT)) {
    return { kind: 'LOCAL_PORT_OCCUPIED', port: APP_PORT };
  }
  if (await isPortInUseFn(REMOTE_ADMIN_PORT)) {
    return { kind: 'REMOTE_PORT_OCCUPIED', port: REMOTE_ADMIN_PORT };
  }
  return null;
}

async function waitForApplication({ timeoutMs = 60000, intervalMs = 350 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const probe = await probeApplication();
    if (probe.matches) {
      return probe.response.body;
    }
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }
  throw new Error('本机服务启动超时');
}

async function postDesktopControl(requestPath, token, body) {
  const response = await requestJson({
    method: 'POST',
    requestPath,
    body,
    token,
    timeoutMs: 10000
  });
  if (response.statusCode < 200 || response.statusCode >= 300) {
    const message = response.body?.message || response.body?.error || '本机操作失败';
    throw new Error(message);
  }
  return response.body;
}

module.exports = {
  APP_HOST,
  APP_ORIGIN,
  APP_PORT,
  REMOTE_ADMIN_PORT,
  backendFailureMessage,
  backendLocations,
  detectStartupConflict,
  ensureStorageLayout,
  isAttendanceHealth,
  isKioskUrl,
  isRequestedWindowSize,
  isLoopbackPortInUse,
  isZoomShortcut,
  postDesktopControl,
  probeApplication,
  restoreApplicationWindow,
  requestJson,
  resolveAppRoot,
  selectSmokeScenario,
  shouldHideWindowOnClose,
  visualZoomLimitsForUrl,
  waitForApplication
};
