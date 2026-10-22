#!/usr/bin/env node
// Copyright (C) 2026 Marcus Hirt. See LICENSE for license details.
// Local structural checks only: no official schema fetch or submission certification.
import { lstat, mkdir, readFile, readdir, realpath, writeFile } from 'node:fs/promises';
import { isIP } from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { zipSync } from 'fflate';

const here = path.dirname(fileURLToPath(import.meta.url));
const text = value => typeof value === 'string' && value.trim().length > 0;
const requireValue = (condition, message) => { if (!condition) throw new Error(message); };

function semver(value) {
  const match = typeof value === 'string' && value.match(/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([\dA-Za-z-]+(?:\.[\dA-Za-z-]+)*))?(?:\+[\dA-Za-z-]+(?:\.[\dA-Za-z-]+)*)?$/);
  requireValue(match && (!match[4] || match[4].split('.').every(part => !/^\d+$/.test(part) || /^(0|[1-9]\d*)$/.test(part))),
    `Invalid semantic version: ${value}`);
  return value;
}

// Read the direct Maven project/version, excluding parent and dependency versions.
function projectVersion(xml) {
  const stack = [];
  let version = '';
  for (const token of xml.replace(/<!--[\s\S]*?-->/g, '').match(/<[^>]*>|[^<]+/g) ?? []) {
    if (token.startsWith('<?') || token.startsWith('<!')) continue;
    if (token.startsWith('</')) stack.pop();
    else if (token.startsWith('<')) {
      if (!token.endsWith('/>')) stack.push(token.match(/^<([\w:.-]+)/)?.[1]?.split(':').at(-1));
    } else if (stack.join('/') === 'project/version') version += token;
  }
  return semver(version.trim());
}

function https(value, label, endpoint = false) {
  let url;
  try { url = new URL(value); } catch { throw new Error(`${label} must be a public HTTPS URL`); }
  const host = url.hostname.replace(/^\[|\]$/g, '').replace(/\.$/, '').toLowerCase();
  requireValue(url.protocol === 'https:' && !url.username && !url.password && (!endpoint || !url.hash && !url.search)
    && !isIP(host) && host.includes('.') && !/(^|\.)(localhost|local|internal|invalid|test|example|lan)$/.test(host),
  `${label} must be a public HTTPS URL without credentials${endpoint ? ', query or fragment' : ''}`);
}

async function safeFile(root, relative) {
  requireValue(text(relative) && !relative.includes('\\') && !path.isAbsolute(relative)
    && relative.split('/').every(part => part && part !== '.' && part !== '..'), `Unsafe package path: ${relative}`);
  let current = root;
  for (const part of relative.split('/')) {
    current = path.join(current, part);
    requireValue(!(await lstat(current)).isSymbolicLink(), `Symlink is not allowed: ${relative}`);
  }
  const resolved = await realpath(current);
  requireValue(resolved.startsWith((await realpath(root)) + path.sep) && (await lstat(current)).isFile(),
    `Package path is not a file inside the source root: ${relative}`);
  return readFile(current);
}

function validateManifest(manifest, mcp, submission) {
  requireValue(manifest.$schema === 'https://agent-plugins.org/schemas/1.0.0/plugin.schema.json', 'plugin.json must reference the portable plugin 1.0.0 $schema');
  requireValue(mcp.$schema === 'https://agent-plugins.org/schemas/1.0.0/mcp.schema.json', 'mcp.json must reference the portable MCP 1.0.0 $schema');
  requireValue(/^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(manifest.name ?? '') && text(manifest.description), 'Plugin name and description are required');
  requireValue(text(manifest.author?.name) && text(manifest.license), 'Plugin author.name and license are required');
  const servers = mcp.mcpServers && Object.entries(mcp.mcpServers);
  requireValue(servers?.length === 1, 'mcp.json must declare exactly one remote MCP server');
  const [name, server] = servers[0];
  requireValue(name === manifest.name && server?.type === 'streamable-http', 'MCP server must match the plugin name and use streamable-http');
  requireValue(Object.keys(server).every(key => ['type', 'url', 'extensions'].includes(key)), 'MCP server must not contain headers, credentials or local process settings');
  https(server.url, 'MCP server URL', true);
  const auth = server.extensions?.['com.openai']?.auth;
  requireValue(auth?.type === 'none' && Object.keys(auth).length === 1, 'MCP auth.type must be none, without credentials');
  const openai = manifest.extensions?.['com.openai'];
  const ui = openai?.interface;
  requireValue(text(ui?.displayName) && text(ui?.shortDescription), 'OpenAI interface displayName and shortDescription are required');
  for (const field of ['websiteURL', 'supportURL', 'privacyPolicyURL', 'termsOfServiceURL']) {
    if (ui[field] !== undefined) https(ui[field], `interface.${field}`);
  }
  if (submission) {
    const missing = ['websiteURL', 'supportURL', 'privacyPolicyURL', 'termsOfServiceURL'].filter(field => !text(ui[field]));
    if (!text(openai.review?.demo_recording_url)) missing.push('review.demo_recording_url');
    requireValue(!missing.length, `Submission metadata missing: ${missing.join(', ')}. Supply real published policies and a demo recording before submission.`);
    https(openai.review.demo_recording_url, 'review.demo_recording_url');
    for (const [kind, count] of [['positive', 5], ['negative', 3]]) {
      const cases = openai.review?.test_cases?.[kind];
      requireValue(Array.isArray(cases) && cases.length === count, `Submission requires exactly ${count} ${kind} test cases`);
      requireValue(cases.every(item => text(item.description) && text(item.prompt)
        && (kind !== 'positive' || text(item.tools_triggered) && text(item.expected_behavior))), `Incomplete ${kind} review test case`);
    }
  }
  return ui;
}

