const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { app, BrowserWindow, dialog, ipcMain, session } = require('electron');
const {
  APP_ORIGIN,
  backendLocations,
  ensureStorageLayout,
  postDesktopControl,
  probeApplication,
  resolveAppRoot,
  waitForApplication
} = require('./runtime.cjs');

const controlToken = crypto.randomBytes(32).toString('hex');
let appRoot;
let backendChild = null;
let backendLog = null;
let desktopLog = null;
let mainWindow = null;
let splashWindow = null;
let shuttingDown = false;
let allowQuit = false;

function writeDesktopLog(message) {
  const line = `[${new Date().toISOString()}] ${message}\n`;
  if (desktopLog) {
    desktopLog.write(line);
  }
}

function createSplashWindow() {
  splashWindow = new BrowserWindow({
    width: 480,
    height: 300,
    frame: false,
    resizable: false,
    show: false,
    backgroundColor: '#0b63b6',
    icon: path.join(__dirname, 'assets', 'app-icon.png'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true
    }
  });
  splashWindow.loadFile(path.join(__dirname, 'splash.html'));
  splashWindow.once('ready-to-show', () => splashWindow?.show());
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1080,
    minHeight: 720,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: '#f4f8fc',
    title: '计算机协会管理系统',
    icon: path.join(__dirname, 'assets', 'app-icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
      spellcheck: false,
      devTools: !app.isPackaged
    }
  });

  mainWindow.removeMenu();
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  mainWindow.webContents.on('will-navigate', (event, targetUrl) => {
    try {
      if (new URL(targetUrl).origin !== APP_ORIGIN) {
        event.preventDefault();
      }
    } catch {
      event.preventDefault();
    }
  });
  mainWindow.once('ready-to-show', () => {
    splashWindow?.close();
    splashWindow = null;
    mainWindow?.show();
    const smokeExitMs = Number(process.env.CA_ATTENDANCE_SMOKE_EXIT_MS || 0);
    if (!app.isPackaged && smokeExitMs >= 1000) {
      writeDesktopLog(`scheduled smoke-test exit in ${smokeExitMs}ms`);
      setTimeout(() => app.quit(), smokeExitMs);
    }
  });
  mainWindow.on('closed', () => {
    mainWindow = null;
  });
  mainWindow.loadURL(APP_ORIGIN);
}

function startBackend() {
  const locations = backendLocations({
    isPackaged: app.isPackaged,
    resourcesPath: process.resourcesPath,
    moduleDirectory: __dirname
  });
  if (!fs.existsSync(locations.jar)) {
    throw new Error(`找不到后端程序：${locations.jar}`);
  }
  if (locations.java !== 'java' && !fs.existsSync(locations.java)) {
    throw new Error(`找不到 Java 运行时：${locations.java}`);
  }

  backendLog = fs.createWriteStream(path.join(appRoot, 'logs', 'backend.log'), { flags: 'a' });
  const args = [
    '-Dfile.encoding=UTF-8',
    '-Djava.awt.headless=true',
    '-jar',
    locations.jar,
    '--server.address=127.0.0.1',
    '--server.port=8080'
  ];
  backendChild = spawn(locations.java, args, {
    cwd: appRoot,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: {
      ...process.env,
      APP_ROOT: appRoot,
      APP_RECOVERY_TOKEN: controlToken,
      SPRING_MAIN_BANNER_MODE: 'off'
    }
  });

  backendChild.stdout.pipe(backendLog, { end: false });
  backendChild.stderr.pipe(backendLog, { end: false });
  backendChild.once('error', error => writeDesktopLog(`backend spawn error: ${error.message}`));
  backendChild.once('exit', (code, signal) => {
    writeDesktopLog(`backend exited code=${code ?? 'null'} signal=${signal ?? 'null'}`);
    backendChild = null;
    backendLog?.end();
    backendLog = null;
    if (!shuttingDown) {
      dialog.showErrorBox('本机服务已停止', `后端服务意外退出，请查看日志：${path.join(appRoot, 'logs', 'backend.log')}`);
      app.quit();
    }
  });
  writeDesktopLog(`backend started pid=${backendChild.pid}`);
}

