// Copyright (C) 2026 Marcus Hirt. See LICENSE for license details.
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, realpath, rm, symlink, writeFile, access } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { test } from 'node:test';
import { unzipSync } from 'fflate';
import { buildPlugin } from './pack.mjs';

async function fixture(t) {
  const root = await mkdtemp(path.join(tmpdir(), 'openjdk-plugin-test-'));
  t.after(async () => {
    const resolved = await realpath(root);
    assert.equal(path.dirname(resolved), await realpath(tmpdir()));
    assert(path.basename(resolved).startsWith('openjdk-plugin-test-'));
    await rm(resolved, { recursive: true, force: true });
  });
  const manifest = { $schema: 'https://agent-plugins.org/schemas/1.0.0/plugin.schema.json', name: 'openjdk-helper', description: 'Read-only OpenJDK information', author: { name: 'Marcus Hirt' }, license: 'BSD-3-Clause',
    extensions: { 'com.openai': { interface: { displayName: 'OpenJDK Helper', shortDescription: 'OpenJDK tools',
      composerIcon: './assets/icon.svg', logo: './assets/icon.svg', websiteURL: 'https://www.hirt.se', supportURL: 'https://github.com/thegreystone/openjdk-helper/issues' },
      review: { test_cases: {
        positive: Array.from({ length: 5 }, () => ({ description: 'Read data', prompt: 'Find JEP 444', tools_triggered: 'getJep', expected_behavior: 'Cite the JEP' })),
        negative: Array.from({ length: 3 }, () => ({ description: 'No mutation', prompt: 'Integrate a change' })) } } } } };
  const mcp = { $schema: 'https://agent-plugins.org/schemas/1.0.0/mcp.schema.json', mcpServers: { 'openjdk-helper': { type: 'streamable-http', url: 'https://api.hirt.se/openjdk/mcp', extensions: { 'com.openai': { auth: { type: 'none' } } } } } };
  const claude = { name: 'openjdk-helper', description: 'Read-only OpenJDK information', icon: './assets/icon.png' };
  const claudeMcp = { mcpServers: { 'openjdk-helper': { type: 'http', url: 'https://api.hirt.se/openjdk/mcp' } } };
  await mkdir(path.join(root, 'plugin/assets'), { recursive: true });
  await mkdir(path.join(root, 'plugin/.claude-plugin'), { recursive: true });
  await mkdir(path.join(root, 'plugin/skills/openjdk-helper'), { recursive: true });
  await writeFile(path.join(root, 'pom.xml'), '<project><parent><version>99.0.0</version></parent><version>0.2.1-SNAPSHOT</version><dependencies><dependency><version>88.0.0</version></dependency></dependencies></project>');
  await writeFile(path.join(root, 'LICENSE'), 'BSD-3-Clause fixture');
  await writeFile(path.join(root, 'plugin/assets/icon.svg'), '<svg xmlns="http://www.w3.org/2000/svg"/>');
  await writeFile(path.join(root, 'plugin/assets/icon.png'), Buffer.from('89504e470d0a1a0a', 'hex'));
  await writeFile(path.join(root, 'plugin/skills/openjdk-helper/SKILL.md'), '---\nname: openjdk-helper\ndescription: Read OpenJDK information\n---\nUse read-only tools.\n');
  const save = async () => {
    await writeFile(path.join(root, 'plugin/plugin.json'), JSON.stringify(manifest)); await writeFile(path.join(root, 'plugin/mcp.json'), JSON.stringify(mcp));
    await writeFile(path.join(root, 'plugin/.claude-plugin/plugin.json'), JSON.stringify(claude)); await writeFile(path.join(root, 'plugin/.mcp.json'), JSON.stringify(claudeMcp));
  };
  await save();
  return { root, manifest, mcp, claude, claudeMcp, save, ui: manifest.extensions['com.openai'].interface, review: manifest.extensions['com.openai'].review };
}

test('build derives direct POM version and includes only whitelisted root entries', async t => {
  const f = await fixture(t);
  for (const name of ['README.md', 'pack.mjs', '.env', 'assets/secret.txt', 'skills/openjdk-helper/test.js']) {
    await writeFile(path.join(f.root, 'plugin', name), 'must not ship');
  }
  const result = await buildPlugin({ root: f.root });
  const entries = unzipSync(await readFile(result.output));
  assert.equal(result.version, '0.2.1-SNAPSHOT');
  assert.deepEqual(Object.keys(entries).sort(), ['LICENSE', 'assets/icon.svg', 'mcp.json', 'plugin.json', 'skills/openjdk-helper/SKILL.md']);
  assert.equal(JSON.parse(Buffer.from(entries['plugin.json']).toString()).version, result.version);
  const first = await readFile(result.output);
  await buildPlugin({ root: f.root });
  assert.deepEqual(await readFile(result.output), first, 'Same inputs should produce identical ZIP bytes');
});

test('version override affects only package and check mode writes no archive', async t => {
  const f = await fixture(t);
  const original = await readFile(path.join(f.root, 'plugin/plugin.json'));
  const checked = await buildPlugin({ root: f.root, version: '1.2.3-rc.1+release', check: true });
  assert.equal(checked.output, null);
  await assert.rejects(access(path.join(f.root, 'target')));
  const built = await buildPlugin({ root: f.root, version: '1.2.3' });
  assert.match(built.output, /openjdk-helper-1\.2\.3-plugin\.zip$/);
  assert.deepEqual(await readFile(path.join(f.root, 'plugin/plugin.json')), original);
});

