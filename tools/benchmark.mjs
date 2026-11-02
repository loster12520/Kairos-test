#!/usr/bin/env node
// Size and time budgets. The point is not micro accuracy: it is that a regression in the
// compiled output or the build time fails loudly instead of being noticed after release.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SITE = join(ROOT, 'demo-web', 'build', 'site');
const KAIROS_OUT = join(ROOT, 'demo-web', 'build', 'kairos', 'js', 'main');

const BUDGETS = {
  kairosCssBytes: 16 * 1024,
  appBundleBytes: 160 * 1024,
  siteHtmlBytes: 32 * 1024,
  compileMillis: 180_000,
};

function size(path) {
  return existsSync(path) ? statSync(path).size : 0;
}

console.log('Kairos benchmark');
const started = Date.now();
execFileSync('./gradlew', ['--console=plain', '-q', ':demo-web:compileProductionExecutableKotlinJs', '--rerun-tasks'], {
  cwd: ROOT,
  stdio: 'pipe',
});
const compileMillis = Date.now() - started;

const metrics = {
  kairosCssBytes: size(join(SITE, 'kairos.css')) || size(join(KAIROS_OUT, 'kairos.css')),
  appBundleBytes: size(join(SITE, 'kairos-demo-web.js')),
  siteHtmlBytes: size(join(SITE, 'index.html')),
  compileMillis,
};
const manifestPath = join(SITE, 'kairos.map.json');
if (existsSync(manifestPath)) {
  const map = JSON.parse(readFileSync(manifestPath, 'utf8'));
  metrics.rules = map.rules.length;
  metrics.conditionalRules = map.coverage ? map.coverage.conditionalRules : 0;
  metrics.fallbackBlocks = map.coverage ? map.coverage.fallbackBlocks : 0;
}

console.table(metrics);
writeFileSync(join(ROOT, 'build', 'benchmark.json'), JSON.stringify(metrics, null, 2));

const failures = [];
for (const [name, budget] of Object.entries(BUDGETS)) {
  const value = metrics[name];
  if (value > budget) failures.push(name + ' = ' + value + ' exceeds budget ' + budget);
  else console.log('  ok   ' + name.padEnd(18) + value + ' / ' + budget);
}
if (failures.length > 0) {
  console.log('');
  failures.forEach((failure) => console.log('  FAIL ' + failure));
  process.exit(1);
}
console.log('');
console.log('budgets passed');