// The same directory is also a Claude plugin: .claude-plugin/plugin.json and .mcp.json must describe the same
// plugin and server. Claude installs the folder from Git and tracks commits, so that manifest must not pin a version.
function validateClaude(manifest, server, claude, claudeMcp) {
  requireValue(claude.name === manifest.name && text(claude.description), 'Claude plugin.json must use the same name and have a description');
  requireValue(claude.version === undefined, 'Claude plugin.json must not pin a version; Git-backed installs track commits');
  const servers = claudeMcp.mcpServers && Object.entries(claudeMcp.mcpServers);
  requireValue(servers?.length === 1 && servers[0][0] === manifest.name, 'Claude .mcp.json must declare exactly one server named after the plugin');
  const [, claudeServer] = servers[0];
  requireValue(claudeServer?.type === 'http' && claudeServer.url === server.url && Object.keys(claudeServer).every(key => ['type', 'url'].includes(key)),
    'Claude .mcp.json server must use type http and the same URL as mcp.json, without headers or credentials');
  if (claude.icon !== undefined) requireValue(/^\.\/assets\/[A-Za-z0-9_.-]+\.(png|jpg|jpeg)$/.test(claude.icon), 'Claude icon must reference a PNG or JPEG in assets/');
  return claude.icon?.replace(/^\.\//, '');
}

export async function buildPlugin({ root = path.dirname(here), version, submission = false, check = false } = {}) {
  root = path.resolve(root);
  requireValue(!(await lstat(path.join(root, 'plugin'))).isSymbolicLink(), 'Plugin source directory must not be a symlink');
  const manifest = JSON.parse(await safeFile(root, 'plugin/plugin.json'));
  const mcp = JSON.parse(await safeFile(root, 'plugin/mcp.json'));
  // The committed plugin.json version is the most recent release, which is also what the hosted service runs. A release
  // build passes --version from the tag and must find that exact version committed, because Git-backed marketplace
  // installs read the committed file. Development builds name the ZIP after the POM snapshot version instead.
  const selected = semver(version ?? projectVersion((await safeFile(root, 'pom.xml')).toString('utf8')));
  requireValue(/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(manifest.version ?? ''),
    'plugin.json version must be the most recent release version (x.y.z, no snapshot or prerelease)');
  requireValue(version === undefined || manifest.version === version,
    `plugin.json version ${manifest.version} does not match release version ${version}; bump it before tagging`);
  manifest.version = selected;
  const ui = validateManifest(manifest, mcp, submission);
  const claudeIcon = validateClaude(manifest, Object.values(mcp.mcpServers)[0],
    JSON.parse(await safeFile(root, 'plugin/.claude-plugin/plugin.json')), JSON.parse(await safeFile(root, 'plugin/.mcp.json')));
  if (claudeIcon) await safeFile(root, `plugin/${claudeIcon}`);
  const files = { 'plugin.json': Buffer.from(JSON.stringify(manifest, null, 2) + '\n'),
    'mcp.json': Buffer.from(JSON.stringify(mcp, null, 2) + '\n'), LICENSE: await safeFile(root, 'LICENSE') };
  for (const field of ['composerIcon', 'logo']) {
    const icon = ui[field]?.replace(/^\.\//, '');
    requireValue(/^assets\/[A-Za-z0-9_.-]+\.(svg|png|jpg|jpeg|webp)$/.test(icon ?? ''), `interface.${field} must reference an image in assets/`);
    files[icon] = await safeFile(root, `plugin/${icon}`);
  }
  const skillsPath = path.join(root, 'plugin', 'skills');
  requireValue(!(await lstat(skillsPath)).isSymbolicLink(), 'Skills directory must not be a symlink');
  for (const entry of await readdir(skillsPath, { withFileTypes: true })) {
    requireValue(!entry.isSymbolicLink(), `Symlink is not allowed: skills/${entry.name}`);
    if (!entry.isDirectory()) continue;
    const name = `skills/${entry.name}/SKILL.md`;
    let content;
    try { content = await safeFile(root, `plugin/${name}`); } catch (error) { if (error.code === 'ENOENT') continue; throw error; }
    const header = content.toString('utf8').match(/^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/)?.[1];
    const skillName = header?.match(/^name:\s*(.+)$/m)?.[1]?.trim().replace(/^['"]|['"]$/g, '');
    requireValue(/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(entry.name) && skillName === entry.name
      && /^description:\s*\S.+$/m.test(header ?? ''), `Invalid name/description frontmatter in ${name}`);
    files[name] = content;
  }
  requireValue(Object.keys(files).some(name => name.startsWith('skills/')), 'At least one SKILL.md is required');
  const names = Object.keys(files).sort();
  const output = path.join(root, 'target', `openjdk-helper-${selected}-plugin.zip`);
  if (!check) {
    await mkdir(path.dirname(output), { recursive: true });
    requireValue(!(await lstat(path.dirname(output))).isSymbolicLink(), 'Output directory must not be a symlink');
    try { requireValue(!(await lstat(output)).isSymbolicLink(), 'Output ZIP must not be a symlink'); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
    const archive = Object.fromEntries(names.map(name => [name, [files[name], { mtime: new Date(1980, 0, 1), os: 3, attrs: 0o100644 << 16 }]]));
    await writeFile(output, zipSync(archive, { level: 9 }));
  }
  return { version: selected, files: names, output: check ? null : output, submission };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const { values } = parseArgs({ options: { version: { type: 'string' }, submission: { type: 'boolean' }, check: { type: 'boolean' } } });
    const result = await buildPlugin(values);
    console.log(`${result.output ? 'Packed ' + result.output : 'Structural checks passed'} (${result.files.length} files, version ${result.version}).`);
    console.log('Local structural checks do not certify official schema compliance or submission approval.');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
