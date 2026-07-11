const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');

const APP_HOST = '127.0.0.1';
const APP_PORT = 8080;
const APP_ORIGIN = `http://${APP_HOST}:${APP_PORT}`;
const HEALTH_PATH = '/api/health';

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

function backendLocations({ isPackaged, resourcesPath, moduleDirectory }) {
  if (isPackaged) {
    return {
      java: path.join(resourcesPath, 'runtime', 'bin', 'java.exe'),
      jar: path.join(resourcesPath, 'backend', 'attendance-backend.jar')
    };
  }

  const repoRoot = path.resolve(moduleDirectory, '..');
  const javaHome = process.env.JAVA_HOME?.trim();
  return {
    java: javaHome ? path.join(javaHome, 'bin', 'java.exe') : 'java',
    jar: path.join(repoRoot, 'backend', 'target', 'attendance-backend.jar')
  };
}

function requestJson({ method = 'GET', requestPath, body, token, timeoutMs = 3000 }) {
  return new Promise((resolve, reject) => {
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
      hostname: APP_HOST,
      port: APP_PORT,
      path: requestPath,
      method,
      headers,
      timeout: timeoutMs
    }, response => {
      const chunks = [];
      let size = 0;
      response.on('data', chunk => {
        size += chunk.length;
        if (size <= 1024 * 1024) {
          chunks.push(chunk);
        }
      });
      response.on('end', () => {
        const text = Buffer.concat(chunks).toString('utf8');
        let parsed = null;
        if (text) {
          try {
            parsed = JSON.parse(text);
          } catch {
            parsed = null;
          }
        }
        resolve({ statusCode: response.statusCode ?? 0, body: parsed, text });
      });
    });

    request.on('timeout', () => request.destroy(new Error('本机服务响应超时')));
    request.on('error', reject);
    if (payload) {
      request.write(payload);
    }
    request.end();
  });
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
  backendLocations,
  ensureStorageLayout,
  isAttendanceHealth,
  postDesktopControl,
  probeApplication,
  restoreApplicationWindow,
  resolveAppRoot,
  shouldHideWindowOnClose,
  waitForApplication
};
