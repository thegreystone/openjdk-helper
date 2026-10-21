#!/usr/bin/env node
// Copyright (C) 2026 Marcus Hirt. See LICENSE for license details.
// npm ci --prefix mcpb
// node mcpb/pack.mjs <version> <suffix> <native-binary> [output.mcpb]

import { chmodSync, closeSync, copyFileSync, mkdirSync, mkdtempSync, openSync, readFileSync,
  readSync, realpathSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { packExtension } from '@anthropic-ai/mcpb/cli';

const platforms = {
  'linux-x86_64': { platform: 'linux', format: 'elf', machine: 0x3e, label: 'Linux x86-64' },
  'linux-aarch64': { platform: 'linux', format: 'elf', machine: 0xb7, label: 'Linux ARM64' },
  'macos-aarch64': { platform: 'darwin', format: 'macho', machine: 0x0100000c, label: 'macOS Apple Silicon' },
  'windows-x86_64': { platform: 'win32', format: 'pe', machine: 0x8664, label: 'Windows x86-64' }
};

function checkBinary(binary, target) {
  if (!statSync(binary).isFile()) throw new Error(`Not a file: ${binary}`);
  const descriptor = openSync(binary, 'r');
  try {
    const header = Buffer.alloc(64);
    if (readSync(descriptor, header, 0, header.length, 0) !== header.length) {
      throw new Error('Native executable is too short');
    }
    let matches = false;
    if (target.format === 'elf') {
      matches = header.subarray(0, 4).equals(Buffer.from([0x7f, 0x45, 0x4c, 0x46]))
        && header[4] === 2 && header[5] === 1 && header.readUInt16LE(18) === target.machine;
    } else if (target.format === 'macho') {
      matches = header.readUInt32LE(0) === 0xfeedfacf && header.readUInt32LE(4) === target.machine;
    } else if (header.toString('ascii', 0, 2) === 'MZ') {
      const peHeader = Buffer.alloc(6);
      const length = readSync(descriptor, peHeader, 0, peHeader.length, header.readUInt32LE(60));
      matches = length === peHeader.length && peHeader.readUInt32LE(0) === 0x00004550
        && peHeader.readUInt16LE(4) === target.machine;
    }
    if (!matches) throw new Error(`Executable does not match ${target.label}: ${binary}`);
  } finally {
    closeSync(descriptor);
  }
}

async function main() {
  const [version, suffix, input, output, ...extra] = process.argv.slice(2);
  if (!version || !suffix || !input || extra.length) {
    throw new Error('Usage: node mcpb/pack.mjs <version> <suffix> <native-binary> [output.mcpb]');
  }
  if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/.test(version)) {
    throw new Error(`Expected a semantic version without a v prefix: ${version}`);
  }
  const target = platforms[suffix];
  if (!target) throw new Error(`Unsupported suffix: ${suffix}. Expected ${Object.keys(platforms).join(', ')}`);
  // The official packer preserves Unix executable permissions only on Unix hosts.
  if (process.platform === 'win32' && target.platform !== 'win32') {
    throw new Error('Pack Linux/macOS bundles on Linux or macOS to preserve executable permissions');
  }

  const here = dirname(fileURLToPath(import.meta.url));
  const project = dirname(here);
  const binary = resolve(input);
  checkBinary(binary, target);
  const destination = resolve(output ?? join(project, 'target', `openjdk-helper-${version}-${suffix}.mcpb`));
  if (!destination.endsWith('.mcpb')) throw new Error('Output filename must end in .mcpb');
  const temporaryRoot = realpathSync(tmpdir());
  const stage = mkdtempSync(join(temporaryRoot, 'openjdk-helper-mcpb-'));
  try {
    const binaryName = target.platform === 'win32' ? 'openjdk-helper.exe' : 'openjdk-helper';
    mkdirSync(join(stage, 'server'));
    const packagedBinary = join(stage, 'server', binaryName);
    copyFileSync(binary, packagedBinary);
    if (process.platform !== 'win32') chmodSync(packagedBinary, 0o755);
    copyFileSync(join(project, 'LICENSE'), join(stage, 'LICENSE'));

    const manifest = JSON.parse(readFileSync(join(here, 'manifest.json'), 'utf8'));
    manifest.version = version;
    manifest.long_description += ` This package is for ${target.label}.`;
    manifest.server.entry_point = `server/${binaryName}`;
    manifest.server.mcp_config.command = '${__dirname}/' + manifest.server.entry_point;
    manifest.compatibility.platforms = [target.platform];
    writeFileSync(join(stage, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');

    if (!await packExtension({ extensionPath: stage, outputPath: destination })) {
      throw new Error('MCPB manifest validation or packaging failed');
    }
    console.log(`Packed ${target.label}: ${destination}`);
  } finally {
    // Delete only the temporary directory created by this invocation.
    if (dirname(stage) !== temporaryRoot || !basename(stage).startsWith('openjdk-helper-mcpb-')) {
      throw new Error(`Refusing to clean unexpected staging path: ${stage}`);
    }
    rmSync(stage, { recursive: true, force: true });
  }
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
