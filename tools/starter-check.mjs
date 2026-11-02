#!/usr/bin/env node
// M4/M5 acceptance: `kairos init` produces a project that builds and runs with no hand written
// CSS, using only the published plugin and coordinates.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const WORK = join(ROOT, 'build', 'starter-check');
const STATIC = join(WORK, 'static-starter');
const KTOR = join(WORK, 'ktor-starter');
const INCREMENTAL = join(WORK, 'incremental-starter');

const results = [];
function check(id, title, body) {
  try {
    const detail = body();
    results.push({ id, ok: true });
    console.log('  PASS  ' + id + '  ' + title + (detail ? '  -> ' + detail : ''));
  } catch (error) {
    results.push({ id, ok: false });
    console.log('  FAIL  ' + id + '  ' + title + '  -> ' + error.message);
  }
}
function assert(condition, message) {
  if (!condition) throw new Error(message);
}
function gradle(...args) {
  return execFileSync('./gradlew', ['--console=plain', ...args], { cwd: ROOT, stdio: 'pipe', encoding: 'utf8' });
}

console.log('Kairos starter check');
console.log('  work  ' + WORK);
console.log('');

rmSync(WORK, { recursive: true, force: true });

check('S1', 'kairos init writes a buildable static starter', () => {
  execFileSync('./gradlew', ['-q', '--console=plain', ':kairos-cli:run', '--args=init ' + STATIC], {
    cwd: ROOT,
    stdio: 'pipe',
  });
  for (const file of ['settings.gradle.kts', 'build.gradle.kts', 'src/main/kotlin/Main.kt', 'README.md']) {
    assert(existsSync(join(STATIC, file)), 'missing generated file ' + file);
  }
  return '4 files generated';
});

check('S2', 'the static starter runs and ships its compiled stylesheet', () => {
  // assembleSite depends on run, so one invocation renders the page and copies the stylesheet.
  const runOutput = execFileSync('./gradlew', ['-p', STATIC, '--console=plain', 'assembleSite'], {
    cwd: ROOT,
    stdio: 'pipe',
    encoding: 'utf8',
  });
  const renderedLine = runOutput.split('\n').find((line) => line.includes('rendered ')) || '';
  const index = join(STATIC, 'build', 'site', 'index.html');
  const css = join(STATIC, 'build', 'site', 'kairos.css');
  assert(existsSync(index), 'index.html missing (run said: ' + renderedLine.trim() + ')');
  assert(existsSync(css), 'kairos.css missing');
  const html = readFileSync(index, 'utf8');
  const stylesheet = readFileSync(css, 'utf8');
  const classes = [...html.matchAll(/class="([^"]*)"/g)].flatMap((match) => match[1].split(/\s+/)).filter(Boolean);
  assert(classes.length > 0, 'generated page has no class names');
  const missing = classes.filter((name) => !stylesheet.includes('.' + name));
  assert(missing.length === 0, 'classes missing from stylesheet: ' + missing.join(', '));
  assert(!/<[^>]+ style="/.test(html), 'generated page uses inline styles');
  return classes.length + ' compiled classes';
});

check('S3', 'kairos init also writes a compilable ktor-htmx starter', () => {
  execFileSync('./gradlew', ['-q', '--console=plain', ':kairos-cli:run', '--args=init ' + KTOR + ' --template ktor-htmx'], {
    cwd: ROOT,
    stdio: 'pipe',
  });
  execFileSync('./gradlew', ['-p', KTOR, '--console=plain', 'classes'], { cwd: ROOT, stdio: 'pipe' });
  const source = readFileSync(join(KTOR, 'src', 'main', 'kotlin', 'Main.kt'), 'utf8');
  assert(source.includes('embeddedServer'), 'ktor starter does not start a server');
  assert(source.includes('page('), 'ktor starter does not render a Kairos page');
  return 'ktor starter compiles';
});

check('S5', 'kairos init writes an incremental ktor starter', () => {
  execFileSync('./gradlew', ['-q', '--console=plain', ':kairos-cli:run', '--args=init ' + INCREMENTAL + ' --template ktor-incremental'], {
    cwd: ROOT,
    stdio: 'pipe',
  });
  execFileSync('./gradlew', ['-p', INCREMENTAL, '--console=plain', 'classes'], { cwd: ROOT, stdio: 'pipe' });
  const source = readFileSync(join(INCREMENTAL, 'src', 'main', 'kotlin', 'Main.kt'), 'utf8');
  assert(source.includes('embeddedServer'), 'incremental starter does not start a server');
  assert(source.includes('existingPage'), 'incremental starter lost the pre-existing route');
  assert(source.includes('pricingPage'), 'incremental starter has no Kairos page');
  return 'existing route + Kairos route compile together';
});

check('S4', 'kairos check validates the generated artifacts', () => {
  const report = execFileSync(
    './gradlew',
    ['-q', '--console=plain', ':kairos-cli:run', '--args=check --map ' + join(STATIC, 'build', 'site', 'kairos.map.json') +
      ' --css ' + join(STATIC, 'build', 'site', 'kairos.css')],
    { cwd: ROOT, stdio: 'pipe', encoding: 'utf8' },
  );
  assert(report.includes('check passed'), 'check did not pass: ' + report);
  return report.trim().split('\n')[0];
});

const failed = results.filter((result) => !result.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' checks passed');
if (failed.length > 0) process.exit(1);
