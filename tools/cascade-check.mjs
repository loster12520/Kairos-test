#!/usr/bin/env node
// M2 acceptance: the cascade contract with third-party CSS.
// - Kairos never emits unlayered rules (it must not pollute the global cascade);
// - an unlayered third-party rule beats a Kairos layered rule (documented, expected);
// - conditional rules (hover/media) live in the same layers.
import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';

const SITE = join(process.cwd(), 'demo-web', 'build', 'site');
const results = [];
function check(id, title, body) {
  return Promise.resolve()
    .then(body)
    .then((detail) => {
      results.push({ id, ok: true });
      console.log('  PASS  ' + id + '  ' + title + (detail ? '  -> ' + detail : ''));
    })
    .catch((error) => {
      results.push({ id, ok: false });
      console.log('  FAIL  ' + id + '  ' + title + '  -> ' + error.message);
    });
}
function assert(condition, message) {
  if (!condition) throw new Error(message);
}
function kairosClassesOutsideLayers(css) {
  // Strip every @layer block, then look for leftover Kairos selectors.
  const withoutLayers = css.replace(/@layer\s+kairos\.[a-z]+\s*\{[\s\S]*?\n\}/g, '');
  return [...withoutLayers.matchAll(/\.k[robv][0-9a-f]{12}/g)].length;
}

const kairosCss = readFileSync(join(SITE, 'kairos.css'), 'utf8');
const thirdPartyCss = readFileSync(join(SITE, 'site.css'), 'utf8');
const layers = ['kairos.reset', 'kairos.base', 'kairos.variant', 'kairos.override'];

console.log('Kairos cascade contract check');
console.log('  site  ' + SITE);
console.log('');

await check('K1', 'Kairos emits the full layer declaration and no unlayered rules', () => {
  const declaration = '@layer ' + layers.join(', ') + ';';
  assert(kairosCss.includes(declaration), 'missing layer order declaration');
  const outside = kairosClassesOutsideLayers(kairosCss);
  assert(outside === 0, outside + ' Kairos selector(s) escaped the layers');
  return '4 layers, 0 unlayered selectors';
});

await check('K2', 'conditional rules stay inside the layers too', () => {
  for (const selector of [':hover', '::before', '@media', '@container', '@supports']) {
    assert(kairosCss.includes(selector), 'missing conditional construct ' + selector);
  }
  // Grab the byte range of every layer block and require conditional rules to fall inside one.
  const ranges = [...kairosCss.matchAll(/@layer\s+kairos\.[a-z]+\s*\{/g)].map((match) => match.index);
  assert(ranges.length >= 4, 'expected at least four layer blocks, got ' + ranges.length);
  const hoverIndex = kairosCss.indexOf(':hover');
  assert(hoverIndex > ranges[0], ':hover appears before the first layer block');
  return 'hover/media/container/supports inside kairos.*';
});

await check('K3', 'the third-party stylesheet is unlayered', () => {
  const withoutComments = thirdPartyCss.replace(/\/\*[\s\S]*?\*\//g, '');
  assert(!/@layer/.test(withoutComments), 'the third-party stylesheet accidentally uses @layer');
  assert(thirdPartyCss.includes('#cascade-probe'), 'the probe rule is missing from the third-party css');
  return 'no @layer in site.css';
});

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
await page.goto(pathToFileURL(join(SITE, 'index.html')).href, { waitUntil: 'load' });

await check('K4', 'an unlayered third-party rule beats the layered Kairos rule', async () => {
  const probe = page.locator('#cascade-probe');
  const background = await probe.evaluate((element) => getComputedStyle(element).backgroundColor);
  assert(background === 'rgb(1, 2, 3)', 'expected the third-party colour, got ' + background);
  assert(kairosCss.includes('background-color: #ff0000'), 'the Kairos probe rule is missing');
  return 'third-party rgb(1, 2, 3) wins over layered #ff0000';
});

await check('K5', 'Kairos rules still apply where nothing overrides them', async () => {
  const card = page.locator('[data-kairos-id="modal-open"]');
  const border = await card.evaluate((element) => getComputedStyle(element).borderTopWidth);
  assert(border === '1px', 'compiled button border is ' + border);
  return 'compiled button styles applied';
});

await browser.close();

const failed = results.filter((result) => !result.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' checks passed');
if (failed.length > 0) process.exit(1);
