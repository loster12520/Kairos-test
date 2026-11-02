#!/usr/bin/env node
// Cross-browser smoke test: the compiled CSS and the mounted behaviours must behave the same in
// Chromium and Firefox. Playwright's Firefox build is used when present; CI installs both.
import { chromium, firefox, webkit } from 'playwright';
import { existsSync, readdirSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';

const SITE = join(process.cwd(), 'demo-web', 'build', 'site', 'index.html');
const results = [];

function firefoxExecutable() {
  const root = join(process.env.HOME || '', '.cache', 'ms-playwright');
  if (!existsSync(root)) return null;
  const candidate = readdirSync(root)
    .filter((name) => name.startsWith('firefox-'))
    .map((name) => join(root, name, 'firefox', 'firefox'))
    .find((path) => existsSync(path));
  return candidate || null;
}

async function runIn(name, browserType, options) {
  const browser = await browserType.launch(options);
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  await page.goto(pathToFileURL(SITE).href, { waitUntil: 'load' });
  try {
    const title = await page.title();
    const button = page.locator('[data-kairos-id="theme-toggle"]');
    const before = await button.evaluate((element) => getComputedStyle(element).backgroundColor);
    await button.hover();
    await page.waitForTimeout(150);
    const after = await button.evaluate((element) => getComputedStyle(element).backgroundColor);

    const shell = page.locator('body > div').first();
    const wide = await shell.evaluate((element) => getComputedStyle(element).paddingLeft);
    await page.setViewportSize({ width: 600, height: 900 });
    await page.waitForTimeout(150);
    const narrow = await shell.evaluate((element) => getComputedStyle(element).paddingLeft);

    const brand = page.locator('header span').first();
    const pseudo = await brand.evaluate((element) => getComputedStyle(element, '::before').width);
    const probe = await page.locator('#cascade-probe').evaluate((element) => getComputedStyle(element).backgroundColor);

    const failures = [];
    if (!title.includes('Kairos')) failures.push('title = ' + title);
    if (before === after) failures.push('hover did not change the background');
    if (wide !== '24px') failures.push('wide padding = ' + wide);
    if (narrow !== '16px') failures.push('narrow padding = ' + narrow);
    if (pseudo !== '8px') failures.push('::before width = ' + pseudo);
    if (probe !== 'rgb(1, 2, 3)') failures.push('cascade probe = ' + probe);

    results.push({ name, ok: failures.length === 0, detail: failures.join('; ') });
    console.log(
      (failures.length === 0 ? '  PASS  ' : '  FAIL  ') +
        name +
        '  ' +
        (failures.length === 0
          ? 'hover ' + before + ' -> ' + after + ', padding ' + wide + ' -> ' + narrow + ', ::before ' + pseudo
          : failures.join('; ')),
    );
  } finally {
    await browser.close();
  }
}

console.log('Kairos cross-browser check');
console.log('  page  ' + SITE);
console.log('');

await runIn('chromium', chromium, {});

const ff = firefoxExecutable();
if (ff) {
  await runIn('firefox', firefox, { executablePath: ff });
} else {
  console.log('  SKIP  firefox  (no local Playwright build; install with: npx playwright install firefox)');
}

try {
  await runIn('webkit', webkit, {});
} catch (error) {
  console.log('  SKIP  webkit  (' + String(error.message || error).split('\n')[0] + ')');
}

const failed = results.filter((result) => !result.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' browser(s) passed');
if (failed.length > 0) process.exit(1);
