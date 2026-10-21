#!/usr/bin/env node
// Copyright (C) 2026 Marcus Hirt. See LICENSE for license details.

// Validate the artifact a desktop client installs, including its default transport.
// Usage: node mcpb/test-mcpb.mjs target/openjdk-helper-<version>-<platform>.mcpb
import assert from 'node:assert/strict';
import { execFile, spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, readFile, rm, stat } from 'node:fs/promises';
import { createServer as httpServer } from 'node:http';
import { createServer as tcpServer } from 'node:net';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createInterface } from 'node:readline';
import { createRequire } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { promisify } from 'node:util';
import { validateManifest } from '@anthropic-ai/mcpb/cli';

function inside(root, relative) {
  assert(!relative.includes('\\') && !relative.includes(':'), `Invalid archive path: ${relative}`);
  const resolved = path.resolve(root, relative);
  assert(resolved.startsWith(root + path.sep), `Path escapes bundle: ${relative}`);
  return resolved;
}

// Use the official CLI to handle MCPB signatures, archive paths and executable
// permissions. In pinned MCPB 2.1.2 unpackExtension is not a public API export.
async function extract(bundle, directory) {
  // createRequire rather than import.meta.resolve, so the test also runs on Node 18 when signing locally.
  const packageRoot = new URL('../', pathToFileURL(createRequire(import.meta.url).resolve('@anthropic-ai/mcpb')));
  const metadata = JSON.parse(await readFile(new URL('package.json', packageRoot), 'utf8'));
  const cli = fileURLToPath(new URL(metadata.bin, packageRoot));
  await promisify(execFile)(process.execPath, [cli, 'unpack', bundle, directory], {
    timeout: 30000, windowsHide: true
  });
  assert(validateManifest(path.join(directory, 'manifest.json')), 'Invalid MCPB manifest');
  assert((await stat(path.join(directory, 'LICENSE'))).isFile(), 'Bundle must contain LICENSE');
}

function expand(value, directory) {
  const expanded = value.replaceAll('${__dirname}', directory);
  assert(!expanded.includes('${'), `Unresolved manifest variable: ${value}`);
  return expanded;
}

async function listen(server) {
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  return server.address().port;
}

function result(message) {
  assert(!message.error, JSON.stringify(message));
  assert(message.result, JSON.stringify(message));
  return message.result;
}

function text(message) {
  const value = result(message);
  assert(!value.isError, JSON.stringify(message));
  assert.equal(value.content[0].type, 'text');
  return value.content[0].text;
}

