#!/usr/bin/env node
// Browser level acceptance: load the assembled site in a real DOM, drive the reactive bindings
// and check that the only DOM mutations are class names and text.
import { JSDOM, VirtualConsole } from 'jsdom';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SITE = join(ROOT, 'demo-web', 'build', 'site');
const INDEX = join(SITE, 'index.html');
const CSS = join(SITE, 'kairos.css');

const results = [];
function check(id, title, body) {
  const nl = String.fromCharCode(10);
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
  reset: { letter: 'r', css: 'kairos.reset' },
  base: { letter: 'b', css: 'kairos.base' },
  variant: { letter: 'v', css: 'kairos.variant' },
  override: { letter: 'o', css: 'kairos.override' },
};
function cls(layer, declarations) {
  const meta = LAYERS[layer];
  const canonical = declarations
    .map((d) => [d.property, d.value])
    .sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0))
    .map((d) => d[0] + ':' + d[1])
    .join(';');
  return 'k' + meta.letter + fnv1a64Hex(meta.css + '#' + canonical).slice(0, 12);
}

const LIGHT = cls('variant', [{ property: 'background-color', value: '#f7f7f8' }, { property: 'color', value: '#16181d' }]);
const DARK = cls('variant', [{ property: 'background-color', value: '#12141a' }, { property: 'color', value: '#eef0f4' }]);
const GAP_COMFORTABLE = cls('base', [{ property: 'gap', value: '24px' }]);
const GAP_COMPACT = cls('base', [{ property: 'gap', value: '8px' }]);

assert(existsSync(INDEX), 'site not assembled: ' + INDEX);
const css = readFileSync(CSS, 'utf8');

console.log('Kairos browser check');
console.log('  page  ' + INDEX);
console.log('');

const scriptErrors = [];
const consoleLines = [];
const virtualConsole = new VirtualConsole();
virtualConsole.on('jsdomError', (error) => scriptErrors.push(String(error && error.message ? error.message : error)));
virtualConsole.on('error', (message) => scriptErrors.push(String(message)));
virtualConsole.on('log', (message) => consoleLines.push(String(message)));

const dom = new JSDOM(readFileSync(INDEX, 'utf8'), {
  runScripts: 'dangerously',
  resources: 'usable',
  url: pathToFileURL(INDEX).href,
  pretendToBeVisual: true,
  virtualConsole,
});

await new Promise((resolve) => {
  if (dom.window.document.readyState === 'complete') resolve();
  else dom.window.addEventListener('load', resolve);
});
await new Promise((resolve) => setTimeout(resolve, 300));

const { document, MouseEvent } = dom.window;
const html = document.documentElement;
const main = document.querySelector('[data-kairos-id="page-main"]');
const themeButton = document.querySelector('[data-kairos-id="theme-toggle"]');
const densityButton = document.querySelector('[data-kairos-id="density-toggle"]');
const counterValue = document.querySelector('[data-kairos-id="counter-value"]');
const counterButton = document.querySelector('[data-kairos-id="counter-increment"]');
const click = (element) => element.dispatchEvent(new MouseEvent('click', { bubbles: true }));

check('B1', 'the static page ships the compiled variant class', () => {
  assert(html.classList.contains(LIGHT), 'html lacks ' + LIGHT + ' (has: ' + html.className + ')');
  return LIGHT;
});

check('B2', 'every interactive control exists in the static markup', () => {
  for (const [name, element] of [['theme-toggle', themeButton], ['density-toggle', densityButton], ['counter-increment', counterButton], ['counter-value', counterValue]]) {
    assert(element, 'missing ' + name);
  }
  return 'theme, density, counter';
});

check('B3', 'the compiled stylesheet contains every class the page uses', () => {
  for (const name of [LIGHT, DARK, GAP_COMFORTABLE, GAP_COMPACT]) {
    assert(css.includes('.' + name + ' {'), 'missing rule for ' + name);
  }
  return 'light, dark, comfortable, compact';
});

check('B4', 'clicking the theme button swaps the light class group for the dark one', () => {
  const names = () => html.className.trim().split(/\s+/).filter(Boolean);
  const before = names().length;
  click(themeButton);
  assert(html.classList.contains(DARK), 'dark class not applied (html: ' + html.className + ')');
  assert(!html.classList.contains(LIGHT), 'light class still present');
  assert(names().length === before, 'class count changed');
  click(themeButton);
  assert(html.classList.contains(LIGHT), 'second click did not restore the light class');
  return LIGHT + ' <-> ' + DARK;
});

check('B5', 'clicking the density button swaps one class on <main>', () => {
  assert(main.classList.contains(GAP_COMFORTABLE), 'main lacks the comfortable class');
  click(densityButton);
  assert(main.classList.contains(GAP_COMPACT), 'compact class not applied');
  assert(!main.classList.contains(GAP_COMFORTABLE), 'comfortable class still present');
  click(densityButton);
  assert(main.classList.contains(GAP_COMFORTABLE), 'second click did not restore comfortable');
  return GAP_COMFORTABLE + ' <-> ' + GAP_COMPACT;
});

check('B6', 'the runtime never writes an inline style', () => {
  const withStyle = [...document.querySelectorAll('[style]')];
  assert(withStyle.length === 0, 'elements carry inline style: ' + withStyle.map((e) => e.tagName).join(','));
  return '0 elements with a style attribute';
});

check('B7', 'the page runs its scripts without errors and reports an empty style registry', () => {
  assert(scriptErrors.length === 0, 'script errors: ' + scriptErrors.join(' | '));
  const folds = consoleLines.find((line) => line.startsWith('folds='));
  assert(folds, 'the bundle never reported the registry size');
  assert(folds === 'folds=0', 'the registry is not empty: ' + folds);
  return 'no script errors, ' + folds;
});

check('B8', 'the reactive binding updates only the text node it owns', () => {
  const before = counterValue.textContent.trim();
  click(counterButton);
  const afterOne = counterValue.textContent.trim();
  click(counterButton);
  const afterTwo = counterValue.textContent.trim();
  assert(before === '0', 'expected the counter to start at 0, got ' + before);
  assert(afterOne === '1', 'first click produced ' + afterOne);
  assert(afterTwo === '2', 'second click produced ' + afterTwo);
  assert([...document.querySelectorAll('[style]')].length === 0, 'a style attribute appeared after the update');
  return before + ' -> ' + afterOne + ' -> ' + afterTwo + ', still no inline style';
});

check('B9', 'conditional styles ship and every conditional class is mounted', () => {
  const cssText = readFileSync(CSS, 'utf8');
  const map = JSON.parse(readFileSync(join(SITE, 'kairos.map.json'), 'utf8'));
  const conditional = map.rules.filter((rule) => (rule.conditions || []).length > 0);
  assert(conditional.length >= 5, 'expected at least 5 conditional rules, got ' + conditional.length);
  for (const kind of ['pseudoClass', 'pseudoElement', 'media', 'container', 'supports']) {
    assert(
      conditional.some((rule) => rule.conditions.some((condition) => condition.kind === kind)),
      'manifest has no ' + kind + ' rule',
    );
  }
  const html = readFileSync(INDEX, 'utf8');
  for (const rule of conditional) {
    assert(html.includes(rule.className), 'no element carries conditional class ' + rule.className);
  }
  for (const marker of [':hover', '::before', '@media', '@container', '@supports']) {
    assert(cssText.includes(marker), 'stylesheet lacks ' + marker);
  }
  return conditional.length + ' conditional classes mounted';
});

const failed = results.filter((r) => !r.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' checks passed');
if (failed.length > 0) process.exit(1);
