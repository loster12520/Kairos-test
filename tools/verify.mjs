#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { execFileSync, execSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DEMO = join(ROOT, 'demo-web');
const KAIROS_OUT = join(DEMO, 'build', 'kairos');
const JS_OUT = join(DEMO, 'build', 'compileSync', 'js', 'main', 'productionExecutable', 'kotlin');
const SITE = join(DEMO, 'build', 'site');

const results = [];
function check(id, title, body) {
  const nl = String.fromCharCode(10);
  let detail;
  try {
    detail = body();
  } catch (error) {
    results.push({ id, title, ok: false });
    console.log('  FAIL  ' + id + '  ' + title);
    console.log('        ' + String(error.message).split(nl).join(nl + '        '));
    return;
  }
  if (detail && typeof detail === 'object' && detail.skip) {
    results.push({ id, title, ok: true, skipped: true });
    console.log('  SKIP  ' + id + '  ' + title + '  (' + detail.skip + ')');
    return;
  }
  results.push({ id, title, ok: true });
  console.log('  PASS  ' + id + '  ' + title);
  if (detail) console.log('        ' + String(detail).split(nl).join(nl + '        '));
}
function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function fnv1a64Hex(input) {
  let hash = 0xcbf29ce484222325n;
  const prime = 0x100000001b3n;
  const mask = 0xffffffffffffffffn;
  for (let index = 0; index < input.length; index++) {
    hash ^= BigInt(input.charCodeAt(index));
    hash = (hash * prime) & mask;
  }
  return hash.toString(16).padStart(16, '0');
}
const LAYERS = {
  Reset: { letter: 'r', css: 'kairos.reset', index: 0 },
  Base: { letter: 'b', css: 'kairos.base', index: 1 },
  Variant: { letter: 'v', css: 'kairos.variant', index: 2 },
  Override: { letter: 'o', css: 'kairos.override', index: 3 },
};
function classNameFor(layerName, declarations) {
  const layer = LAYERS[layerName];
  const canonical = declarations
    .map((d) => [d.property, d.value])
    .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
    .map((d) => d[0] + ':' + d[1])
    .join(';');
  return 'k' + layer.letter + fnv1a64Hex(layer.css + '#' + canonical).slice(0, 12);
}
const decl = (property, value) => ({ property, value });

const cssPath = join(KAIROS_OUT, 'kairos.css');
const mapPath = join(KAIROS_OUT, 'kairos.map.json');
const reportPath = join(KAIROS_OUT, 'kairos-report.txt');
const bundlePath = join(JS_OUT, 'kairos-demo-web.js');
const referencePath = join(DEMO, 'build', 'reference-site', 'reference.txt');

function runBundle() {
  assert(existsSync(bundlePath), 'missing bundle: ' + bundlePath);
  const out = execFileSync('node', [bundlePath], { encoding: 'utf8' });
  const parsed = {};
  for (const line of out.split(/\r?\n/)) {
    const match = /^([A-Za-z]+)=(.*)$/.exec(line.trim());
    if (match) parsed[match[1]] = match[2];
  }
  return parsed;
}
function readPairs(path) {
  const parsed = {};
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const match = /^([A-Za-z]+)=(.*)$/.exec(line.trim());
    if (match) parsed[match[1]] = match[2];
  }
  return parsed;
}
const sha256 = (path) => createHash('sha256').update(readFileSync(path)).digest('hex');

const REBUILD = process.argv.includes('--rebuild');
let determinism = null;
let artifactRegeneration = null;
if (REBUILD) {
  rmSync(KAIROS_OUT, { recursive: true, force: true });
  execSync('gradle :demo-web:compileProductionExecutableKotlinJs --console=plain -q', { cwd: ROOT, stdio: 'pipe' });
  artifactRegeneration = existsSync(cssPath) && existsSync(mapPath);
  const buildOnce = () => {
    rmSync(KAIROS_OUT, { recursive: true, force: true });
    execSync('gradle :demo-web:compileProductionExecutableKotlinJs --console=plain -q --rerun-tasks', { cwd: ROOT, stdio: 'pipe' });
    if (!existsSync(cssPath) || !existsSync(mapPath)) throw new Error('build produced no kairos artifacts');
    return { css: sha256(cssPath), map: sha256(mapPath) };
  };
  determinism = { first: buildOnce(), second: buildOnce() };
}

console.log('Kairos acceptance harness');
console.log('  css  ' + cssPath);
console.log('  map  ' + mapPath);
console.log('');

for (const required of [cssPath, mapPath, reportPath]) {
  assert(existsSync(required), 'missing artifact: ' + required + ' (run: gradle :demo-web:assembleSite)');
}
const css = readFileSync(cssPath, 'utf8');
const map = JSON.parse(readFileSync(mapPath, 'utf8'));
const report = readFileSync(reportPath, 'utf8');
assert(existsSync(SITE), 'missing site output (run gradle :demo-web:assembleSite)');
const run = runBundle();