async function smoke(bundle, directory) {
  await extract(bundle, directory);
  const manifest = JSON.parse(await readFile(path.join(directory, 'manifest.json'), 'utf8'));
  assert.equal(manifest.server.type, 'binary');
  assert(!JSON.stringify(manifest).match(/__(VERSION|BINARY|PLATFORM)__/), 'Unexpanded manifest template');
  assert(manifest.compatibility.platforms.includes(process.platform), `Bundle does not support ${process.platform}`);
  const entry = inside(directory, manifest.server.entry_point);
  assert((await stat(entry)).isFile(), 'Manifest entry point is missing');
  const base = manifest.server.mcp_config;
  const override = base.platform_overrides?.[process.platform] ?? {};
  const config = { ...base, ...override, env: { ...base.env, ...override.env } };
  const command = path.resolve(directory, expand(config.command, directory));
  assert(command.startsWith(directory + path.sep), 'Manifest command must use a bundled executable');
  const executable = await stat(command);
  assert(executable.isFile(), 'Manifest executable is missing');
  if (process.platform !== 'win32') assert(executable.mode & 0o111, 'ZIP lost executable permission');
  const args = (config.args ?? []).map(arg => expand(arg, directory));
  assert(args.includes('-Dquarkus.profile=stdio'), 'Desktop bundle must select the stdio profile by default');

  const fixture = await readFile(new URL('../src/test/resources/census-small.xml', import.meta.url));
  const census = httpServer((request, response) => {
    response.writeHead(200, { 'Content-Type': 'application/xml; charset=utf-8' });
    response.end(fixture);
  });
  const occupied = tcpServer(socket => socket.destroy());
  let child;
  let closed;
  let stderr = '';
  let success;
  const invalidStdout = [];
  const pending = new Map();
  try {
    const censusPort = await listen(census);
    const occupiedPort = await listen(occupied);
    // Ignore ambient Quarkus settings: the transport must come from the bundle.
    const env = Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith('QUARKUS_')));
    Object.assign(env, Object.fromEntries(Object.entries(config.env).map(([key, value]) => [key, expand(value, directory)])));
    env.OPENJDK_CENSUS_URL = `http://127.0.0.1:${censusPort}/census`;
    env.QUARKUS_HTTP_HOST = '127.0.0.1';
    env.QUARKUS_HTTP_PORT = String(occupiedPort);
    child = spawn(command, args, { cwd: directory, env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
    closed = once(child, 'close');
    child.stdin.on('error', () => {}); // The exit/close result reports startup and broken-pipe failures.
    child.stderr.setEncoding('utf8');
    child.stderr.on('data', data => { if (stderr.length < 64000) stderr += data; });
    createInterface({ input: child.stdout }).on('line', line => {
      if (!line.trim()) return;
      try {
        const message = JSON.parse(line);
        assert.equal(message.jsonrpc, '2.0');
        if (message.id !== undefined && pending.has(message.id)) {
          pending.get(message.id)(message);
          pending.delete(message.id);
        }
      } catch {
        invalidStdout.push(line);
      }
    });
    let nextId = 1;
    function send(message) {
      child.stdin.write(JSON.stringify({ jsonrpc: '2.0', ...message }) + '\n');
    }
    async function request(method, params = {}) {
      const id = nextId++;
      let timer;
      const response = new Promise((resolve, reject) => {
        pending.set(id, resolve);
        timer = setTimeout(() => reject(new Error(`Timed out waiting for ${method}\n${stderr}`)), 45000);
      });
      try {
        send({ id, method, params });
        return await Promise.race([response, closed.then(() => {
          throw new Error(`Server exited waiting for ${method}\n${stderr}`);
        })]);
      } finally {
        clearTimeout(timer);
        pending.delete(id);
      }
    }
    const initialized = result(await request('initialize', {
      protocolVersion: '2025-11-25', capabilities: {}, clientInfo: { name: 'mcpb-smoke-test', version: '1' }
    }));
    assert.equal(initialized.serverInfo.name, 'OpenJDK Helper');
    assert.equal(initialized.protocolVersion, '2025-11-25');
    assert(initialized.capabilities.tools);
    send({ method: 'notifications/initialized' });
    const listing = result(await request('tools/list'));
    assert.deepEqual(manifest.tools.map(tool => tool.name).sort(), listing.tools.map(tool => tool.name).sort(),
      'Manifest tool metadata must match the executable');
    assert.deepEqual(listing.tools.map(tool => tool.name).sort(), [
      'getVersion', 'getPerson', 'getProject', 'getGroup', 'searchPeople', 'searchProjects', 'searchGroups',
      'getGitHubRepos', 'getGitHubPulls', 'getProjectMembers', 'getCensusStatus',
      'getContributionContext', 'getBuildGuidance', 'getOpenJdkReview', 'getBackportStatus',
      'getIssue', 'searchIssues', 'getJep', 'searchJeps', 'getProjectProcesses',
      'getIssueFixStatus', 'getPullRequestTriage', 'getContributorRecord', 'getProjectActivity',
      'getReleaseSchedule', 'searchMailingList', 'searchDevelopersGuide'
    ].sort());
    const version = text(await request('tools/call', { name: 'getVersion', arguments: {} }));
    assert.equal(version, initialized.serverInfo.version);
    assert.equal(version, manifest.version, 'Manifest version must match the executable inside the bundle');
    assert(version && version !== 'unknown' && !version.includes('${'));
    const person = JSON.parse(text(await request('tools/call', { name: 'getPerson', arguments: { userid: 'hirt' } })));
    assert.equal(person.results[0].userId, 'hirt');
    const processes = JSON.parse(text(await request('tools/call', {
      name: 'getProjectProcesses', arguments: { projectid: 'jmc', topic: 'reviewer' }
    })));
    assert(processes.results[0].summary.includes('Three-Vote Consensus'));
    success = `MCPB OK: ${path.basename(bundle)}; ${listing.tools.length} tools; version ${version}; stdio default, HTTP disabled`;
  } finally {
    if (child) {
      child.stdin.end();
      if (child.exitCode === null) {
        const deadline = setTimeout(() => child.kill('SIGKILL'), 5000);
        try { await closed.catch(() => {}); } finally { clearTimeout(deadline); }
      } else {
        await closed.catch(() => {});
      }
    }
    await Promise.all([new Promise(resolve => census.close(resolve)), new Promise(resolve => occupied.close(resolve))]);
    assert.deepEqual(invalidStdout, [], 'Only JSON-RPC messages may appear on bundled server stdout');
  }
  console.log(success);
}

const bundle = process.argv[2];
if (!bundle || process.argv.length !== 3) {
  console.error(`Usage: node ${path.basename(fileURLToPath(import.meta.url))} <bundle.mcpb>`);
  process.exitCode = 1;
} else {
  const directory = await mkdtemp(path.join(tmpdir(), 'openjdk-helper-mcpb-'));
  try {
    await smoke(path.resolve(bundle), directory);
  } finally {
    // This is the exact temporary directory created above, never an archive-supplied path.
    assert(directory.startsWith(path.resolve(tmpdir()) + path.sep + 'openjdk-helper-mcpb-'));
    await rm(directory, { recursive: true, force: true });
  }
}