async function waitForChildExit(timeoutMs) {
  const child = backendChild;
  if (!child || child.exitCode !== null) {
    return true;
  }
  return new Promise(resolve => {
    const timer = setTimeout(() => resolve(false), timeoutMs);
    child.once('exit', () => {
      clearTimeout(timer);
      resolve(true);
    });
  });
}

async function stopBackend() {
  if (!backendChild) {
    return;
  }
  shuttingDown = true;
  try {
    await postDesktopControl('/api/desktop/shutdown', controlToken);
  } catch (error) {
    writeDesktopLog(`graceful shutdown request failed: ${error.message}`);
  }
  const exited = await waitForChildExit(7000);
  if (!exited && backendChild) {
    const child = backendChild;
    writeDesktopLog(`backend did not exit in time; terminating process tree pid=${child.pid}`);
    if (process.platform === 'win32') {
      await new Promise(resolve => {
        const taskkill = spawn('taskkill.exe', ['/pid', String(child.pid), '/t', '/f'], {
          windowsHide: true,
          stdio: 'ignore'
        });
        taskkill.once('error', () => resolve());
        taskkill.once('exit', () => resolve());
      });
    } else {
      child.kill('SIGTERM');
    }
    await waitForChildExit(3000);
  }
}

function assertTrustedSender(event) {
  const senderUrl = event.senderFrame?.url || '';
  try {
    if (new URL(senderUrl).origin !== APP_ORIGIN) {
      throw new Error('不受信任的桌面请求来源');
    }
  } catch {
    throw new Error('不受信任的桌面请求来源');
  }
}

ipcMain.handle('desktop:reset-admin', async (event, request) => {
  assertTrustedSender(event);
  const account = typeof request?.account === 'string' ? request.account.trim() : '';
  const newPassword = typeof request?.newPassword === 'string' ? request.newPassword : '';
  if (!/^[A-Za-z0-9_-]{4,32}$/.test(account) || newPassword.length < 6 || newPassword.length > 64) {
    throw new Error('账号或新密码格式不正确');
  }
  return postDesktopControl('/api/desktop/reset-admin', controlToken, { account, newPassword });
});

const hasSingleInstanceLock = app.requestSingleInstanceLock();
if (!hasSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) {
        mainWindow.restore();
      }
      mainWindow.focus();
    }
  });

  app.whenReady().then(async () => {
    app.setAppUserModelId('cn.cugb.computerassociation.attendance');
    appRoot = resolveAppRoot({
      isPackaged: app.isPackaged,
      executablePath: process.execPath,
      moduleDirectory: __dirname,
      override: process.env.CA_ATTENDANCE_ROOT
    });
    ensureStorageLayout(appRoot);
    desktopLog = fs.createWriteStream(path.join(appRoot, 'logs', 'desktop.log'), { flags: 'a' });
    writeDesktopLog(`desktop starting version=${app.getVersion()} root=${appRoot}`);

    session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => callback(false));
    session.defaultSession.setPermissionCheckHandler(() => false);
    createSplashWindow();

    const existing = await probeApplication();
    if (existing.reachable) {
      throw new Error(existing.matches
        ? '本机服务已经在运行。请先关闭占用 8080 端口的旧程序，再重新启动。'
        : '8080 端口已被其他程序占用，请关闭占用程序后重试。');
    }

    startBackend();
    await waitForApplication();
    createMainWindow();
  }).catch(error => {
    writeDesktopLog(`startup failed: ${error.stack || error.message}`);
    splashWindow?.close();
    dialog.showErrorBox('启动失败', `${error.message}\n\n日志位置：${appRoot ? path.join(appRoot, 'logs') : '尚未创建'}`);
    shuttingDown = true;
    app.quit();
  });
}

app.on('window-all-closed', () => app.quit());
app.on('before-quit', event => {
  if (allowQuit) {
    return;
  }
  event.preventDefault();
  stopBackend().finally(() => {
    allowQuit = true;
    desktopLog?.end();
    app.quit();
  });
});

process.on('uncaughtException', error => {
  writeDesktopLog(`uncaught exception: ${error.stack || error.message}`);
});
process.on('unhandledRejection', error => {
  writeDesktopLog(`unhandled rejection: ${error?.stack || error}`);
});
