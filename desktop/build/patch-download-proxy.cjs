// global-agent 4 removes the vulnerable formatter chain. Node HTTPS requests
// need TLS options even when they do not carry a secureEndpoint flag.
const crypto = require('node:crypto');
const fs = require('node:fs');
const { createRequire } = require('node:module');
const path = require('node:path');

const fromBuilder = createRequire(require.resolve('app-builder-lib'));
const fromDownloader = createRequire(fromBuilder.resolve('@electron/get'));
const distribution = path.dirname(fromDownloader.resolve('global-agent'));
const metadata = JSON.parse(fs.readFileSync(path.join(distribution, '../package.json'), 'utf8'));
if (metadata.version !== '4.1.3') throw new Error('Review download proxy compatibility before changing its version.');
const target = path.join(distribution, 'classes/Agent.js');
const source = fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n');
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const before = 'a2a275ef0a6955109a0b5c1473c7194c183c0dc3bc7e8009a3b59c98be88d539';
const after = '5ac3af0d8469da10e2c923827c5676097d066bac05493bad0dac5bac9ea8a376';
if (hash(source) !== after) {
  if (hash(source) !== before) throw new Error('Download proxy code changed; refusing to apply an unverified patch.');
  const patched = source.replace('if (configuration.secureEndpoint) {', "if (this.protocol === 'https:') {");
  if (hash(patched) !== after) throw new Error('Download proxy patch verification failed.');
  fs.writeFileSync(target, patched);
}
