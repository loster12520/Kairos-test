#!/usr/bin/env node
// M1 reproducibility: build the same sources from a *different absolute path* and require the
// compiled stylesheet, manifest and report to be byte identical. Requires the framework to be
// published to mavenLocal already (the sample app consumes the plugin by coordinate).
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const COPY = mkdtempSync(join(tmpdir(), 'kairos-repro-'));
const SKIP = ['.git', 'build', '.gradle', '.kotlin', 'node_modules'];

console.log('Kairos cross-directory reproducibility check');
console.log('  source  ' + ROOT);
console.log('  copy    ' + COPY);
console.log('');

function copy() {
  cpSync(ROOT, COPY, {
    recursive: true,
    filter: (source) => {
      const parts = source.split(/[\\/]/);
      return !parts.some((part) => SKIP.includes(part));
    },
  });
}

function build(directory) {
  execFileSync('./gradlew', ['--console=plain', ':demo-web:assembleSite'], {
    cwd: directory,
    stdio: 'pipe',
    env: process.env,
  });
  return join(directory, 'demo-web', 'build', 'kairos', 'js', 'main');
}

const sha = (path) => createHash('sha256').update(readFileSync(path)).digest('hex');
const ARTIFACTS = ['kairos.css', 'kairos.map.json', 'kairos-report.txt'];

try {
  copy();
  // The copy reuses mavenLocal; publishing from the original path keeps the coordinates stable.
  execFileSync('./gradlew', ['-PframeworkOnly=true', '--console=plain', 'publishToMavenLocal'], {
    cwd: ROOT,
    stdio: 'pipe',
    env: process.env,
  });
  const original = join(ROOT, 'demo-web', 'build', 'kairos', 'js', 'main');
  build(ROOT);
  const copyOut = build(COPY);

  let failures = 0;
  for (const name of ARTIFACTS) {
    const a = join(original, name);
    const b = join(copyOut, name);
    if (!existsSync(a) || !existsSync(b)) {
      console.log('  FAIL  ' + name + '  missing in one of the builds');
      failures += 1;
      continue;
    }
    const left = sha(a);
    const right = sha(b);
    if (left !== right) {
      console.log('  FAIL  ' + name + '  differs  ' + left + ' != ' + right);
      failures += 1;
    } else {
      console.log('  PASS  ' + name + '  ' + left.slice(0, 16) + '...');
    }
  }
  console.log('');
  if (failures > 0) process.exit(1);
  console.log('reproducible: ' + ARTIFACTS.length + '/' + ARTIFACTS.length + ' artifacts byte identical across directories');
} finally {
  rmSync(COPY, { recursive: true, force: true });
}