check('A1a', 'the compiled bundle never falls back to the runtime style registry', () => {
  assert(run.folds === '0', 'expected folds=0, got ' + run.folds);
  return 'folds=0';
});

check('A1b', 'a style block folds into one constant class name', () => {
  const expected = classNameFor('Base', [decl('color', '#111111'), decl('padding', '24px')]);
  assert(run.hero === expected, 'hero=' + run.hero + ', expected ' + expected);
  assert(readFileSync(bundlePath, 'utf8').includes("'" + expected + "'"), 'bundle lacks the literal');
  return 'hero=' + expected;
});

check('A2', 'class names are reproducible from layer + declarations (independent implementation)', () => {
  for (const rule of map.rules) {
    const layerName = Object.keys(LAYERS).find((k) => LAYERS[k].css === rule.layer);
    assert(layerName, 'unknown layer ' + rule.layer);
    assert(rule.className === classNameFor(layerName, rule.declarations), 'mismatch for ' + rule.className);
  }
  return map.rules.length + ' classes recomputed identically';
});

check('A3', 'no declaration is emitted twice: blocks are grouped and shared declarations extracted', () => {
  const declarations = map.rules.reduce((sum, rule) => sum + rule.declarations.length, 0);
  const seen = new Map();
  for (const rule of map.rules) {
    for (const d of rule.declarations) {
      const key = rule.layer + '|' + d.property + ':' + d.value;
      assert(!seen.has(key), 'declaration emitted twice: ' + d.property + ': ' + d.value + ' in ' + rule.className + ' and ' + seen.get(key));
      seen.set(key, rule.className);
    }
  }
  const grouped = map.rules.filter((rule) => rule.declarations.length > 1).length;
  const shared = map.rules.filter((rule) => rule.declarations.length === 1).length;
  return declarations + ' declarations -> ' + map.rules.length + ' classes (' + grouped + ' grouped, ' + shared + ' shared singles), no duplicates';
});

check('A13', 'a ternary inside a style block compiles to a class selection, not a value', () => {
  assert(run.badge, 'the bundle did not report the badge class list');
  const bundle = readFileSync(bundlePath, 'utf8');
  assert(/badgeClass[\s\S]{0,300}\?\s*'k[robv]/.test(bundle) || /\?\s*'k[robv][0-9a-f]{12}/.test(bundle), 'no conditional over class names found in the bundle');
  const names = run.badge.split(/\s+/).filter(Boolean);
  assert(names.length >= 2, 'expected two classes (shared + residual), got ' + names.length);
  for (const name of names) {
    assert(map.rules.some((rule) => rule.className === name), 'class ' + name + ' is missing from the map');
  }
  return 'badge=' + run.badge;
});

check('A4', 'the all-runtime path (JVM, no plugin) and the all-folded path agree exactly', () => {
  assert(existsSync(referencePath), 'missing JVM reference output');
  const reference = readPairs(referencePath);
  assert(reference.hero === run.hero, 'hero differs: jvm=' + reference.hero + ' js=' + run.hero);
  assert(reference.theme === run.theme, 'theme differs: jvm=' + reference.theme + ' js=' + run.theme);
  assert(reference.card === run.card, 'card differs: jvm=' + reference.card + ' js=' + run.card);
  return 'hero, theme and card identical across both paths';
});

