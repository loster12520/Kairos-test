#!/usr/bin/env node
// M2 acceptance: a component library is consumed as a binary dependency. Its compiled stylesheet
// travels inside the jar, the consumer's Gradle plugin collects it and merges it with the local
// stylesheet; no source access, no manual CSS wiring.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const LIBRARY = join(ROOT, 'examples', 'component-library');
const CONSUMER = join(ROOT, 'examples', 'consumer-library');
const CONSUMER_SITE = join(CONSUMER, 'build', 'site');
const JAR = join(process.env.HOME || '', '.m2', 'repository', 'dev', 'kairos', 'examples', 'component-library', '0.1.0', 'component-library-0.1.0.jar');

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

console.log('Kairos binary component library check');
console.log('  library  ' + LIBRARY);
console.log('  consumer ' + CONSUMER);
console.log('');

check('L1', 'the library jar embeds its compiled stylesheet and manifest', () => {
  execFileSync('./gradlew', ['-p', LIBRARY, '--console=plain', '-q', 'publishToMavenLocal'], { cwd: ROOT, stdio: 'pipe' });
  assert(existsSync(JAR), 'published jar missing: ' + JAR);
  const entries = execFileSync('jar', ['tf', JAR], { encoding: 'utf8' });
  for (const required of ['META-INF/kairos/component-library/jvm-main/kairos.css', 'META-INF/kairos/component-library/jvm-main/kairos.map.json']) {
    assert(entries.includes(required), 'jar lacks ' + required);
  }
  return 'css + manifest inside the jar';
});

check('L2', 'the consumer builds without touching framework or library sources', () => {
  rmSync(join(CONSUMER, 'build'), { recursive: true, force: true });
  execFileSync('./gradlew', ['-p', CONSUMER, '--console=plain', '--refresh-dependencies', 'site'], { cwd: ROOT, stdio: 'pipe' });
  assert(existsSync(join(CONSUMER_SITE, 'index.html')), 'consumer page missing');
  assert(existsSync(join(CONSUMER_SITE, 'kairos.css')), 'merged stylesheet missing');
  const build = readFileSync(join(CONSUMER, 'build.gradle.kts'), 'utf8');
  assert(!build.includes('-Xplugin'), 'consumer wires the compiler plugin by hand');
  assert(!build.includes('kairos-cli'), 'consumer runs the merge itself instead of using the plugin');
  return 'plugin-only wiring';
});

check('L3', 'the merged stylesheet contains the library rules, including its hover rule', () => {
  const css = readFileSync(join(CONSUMER_SITE, 'kairos.css'), 'utf8');
  const map = JSON.parse(readFileSync(join(CONSUMER_SITE, 'kairos.map.json'), 'utf8'));
  assert(map.modules.includes('component-library'), 'merged manifest lost the library module');
  assert(map.modules.includes('consumer-library'), 'merged manifest lost the consumer module');
  const libraryRules = map.rules.filter((rule) =>
    rule.sources.some((source) => source.file.includes('componentlib') || source.file.includes('Card.kt')),
  );
  assert(libraryRules.length >= 2, 'expected at least two library rules, got ' + libraryRules.length);
  for (const rule of libraryRules) {
    assert(css.includes('.' + rule.className), 'merged css lacks library class ' + rule.className);
  }
  assert(/:hover \{/.test(css), 'merged css lost the library hover rule');
  return libraryRules.length + ' library rules merged';
});

check('L4', 'the page uses the component classes and the merged stylesheet covers them', () => {
  const html = readFileSync(join(CONSUMER_SITE, 'index.html'), 'utf8');
  const css = readFileSync(join(CONSUMER_SITE, 'kairos.css'), 'utf8');
  const classes = [...html.matchAll(/class="([^"]*)"/g)].flatMap((match) => match[1].split(/\s+/)).filter(Boolean);
  assert(classes.length >= 3, 'expected the page to carry the component classes');
  const missing = classes.filter((name) => !css.includes('.' + name));
  assert(missing.length === 0, 'page classes missing from merged css: ' + missing.join(', '));
  return classes.length + ' classes, all merged';
});

check('L5', 'the merged manifest records per-module coverage', () => {
  const map = JSON.parse(readFileSync(join(CONSUMER_SITE, 'kairos.map.json'), 'utf8'));
  assert(map.coverage, 'manifest has no coverage object');
  assert(map.coverage.rules === map.rules.length, 'coverage rule count disagrees with the manifest');
  assert(map.coverage.fallbackBlocks === 0, 'unexpected fallback blocks: ' + map.coverage.fallbackBlocks);
  return map.coverage.rules + ' rules, ' + map.coverage.conditionalRules + ' conditional';
});

const failed = results.filter((result) => !result.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' checks passed');
if (failed.length > 0) process.exit(1);