test('submission reports missing real policy URLs and demo together', async t => {
  const f = await fixture(t);
  await assert.rejects(buildPlugin({ root: f.root, submission: true, check: true }), error =>
    ['privacyPolicyURL', 'termsOfServiceURL', 'demo_recording_url'].every(field => error.message.includes(field)));
  Object.assign(f.ui, { privacyPolicyURL: 'https://www.hirt.se/privacy', termsOfServiceURL: 'https://www.hirt.se/terms' });
  f.review.demo_recording_url = 'https://www.youtube.com/watch?v=example#t=10';
  await f.save();
  await buildPlugin({ root: f.root, submission: true, check: true });
  f.review.test_cases.positive.pop(); await f.save();
  await assert.rejects(buildPlugin({ root: f.root, submission: true }), /exactly 5 positive/);
});

test('requires the portable manifest schema identifiers', async t => {
  const f = await fixture(t);
  delete f.manifest.$schema; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /plugin.*\$schema/);
  f.manifest.$schema = 'https://agent-plugins.org/schemas/1.0.0/plugin.schema.json';
  f.mcp.$schema = 'https://example.org/schema'; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /MCP.*\$schema/);
});

test('rejects invalid versions, private or malformed endpoints, and auth settings', async t => {
  const f = await fixture(t);
  for (const version of ['1.2', '01.2.3', '1.2.3-01', '../1.2.3']) await assert.rejects(buildPlugin({ root: f.root, version, check: true }), /semantic version/);
  const server = f.mcp.mcpServers['openjdk-helper'];
  for (const url of ['http://api.hirt.se/mcp', 'https://127.0.0.1/mcp', 'https://localhost/mcp', 'https://user:secret@api.hirt.se/mcp', 'https://api.hirt.se/mcp?token=secret', 'not a URL']) {
    server.url = url; await f.save(); await assert.rejects(buildPlugin({ root: f.root, check: true }), /HTTPS URL/);
  }
  server.url = 'https://api.hirt.se/openjdk/mcp'; server.headers = { Authorization: 'secret' }; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /headers/);
  delete server.headers; server.extensions['com.openai'].auth.type = 'oauth'; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /auth.type must be none/);
});

test('rejects icon path escapes, missing assets, malformed skills and missing portable fields', async t => {
  const f = await fixture(t);
  f.ui.logo = '../LICENSE'; await f.save(); await assert.rejects(buildPlugin({ root: f.root, check: true }), /reference an image/);
  f.ui.logo = './assets/missing.svg'; await f.save(); await assert.rejects(buildPlugin({ root: f.root, check: true }), /ENOENT/);
  f.ui.logo = './assets/icon.svg'; f.manifest.description = ''; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /name and description/);
  f.manifest.description = 'Read data'; await f.save();
  await writeFile(path.join(f.root, 'plugin/skills/openjdk-helper/SKILL.md'), '---\nname: other\n---\nNo valid description');
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /frontmatter/);
});

test('rejects symlinked skill directories without reading outside the plugin', async t => {
  const f = await fixture(t);
  const outside = path.join(f.root, 'outside'); await mkdir(outside);
  try { await symlink(outside, path.join(f.root, 'plugin/skills/escape'), process.platform === 'win32' ? 'junction' : 'dir'); }
  catch (error) { if (['EPERM', 'EACCES'].includes(error.code)) return t.skip('Symlinks unavailable on this host'); throw error; }
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /Symlink is not allowed/);
});

test('does not overwrite a symlinked output ZIP', async t => {
  const f = await fixture(t);
  await mkdir(path.join(f.root, 'target'));
  const destination = path.join(f.root, 'untouched.txt'); await writeFile(destination, 'unchanged');
  try { await symlink(destination, path.join(f.root, 'target/openjdk-helper-0.2.1-SNAPSHOT-plugin.zip'), 'file'); }
  catch (error) { if (['EPERM', 'EACCES'].includes(error.code)) return t.skip('File symlinks unavailable on this host'); throw error; }
  await assert.rejects(buildPlugin({ root: f.root }), /Output ZIP must not be a symlink/);
  assert.equal(await readFile(destination, 'utf8'), 'unchanged');
});

test('keeps the Claude manifest in step with the portable manifest and out of the archive', async t => {
  const f = await fixture(t);
  const result = await buildPlugin({ root: f.root });
  assert(!Object.keys(unzipSync(await readFile(result.output))).some(name => name.startsWith('.claude-plugin') || name === '.mcp.json'));
  f.claude.version = '1.0.0'; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /must not pin a version/);
  delete f.claude.version; f.claude.name = 'other'; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /same name/);
  f.claude.name = 'openjdk-helper'; f.claude.icon = './assets/icon.svg'; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /PNG or JPEG/);
  f.claude.icon = './assets/missing.png'; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /ENOENT/);
  f.claude.icon = './assets/icon.png';
  const server = f.claudeMcp.mcpServers['openjdk-helper'];
  server.url = 'https://api.hirt.se/other/mcp'; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /same URL/);
  server.url = 'https://api.hirt.se/openjdk/mcp'; server.headers = { Authorization: 'secret' }; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /without headers/);
  delete server.headers; server.type = 'streamable-http'; await f.save();
  await assert.rejects(buildPlugin({ root: f.root, check: true }), /type http/);
  server.type = 'http'; await f.save();
  await buildPlugin({ root: f.root, check: true });
});