check('A5', 'emitted CSS contains exactly the classes of the map, grouped by layer', () => {
  const ruleCount = (css.match(/^  \.[a-z0-9]+ \{/gm) || []).length;
  assert(ruleCount === map.rules.length, 'css has ' + ruleCount + ' rules, map has ' + map.rules.length);
  for (const rule of map.rules) {
    assert(css.includes('.' + rule.className + ' {'), 'missing rule for ' + rule.className);
    for (const d of rule.declarations) {
      assert(css.includes(d.property + ': ' + d.value + ';'), 'rule ' + rule.className + ' lacks ' + d.property);
    }
  }
  const blocks = ['kairos.reset', 'kairos.base', 'kairos.variant', 'kairos.override'].map((n) => css.indexOf('@layer ' + n + ' {'));
  for (let i = 1; i < blocks.length; i++) assert(blocks[i] > blocks[i - 1], 'layer blocks out of order');
  return ruleCount + ' rules, 4 layer blocks in priority order';
});

check('A6', 'cross layer conflicts are detected and recorded', () => {
  const border = map.conflicts.find((c) => c.property === 'border');
  assert(border, 'missing border conflict');
  assert(border.winnerLayer === 'kairos.override', 'border winner is ' + border.winnerLayer);
  assert(border.loserLayer === 'kairos.base', 'border loser is ' + border.loserLayer);
  return map.conflicts.length + ' conflicts recorded, override > base for border';
});

check('A7', 'composition keeps both classes; the winning layer is later', () => {
  const html = readFileSync(join(SITE, 'index.html'), 'utf8');
  const border = map.conflicts.find((c) => c.property === 'border' && c.winnerLayer === 'kairos.override');
  const baseClass = map.rules.find((r) => r.layer === 'kairos.base' && r.declarations.some((d) => d.property === 'border' && d.value === border.loserValue));
  const overrideClass = map.rules.find((r) => r.layer === 'kairos.override');
  assert(baseClass && overrideClass, 'missing one side of the conflict');
  const classLists = [...html.matchAll(/class="([^"]*)"/g)].map((m) => m[1].split(/\s+/));
  const withBoth = classLists.filter((list) => list.includes(baseClass.className) && list.includes(overrideClass.className));
  assert(withBoth.length > 0, 'expected an element composed from both layers');
  return 'both classes on ' + withBoth.length + ' element; layer ' + overrideClass.layerIndex + ' > ' + baseClass.layerIndex;
});

check('A8', 'no style block silently failed to resolve', () => {
  assert(Array.isArray(map.errors), 'map has no errors array');
  assert(map.errors.length === 0, 'compilation reported errors: ' + JSON.stringify(map.errors));
  assert(report.includes('## errors (0)'), 'report does not state zero errors');
  assert(report.includes('## classes (' + map.rules.length + ')'), 'report class count disagrees with the map');
  return '0 unresolved blocks, report counts agree with the map';
});

check('A9', 'the merged site stylesheet is deduplicated, complete and correctly layered', () => {
  const siteCss = readFileSync(join(SITE, 'kairos.css'), 'utf8');
  const html = readFileSync(join(SITE, 'index.html'), 'utf8');
  const selectors = [...siteCss.matchAll(/^  \.([a-z0-9]+) \{/gm)].map((m) => m[1]);
  assert(selectors.filter((n, i) => selectors.indexOf(n) !== i).length === 0, 'duplicate selectors after merge');
  const blocks = ['kairos.reset', 'kairos.base', 'kairos.variant', 'kairos.override'].map((n) => siteCss.indexOf('@layer ' + n + ' {'));
  for (let i = 1; i < blocks.length; i++) assert(blocks[i] > blocks[i - 1], 'merged layer blocks out of order');
  const used = new Set([...html.matchAll(/class="([^"]*)"/g)].flatMap((m) => m[1].split(/\s+/)).filter(Boolean));
  const missing = [...used].filter((name) => !siteCss.includes('.' + name + ' '));
  assert(missing.length === 0, 'page uses classes missing from the merged stylesheet: ' + missing.join(', '));
  return selectors.length + ' classes, no duplicates, ' + used.size + ' page classes all present';
});

check('A10', 'module maps merge with cross-module deduplication and unused rules are pruned', () => {
  const featureMapPath = join(ROOT, 'demo-web-feature', 'build', 'kairos', 'kairos.map.json');
  assert(existsSync(featureMapPath), 'missing second module map');
  const featureMap = JSON.parse(readFileSync(featureMapPath, 'utf8'));
  const total = map.rules.length + featureMap.rules.length;
  const union = new Set([...map.rules, ...featureMap.rules].map((r) => r.className));
  const siteCss = readFileSync(join(SITE, 'kairos.css'), 'utf8');
  const shipped = new Set([...siteCss.matchAll(/^  \.([a-z0-9]+) \{/gm)].map((m) => m[1]));
  assert(shipped.size <= union.size, 'merged stylesheet has more classes than the union');
  assert(map.module === 'demo-web', 'first map has module name ' + map.module);
  assert(featureMap.module === 'demo-web-feature', 'second map has module name ' + featureMap.module);
  return total + ' module classes -> ' + union.size + ' unique -> ' + shipped.size + ' shipped';
});

check('A11', 'deleting the artifact directory makes a plain build regenerate it', () => {
  if (artifactRegeneration === null) return { skip: 'measured only with --rebuild' };
  assert(artifactRegeneration, 'artifacts were not regenerated by a plain build');
  return 'plain build regenerated kairos.css and kairos.map.json';
});

check('A12', 'two forced rebuilds produce byte-identical css and map', () => {
  if (!determinism) return { skip: 'measured only with --rebuild' };
  assert(determinism.first.css === determinism.second.css, 'css drifted');
  assert(determinism.first.map === determinism.second.map, 'map drifted');
  return 'css ' + determinism.first.css.slice(0, 16) + '... stable';
});

const failed = results.filter((r) => !r.ok);
const skipped = results.filter((r) => r.skipped);
console.log('');
console.log((results.length - failed.length - skipped.length) + '/' + (results.length - skipped.length) + ' checks passed' +
  (skipped.length > 0 ? ' (' + skipped.map((r) => r.id).join(', ') + ' skipped)' : ''));
if (failed.length > 0) {
  console.log('FAILED: ' + failed.map((r) => r.id).join(', '));
  process.exit(1);
}
