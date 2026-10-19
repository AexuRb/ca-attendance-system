const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { execFile } = require('node:child_process');
const fs = require('node:fs');
const http = require('node:http');
const { createRequire } = require('node:module');
const os = require('node:os');
const path = require('node:path');
const { promisify } = require('node:util');

async function probe(mode) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ca-download-proxy-'));
  const body = Buffer.from('download control %.101f %.101e %.101g');
  const originalAgent = http.globalAgent;
  let originRequests = 0;
  let proxyRequests = 0;
  const origin = http.createServer((_request, response) => {
    originRequests++;
    response.end(body);
  });
  const proxy = http.createServer((request, response) => {
    proxyRequests++;
    assert.match(request.url, /^http:\/\/127\.0\.0\.1:/);
    response.end(body);
  });
  const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    await listen(origin);
    await listen(proxy);
    for (const key of Object.keys(process.env)) {
      if (/^(?:https?_proxy|all_proxy|no_proxy|global_agent_.*|electron_get_use_proxy)$/i.test(key)) {
        delete process.env[key];
      }
    }
    if (mode !== 'direct') {
      process.env.ELECTRON_GET_USE_PROXY = 'true';
      process.env.GLOBAL_AGENT_HTTP_PROXY = `http://127.0.0.1:${proxy.address().port}`;
      if (mode === 'bypass') process.env.GLOBAL_AGENT_NO_PROXY = '127.0.0.1';
    }
    const builderRequire = createRequire(require.resolve('app-builder-lib'));
    const downloader = builderRequire('@electron/get');
    const url = `http://127.0.0.1:${origin.address().port}/control?format=${encodeURIComponent('%.101f %.101e %.101g')}`;
    const file = await downloader.downloadArtifact({
      version: '9.9.9',
      artifactName: 'control.bin',
      isGeneric: true,
      cacheRoot: path.join(directory, 'cache'),
      tempDirectory: directory,
      checksums: { 'control.bin': crypto.createHash('sha256').update(body).digest('hex') },
      mirrorOptions: { resolveAssetURL: async () => url },
      downloadOptions: { quiet: true, timeout: { request: 3000 }, retry: { limit: 0 } },
    });
    assert.deepEqual(fs.readFileSync(file), body);
    assert.equal(proxyRequests, mode === 'proxy' ? 1 : 0);
    assert.equal(originRequests, mode === 'proxy' ? 0 : 1);
    console.log(JSON.stringify({ mode, originRequests, proxyRequests, checksumVerified: true }));
  } finally {
    originalAgent.destroy();
    origin.closeAllConnections();
    proxy.closeAllConnections();
    await Promise.all([origin, proxy].map(server => new Promise(resolve => server.close(resolve))));
    assert.ok(directory.startsWith(path.resolve(os.tmpdir()) + path.sep));
    fs.rmSync(directory, { recursive: true, force: true });
  }
}

if (process.argv[2] === '--probe') {
  probe(process.argv[3]).catch(error => {
    console.error(error);
    process.exitCode = 1;
  });
} else {
  const { test } = require('node:test');
  const execute = promisify(execFile);
  test('HTTPS proxy preserves TLS options without an internal secureEndpoint flag', () => {
    const fromBuilder = createRequire(require.resolve('app-builder-lib'));
    const fromDownloader = createRequire(fromBuilder.resolve('@electron/get'));
    const HttpsProxyAgent = require(path.join(path.dirname(fromDownloader.resolve('global-agent')), 'classes/HttpsProxyAgent.js')).default;
    const agent = new HttpsProxyAgent(() => true, () => true, () => ({ hostname: '127.0.0.1', port: 1 }), {}, 1000);
    let connection;
    const expectedError = new Error('connection intentionally intercepted');
    agent.createConnection = (configuration, callback) => { connection = configuration; callback(expectedError); };
    const request = { path: '/', method: 'GET', on() {}, once() {}, emit(event, error) {
      assert.equal(event, 'error');
      assert.equal(error, expectedError);
    } };
    agent.addRequest(request, { hostname: 'download.invalid', port: 443, ca: 'trusted CA', rejectUnauthorized: true });
    assert.equal(connection.tls.ca, 'trusted CA');
    assert.equal(connection.tls.servername, 'download.invalid');
    assert.equal(connection.tls.rejectUnauthorized, true);
  });
  for (const mode of ['direct', 'proxy', 'bypass']) {
    test(`builder download preserves checksum and ${mode} routing`, async () => {
      const result = await execute(process.execPath, [__filename, '--probe', mode], { timeout: 15000 });
      assert.equal(JSON.parse(result.stdout.trim()).mode, mode);
    });
  }
}
