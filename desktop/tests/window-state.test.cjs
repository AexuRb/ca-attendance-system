const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  createWindowStateStore,
  fitWindowBoundsToDisplays
} = require('../window-state.cjs');

test('keeps restored window bounds inside the nearest available display', () => {
  const fitted = fitWindowBoundsToDisplays(
    { x: 3000, y: 100, width: 1440, height: 900 },
    [{ x: 0, y: 0, width: 1920, height: 1080 }]
  );

  assert.deepEqual(fitted, { x: 480, y: 100, width: 1440, height: 900 });
});

test('persists window state by atomic replacement and rejects malformed state', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ca-attendance-window-state-'));
  try {
    const store = createWindowStateStore({ rootDirectory: root });
    store.save({ bounds: { x: 10, y: 20, width: 1200, height: 800 }, maximized: true });
    store.save({ bounds: { x: 30, y: 40, width: 1300, height: 850 }, maximized: false });

    assert.deepEqual(store.load(), {
      bounds: { x: 30, y: 40, width: 1300, height: 850 },
      maximized: false
    });
    assert.deepEqual(fs.readdirSync(path.dirname(store.filePath)), ['window-state.json']);

    fs.writeFileSync(store.filePath, '{broken', 'utf8');
    assert.equal(store.load(), null);
    assert.equal(fs.existsSync(store.filePath), false);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});
