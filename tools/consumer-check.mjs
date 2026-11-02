#!/usr/bin/env node
// M1 acceptance: an empty out-of-tree project must build with only published coordinates and
// the `dev.kairos` plugin id. No -Xplugin flags, no project() dependencies, no absolute paths
// in the manifest.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const CONSUMER = join(ROOT, 'examples', 'consumer-static');
const BUILD = join(CONSUMER, 'build');
const SITE = join(BUILD, 'site');

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

console.log('Kairos clean-room consumer check');
console.log('  project  ' + CONSUMER);
console.log('');

check('C0', 'the consumer build file has zero manual compiler plugin wiring', () => {
  const build = readFileSync(join(CONSUMER, 'build.gradle.kts'), 'utf8');
  const settings = readFileSync(join(CONSUMER, 'settings.gradle.kts'), 'utf8');
  assert(build.includes('id("dev.kairos")'), 'missing the dev.kairos plugin id');
  assert(!build.includes('-Xplugin'), 'build file passes -Xplugin by hand');
  assert(!build.includes('kairos-compiler-plugin'), 'build file references the compiler plugin jar');
  const source = readFileSync(join(CONSUMER, 'src', 'main', 'kotlin', 'consumer', 'Main.kt'), 'utf8');
  assert(!source.includes('dev.kairos.compiler'), 'source imports compiler internals');
  assert(!settings.includes('includeBuild'), 'consumer must not include the framework build');
  return 'plugin id only';
});

check('C1', 'a clean consumer build succeeds from published artifacts', () => {
  rmSync(BUILD, { recursive: true, force: true });
  execFileSync('./gradlew', ['-p', CONSUMER, '--console=plain', 'site'], {
    cwd: ROOT,
    stdio: 'pipe',
    env: process.env,
  });
  assert(existsSync(join(SITE, 'index.html')), 'site/index.html missing');
  assert(existsSync(join(SITE, 'kairos.css')), 'site/kairos.css missing');
  return 'gradlew -p examples/consumer-static site';
});

check('C2', 'the page only references classes that exist in the emitted stylesheet', () => {
  const html = readFileSync(join(SITE, 'index.html'), 'utf8');
  const css = readFileSync(join(SITE, 'kairos.css'), 'utf8');
  const classes = [...html.matchAll(/class="([^"]*)"/g)].flatMap((m) => m[1].split(/\s+/)).filter(Boolean);
  assert(classes.length > 0, 'no classes on the page');
  const missing = classes.filter((name) => !css.includes('.' + name + ' '));
  assert(missing.length === 0, 'missing from stylesheet: ' + missing.join(', '));
  assert(!/<[^>]+ style="/.test(html), 'page uses inline styles');
  return classes.length + ' classes, all styled';
});

check('C3', 'the manifest stores project relative source paths', () => {
  const map = JSON.parse(readFileSync(join(SITE, 'kairos.map.json'), 'utf8'));
  assert(map.rules.length > 0, 'manifest has no rules');
  for (const rule of map.rules) {
    for (const source of rule.sources) {
      assert(!source.file.startsWith('/'), 'absolute source path: ' + source.file);
      assert(!/^[A-Za-z]:[\\/]/.test(source.file), 'windows absolute source path: ' + source.file);
      assert(source.file.startsWith('src/'), 'unexpected source root: ' + source.file);
    }
  }
  return map.rules.length + ' rules, ' + map.module + ' module';
});

check('C4', 'the stylesheet declares the full layer order and emits blocks in that order', () => {
  const css = readFileSync(join(SITE, 'kairos.css'), 'utf8');
  const layers = ['kairos.reset', 'kairos.base', 'kairos.variant', 'kairos.override'];
  const declaration = '@layer ' + layers.join(', ') + ';';
  assert(css.includes(declaration), 'missing layer order declaration: ' + declaration);
  let previous = css.indexOf(declaration);
  for (const layer of layers) {
    const index = css.indexOf('@layer ' + layer + ' {');
    if (index < 0) continue; // a layer with no rules is legitimately absent
    assert(index > previous, 'layer ' + layer + ' is out of order');
    previous = index;
  }
  return layers.join(' < ');
});

const failed = results.filter((result) => !result.ok);
console.log('');
console.log(results.length - failed.length + '/' + results.length + ' checks passed');
if (failed.length > 0) process.exit(1);
