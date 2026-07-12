const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { app, BrowserWindow, dialog, ipcMain, Menu, nativeImage, safeStorage, session, Tray } = require('electron');
const { createCredentialStore } = require('./credential-store.cjs');
const {
  APP_ORIGIN,
  backendLocations,
  ensureStorageLayout,
  postDesktopControl,
  probeApplication,
  restoreApplicationWindow,
  resolveAppRoot,
  shouldHideWindowOnClose,
  waitForApplication
} = require('./runtime.cjs');

const controlToken = crypto.randomBytes(32).toString('hex');
let appRoot;
let backendChild = null;
let backendLog = null;
let desktopLog = null;
let mainWindow = null;
let splashWindow = null;
let tray = null;
let trayNoticeShown = false;
let serviceReady = false;
let shuttingDown = false;
let allowQuit = false;
let credentialStore = null;

function assertTrustedRenderer(event) {
  const sourceUrl = event.senderFrame?.url || event.sender?.getURL?.() || '';
  try {
    if (new URL(sourceUrl).origin === APP_ORIGIN) return;
  } catch {
    // Fall through to the generic rejection below.
  }
  throw new Error('拒绝来自非本机应用页面的请求');
}

function registerCredentialIpc() {
  credentialStore = createCredentialStore({ rootDirectory: appRoot, safeStorage });
  ipcMain.handle('ca-attendance:credentials:load', event => {
    assertTrustedRenderer(event);
    return credentialStore.load();
  });
  ipcMain.handle('ca-attendance:credentials:save', (event, credentials) => {
    assertTrustedRenderer(event);
    return credentialStore.save(credentials);
  });
  ipcMain.handle('ca-attendance:credentials:clear', event => {
    assertTrustedRenderer(event);
    return credentialStore.clear();
  });
}

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

function showApplicationWindow() {
  if (restoreApplicationWindow(mainWindow)) {
    return;
  }
  if (serviceReady && !shuttingDown) {
    createMainWindow();
    return;
  }
  restoreApplicationWindow(splashWindow);
}

function showTrayNotice() {
  if (trayNoticeShown || !tray || process.platform !== 'win32') {
    return;
  }
  trayNoticeShown = true;
  tray.displayBalloon({
    iconType: 'info',
    title: '计算机协会管理系统仍在运行',
    content: '点击托盘图标可重新打开；需要结束服务时请选择“完全退出”。',
    noSound: true
  });
}

function createTray() {
  if (tray && !tray.isDestroyed()) {
    return;
  }
  const icon = nativeImage
    .createFromPath(path.join(__dirname, 'assets', 'app-icon.png'))
    .resize({ width: 32, height: 32 });
  tray = new Tray(icon);
  tray.setToolTip('计算机协会管理系统');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: '打开管理系统', click: showApplicationWindow },
    { type: 'separator' },
    { label: '完全退出', click: () => app.quit() }
  ]));
  tray.on('click', showApplicationWindow);
}

function scheduleSmokeTest() {
  const traySmokeMs = Number(process.env.CA_ATTENDANCE_SMOKE_TRAY_MS || 0);
  if (traySmokeMs >= 1000) {
    setTimeout(() => {
      mainWindow?.close();
      setTimeout(() => {
        const hidden = Boolean(mainWindow && !mainWindow.isDestroyed() && !mainWindow.isVisible());
        const trayAlive = Boolean(tray && !tray.isDestroyed());
        showApplicationWindow();
        setTimeout(() => {
          const restored = Boolean(mainWindow && !mainWindow.isDestroyed() && mainWindow.isVisible());
          const passed = hidden && trayAlive && restored;
          writeDesktopLog(`tray smoke-test hidden=${hidden} tray=${trayAlive} restored=${restored}`);
          process.exitCode = passed ? 0 : 1;
          app.quit();
        }, 250);
      }, 250);
    }, traySmokeMs);
    return;
  }

  const smokeExitMs = Number(process.env.CA_ATTENDANCE_SMOKE_EXIT_MS || 0);
  if (smokeExitMs >= 1000) {
    writeDesktopLog(`scheduled smoke-test exit in ${smokeExitMs}ms`);
    setTimeout(() => app.quit(), smokeExitMs);
  }
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
    scheduleSmokeTest();
  });
  mainWindow.on('close', event => {
    if (!shouldHideWindowOnClose({ allowQuit, shuttingDown })) {
      return;
    }
    event.preventDefault();
    mainWindow?.hide();
    writeDesktopLog('main window hidden to tray');
    showTrayNotice();
  });
  mainWindow.on('closed', () => {
    mainWindow = null;
  });
  mainWindow.loadURL(APP_ORIGIN);
  createTray();
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
      APP_DESKTOP_CONTROL_TOKEN: controlToken,
      SPRING_MAIN_BANNER_MODE: 'off'
    }
  });

  backendChild.stdout.pipe(backendLog, { end: false });
  backendChild.stderr.pipe(backendLog, { end: false });
  backendChild.once('error', error => writeDesktopLog(`backend spawn error: ${error.message}`));
  backendChild.once('exit', (code, signal) => {
    writeDesktopLog(`backend exited code=${code ?? 'null'} signal=${signal ?? 'null'}`);
    serviceReady = false;
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

const hasSingleInstanceLock = app.requestSingleInstanceLock();
if (!hasSingleInstanceLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    showApplicationWindow();
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
    registerCredentialIpc();
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
    serviceReady = true;
    createMainWindow();
  }).catch(error => {
    writeDesktopLog(`startup failed: ${error.stack || error.message}`);
    splashWindow?.close();
    dialog.showErrorBox('启动失败', `${error.message}\n\n日志位置：${appRoot ? path.join(appRoot, 'logs') : '尚未创建'}`);
    shuttingDown = true;
    app.quit();
  });
}

app.on('window-all-closed', () => {
  // Keep the local service available while the application lives in the tray.
});
app.on('before-quit', event => {
  if (allowQuit) {
    return;
  }
  event.preventDefault();
  shuttingDown = true;
  stopBackend().finally(() => {
    allowQuit = true;
    tray?.destroy();
    tray = null;
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
