const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const MAX_WINDOW_STATE_BYTES = 16 * 1024;
const MIN_WINDOW_WIDTH = 1080;
const MIN_WINDOW_HEIGHT = 720;

function finiteInteger(value) {
  return Number.isFinite(value) ? Math.round(value) : null;
}

function normalizeBounds(value) {
  const bounds = {
    x: finiteInteger(value?.x),
    y: finiteInteger(value?.y),
    width: finiteInteger(value?.width),
    height: finiteInteger(value?.height)
  };
  if (Object.values(bounds).some(item => item === null)) return null;
  if (bounds.width <= 0 || bounds.height <= 0) return null;
  return bounds;
}

function intersectionArea(bounds, workArea) {
  const width = Math.max(0, Math.min(bounds.x + bounds.width, workArea.x + workArea.width)
    - Math.max(bounds.x, workArea.x));
  const height = Math.max(0, Math.min(bounds.y + bounds.height, workArea.y + workArea.height)
    - Math.max(bounds.y, workArea.y));
  return width * height;
}

function centerDistance(bounds, workArea) {
  const dx = bounds.x + bounds.width / 2 - (workArea.x + workArea.width / 2);
  const dy = bounds.y + bounds.height / 2 - (workArea.y + workArea.height / 2);
  return dx * dx + dy * dy;
}

function fitWindowBoundsToDisplays(value, displayWorkAreas) {
  const bounds = normalizeBounds(value);
  const workAreas = (displayWorkAreas || []).map(normalizeBounds).filter(Boolean);
  if (!bounds || !workAreas.length) return null;

  const workArea = [...workAreas].sort((left, right) => {
    const overlap = intersectionArea(bounds, right) - intersectionArea(bounds, left);
    return overlap || centerDistance(bounds, left) - centerDistance(bounds, right);
  })[0];
  const width = Math.min(Math.max(bounds.width, MIN_WINDOW_WIDTH), workArea.width);
  const height = Math.min(Math.max(bounds.height, MIN_WINDOW_HEIGHT), workArea.height);
  const maximumX = workArea.x + workArea.width - width;
  const maximumY = workArea.y + workArea.height - height;

  return {
    x: Math.min(Math.max(bounds.x, workArea.x), maximumX),
    y: Math.min(Math.max(bounds.y, workArea.y), maximumY),
    width,
    height
  };
}

function createWindowStateStore({ rootDirectory, fsModule = fs }) {
  if (!rootDirectory) throw new Error('缺少应用数据目录');
  const filePath = path.join(rootDirectory, 'data', 'window-state.json');

  function clear() {
    fsModule.rmSync(filePath, { force: true });
  }

  function load() {
    if (!fsModule.existsSync(filePath)) return null;
    try {
      const stats = fsModule.statSync(filePath);
      if (stats.size <= 0 || stats.size > MAX_WINDOW_STATE_BYTES) throw new Error('invalid size');
      const decoded = JSON.parse(fsModule.readFileSync(filePath, 'utf8'));
      const bounds = normalizeBounds(decoded?.bounds);
      if (decoded?.version !== 1 || !bounds || typeof decoded.maximized !== 'boolean') {
        throw new Error('invalid window state');
      }
      return { bounds, maximized: decoded.maximized };
    } catch {
      clear();
      return null;
    }
  }

  function save(value) {
    const bounds = normalizeBounds(value?.bounds);
    if (!bounds || typeof value?.maximized !== 'boolean') {
      throw new Error('窗口状态无效');
    }
    const content = `${JSON.stringify({ version: 1, bounds, maximized: value.maximized })}\n`;
    fsModule.mkdirSync(path.dirname(filePath), { recursive: true });
    const temporaryPath = `${filePath}.${process.pid}.${crypto.randomUUID()}.tmp`;
    try {
      fsModule.writeFileSync(temporaryPath, content, { encoding: 'utf8', flag: 'wx' });
      fsModule.renameSync(temporaryPath, filePath);
    } finally {
      fsModule.rmSync(temporaryPath, { force: true });
    }
    return { saved: true };
  }

  return Object.freeze({ clear, filePath, load, save });
}

module.exports = { createWindowStateStore, fitWindowBoundsToDisplays };
