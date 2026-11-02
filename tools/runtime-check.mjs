#!/usr/bin/env node
// M3 acceptance: keyed list reuse and mounted behaviour survive real DOM updates.
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

console.log('Kairos runtime browser check');
console.log('  page  ' + SITE);
console.log('');

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const pageErrors = [];
page.on('pageerror', (error) => pageErrors.push(String(error)));
await page.goto(pathToFileURL(SITE).href, { waitUntil: 'load' });

await check('R1', 'the keyed list renders items with stable key attributes', async () => {
  const items = page.locator('[data-kairos-id="keyed-list"] li');
  const count = await items.count();
  assert(count === 2, 'expected 2 seeded items, got ' + count);
  const keys = await items.evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-kairos-key')));
  assert(keys.every((key) => key && key.length > 0), 'missing data-kairos-key: ' + keys.join(','));
  return keys.join(', ');
});

await check('R2', 'adding an item reuses the existing DOM node instead of rebuilding the list', async () => {
  const first = page.locator('[data-kairos-id="keyed-list"] li').first();
  await first.evaluate((node) => node.setAttribute('data-reuse-marker', 'kept'));
  await page.locator('[data-kairos-id="keyed-add"]').click();
  await page.waitForTimeout(100);
  const count = await page.locator('[data-kairos-id="keyed-list"] li').count();
  assert(count === 3, 'expected 3 items, got ' + count);
  const marker = await first.evaluate((node) => node.getAttribute('data-reuse-marker'));
  assert(marker === 'kept', 'the first list item was rebuilt');
  const labels = await page.locator('[data-kairos-id="keyed-list"] li').evaluateAll((nodes) => nodes.map((node) => node.textContent));
  return labels.join(' | ');
});

await check('R3', 'the page stays clean after the update (no inline styles, no errors)', async () => {
  const inline = await page.evaluate(() => document.querySelectorAll('[style]').length);
  assert(inline === 0, 'found ' + inline + ' elements with inline style');
  assert(pageErrors.length === 0, 'page errors: ' + pageErrors.join(' | '));
  return 'no inline style, no page errors';
});

await check('R4', 'the modal moves focus in, keeps Tab inside and restores focus on Escape', async () => {
  await page.locator('[data-kairos-id="modal-open"]').click();
  await page.waitForTimeout(150);
  let active = await page.evaluate(() => document.activeElement?.id);
  assert(active === 'modal-close', 'focus did not move into the dialog: ' + active);
  const dialogRole = await page.locator('#shortcuts-modal').getAttribute('role');
  assert(dialogRole === 'dialog', 'modal role is ' + dialogRole);
  await page.keyboard.press('Tab');
  active = await page.evaluate(() => document.activeElement?.id);
  assert(active === 'modal-close', 'Tab escaped the modal: ' + active);
  await page.keyboard.press('Shift+Tab');
  active = await page.evaluate(() => document.activeElement?.id);
  assert(active === 'modal-close', 'Shift+Tab escaped the modal: ' + active);
  await page.keyboard.press('Escape');
  await page.waitForTimeout(120);
  const display = await page.locator('#shortcuts-modal').evaluate((element) => getComputedStyle(element).display);
  assert(display === 'none', 'modal is still visible: ' + display);
  const restored = await page.evaluate(() => document.activeElement?.getAttribute('data-kairos-id'));
  assert(restored === 'modal-open', 'focus was not restored to the trigger: ' + restored);
  return 'focus trapped, Escape closed, focus restored';
});

const failed = results.filter((result) => !result.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' checks passed');
await browser.close();
if (failed.length > 0) process.exit(1);
