#!/usr/bin/env node
// Real browser check for the conditional style model: hover, @supports and container/media
// queries are only observable in a layout engine, not in jsdom.
import { chromium } from 'playwright';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';

const SITE = join(process.cwd(), 'demo-web', 'build', 'site', 'index.html');
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

console.log('Kairos conditional style browser check');
console.log('  page  ' + SITE);
console.log('');

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
await page.goto(pathToFileURL(SITE).href, { waitUntil: 'load' });

await check('S1', 'the compiled :hover rule changes the button background', async () => {
  const button = page.locator('button[data-kairos-id="theme-toggle"]');
  const before = await button.evaluate((element) => getComputedStyle(element).backgroundColor);
  await button.hover();
  await page.waitForTimeout(150);
  const after = await button.evaluate((element) => getComputedStyle(element).backgroundColor);
  assert(before !== after, 'hover did not change the background: ' + before + ' -> ' + after);
  await page.mouse.move(2, 2);
  return before + ' -> ' + after;
});

await check('S2', 'the :focus-visible rule applies a compiled border colour', async () => {
  const button = page.locator('button[data-kairos-id="density-toggle"]');
  await button.focus();
  await page.waitForTimeout(120);
  const border = await button.evaluate((element) => getComputedStyle(element).borderTopColor);
  assert(border === 'rgb(124, 92, 255)', 'focus-visible border is ' + border);
  return border;
});

await check('S3', 'the @supports rule ships the backdrop filter', async () => {
  const header = page.locator('header');
  const filter = await header.evaluate((element) => {
    const style = getComputedStyle(element);
    return style.backdropFilter || style.getPropertyValue('backdrop-filter');
  });
  assert(filter && filter.includes('blur'), 'backdrop-filter not applied: ' + filter);
  return filter;
});

await check('S4', 'container/media queries switch the shell padding on a narrow viewport', async () => {
  const shell = page.locator('body > div').first();
  const wide = await shell.evaluate((element) => getComputedStyle(element).paddingLeft);
  assert(wide === '24px', 'wide padding-left is ' + wide);
  await page.setViewportSize({ width: 600, height: 900 });
  await page.waitForTimeout(150);
  const narrow = await shell.evaluate((element) => getComputedStyle(element).paddingLeft);
  assert(narrow === '16px', 'narrow padding-left is ' + narrow);
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.waitForTimeout(100);
  return wide + ' -> ' + narrow;
});

await check('S5', 'the ::before pseudo element renders a pill from compiled css', async () => {
  const brand = page.locator('header span').first();
  const before = await brand.evaluate((element) => {
    const style = getComputedStyle(element, '::before');
    return { width: style.width, background: style.backgroundColor };
  });
  assert(before.width === '8px', '::before width is ' + before.width);
  assert(before.background === 'rgb(124, 92, 255)', '::before background is ' + before.background);
  return before.width + ' ' + before.background;
});

const failed = results.filter((result) => !result.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' checks passed');
await browser.close();
if (failed.length > 0) process.exit(1);
