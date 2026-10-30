#!/usr/bin/env node
// End-to-end acceptance for the blog demo: a real browser drives the server rendered page,
// htmx fragment swaps and the Kotlin/JS reactive enhancement.
import { chromium } from 'playwright';

const BASE = process.env.BLOG_URL || 'http://127.0.0.1:8080';
const results = [];
function check(id, title, body) {
  try {
    return Promise.resolve(body()).then((detail) => {
      results.push({ id, ok: true });
      console.log('  PASS  ' + id + '  ' + title + (detail ? '  -> ' + detail : ''));
    }).catch((error) => {
      results.push({ id, ok: false });
      console.log('  FAIL  ' + id + '  ' + title + '  -> ' + error.message);
    });
  } catch (error) {
    results.push({ id, ok: false });
    console.log('  FAIL  ' + id + '  ' + title + '  -> ' + error.message);
    return Promise.resolve();
  }
}
function assert(condition, message) {
  if (!condition) throw new Error(message);
}

console.log('Kairos blog end-to-end check');
console.log('  url  ' + BASE);
console.log('');

const browser = await chromium.launch();
const page = await browser.newPage();
const pageErrors = [];
page.on('pageerror', (error) => pageErrors.push(String(error)));
page.on('console', (message) => { if (message.type() === 'error') pageErrors.push(message.text()); });

await page.goto(BASE, { waitUntil: 'networkidle' });

await check('E1', 'the server renders the home page with seeded posts', async () => {
  const title = await page.title();
  assert(title === 'kairos blog', 'title is ' + title);
  const cards = await page.locator('#post-list [data-title]').count();
  assert(cards >= 3, 'expected at least the 3 seeded posts, got ' + cards);
  const text = await page.locator('#post-list').textContent();
  for (const seeded of ['Hello, Kairos', 'One block, one class']) {
    assert(text.includes(seeded), 'missing seeded post: ' + seeded);
  }
  return cards + ' posts';
});

await check('E2', 'the compiled stylesheet is applied (no inline styles)', async () => {
  const background = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  assert(background !== 'rgba(0, 0, 0, 0)' && background !== '', 'body has no background: ' + background);
  const card = page.locator('#post-list [data-title]').first();
  const cardBorder = await card.evaluate((element) => getComputedStyle(element).borderTopWidth);
  assert(cardBorder !== '0px', 'card has no border: ' + cardBorder);
  const inline = await page.evaluate(() => document.querySelectorAll('[style]').length);
  assert(inline === 0, 'found ' + inline + ' elements with an inline style');
  return 'body ' + background + ', card border ' + cardBorder;
});

await check('E3', 'htmx and the Kotlin client bundle are loaded', async () => {
  const htmx = await page.evaluate(() => typeof window.htmx);
  assert(htmx === 'object', 'htmx is ' + htmx);
  const ready = await page.evaluate(() => Boolean(document.querySelector('[data-kairos-id="theme-toggle"]')));
  assert(ready, 'theme toggle missing');
  return 'htmx ' + htmx;
});

await check('E4', 'clicking a post swaps an htmx fragment into the detail slot', async () => {
  await page.locator('#post-list [data-title] a').first().click();
  await page.waitForFunction(() => {
    const detail = document.querySelector('#detail');
    return detail && detail.textContent.includes('Add a comment');
  }, { timeout: 8000 });
  const heading = await page.locator('#detail h2').first().textContent();
  assert(heading && heading.length > 0, 'detail heading is empty');
  return 'loaded "' + heading.trim() + '" plus the comment form';
});

await check('E5', 'posting a comment appends it to the comment list', async () => {
  const marker = 'e2e comment ' + Date.now();
  await page.locator('#comments input[name="author"]').fill('e2e');
  await page.locator('#comments textarea[name="body"]').fill(marker);
  await page.locator('#comments button[type="submit"]').click();
  await page.waitForFunction((text) => document.querySelector('#comments')?.textContent.includes(text), marker, { timeout: 8000 });
  return marker;
});

await check('E6', 'creating a post adds a card without a full page reload', async () => {
  const title = 'E2E post ' + Date.now();
  const before = await page.locator('#post-list [data-title]').count();
  await page.locator('[data-kairos-id="new-post"]').click();
  await page.waitForSelector('#detail form', { timeout: 8000 });
  await page.locator('#detail input[name="title"]').fill(title);
  await page.locator('#detail textarea[name="body"]').fill('created by the end to end test');
  await page.locator('#detail button[type="submit"]').click();
  await page.waitForFunction((text) => document.body.textContent.includes(text), title, { timeout: 8000 });
  const cards = await page.locator('#post-list [data-title]').count();
  assert(cards === before + 1, 'expected ' + (before + 1) + ' posts after create, got ' + cards);
  return title + ' (' + before + ' -> ' + cards + ')';
});

await check('E7', 'the reactive filter hides non matching posts', async () => {
  await page.locator('[data-kairos-id="filter"]').fill('reactive');
  await page.waitForFunction(() => {
    const cards = [...document.querySelectorAll('#post-list [data-title]')];
    const visible = cards.filter((card) => getComputedStyle(card).display !== 'none');
    return visible.length === 1;
  }, { timeout: 8000 });
  const visible = await page.evaluate(() =>
    [...document.querySelectorAll('#post-list [data-title]')].filter((card) => getComputedStyle(card).display !== 'none').length);
  await page.locator('[data-kairos-id="filter"]').fill('');
  await page.waitForFunction(() => {
    const cards = [...document.querySelectorAll('#post-list [data-title]')];
    return cards.every((card) => getComputedStyle(card).display !== 'none');
  }, { timeout: 8000 });
  return visible + ' visible while filtering';
});

await check('E8', 'the theme toggle flips a class on <html> and survives a reload', async () => {
  const before = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  assert(!(await page.evaluate(() => document.documentElement.classList.contains('dark'))), 'page started in dark mode');
  await page.locator('[data-kairos-id="theme-toggle"]').click();
  await page.waitForFunction(() => document.documentElement.classList.contains('dark'), { timeout: 8000 });
  // The theme is animated, so wait for the computed colour to settle rather than reading it once.
  await page.waitForFunction((previous) => getComputedStyle(document.body).backgroundColor !== previous, before, { timeout: 8000 });
  const after = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  assert(after !== before, 'background did not change: ' + before);
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForFunction(() => document.documentElement.classList.contains('dark'), { timeout: 8000 });
  assert(await page.evaluate(() => localStorage.getItem('kairos-theme') === 'dark'), 'theme preference not stored');
  await page.locator('[data-kairos-id="theme-toggle"]').click();
  return before + ' -> ' + after + ' (remembered across reload)';
});

await check('E10', 'post bodies are rendered as markdown', async () => {
  await page.locator('#post-list [data-title] a', { hasText: 'Hello, Kairos' }).click();
  await page.waitForFunction(() => {
    const heading = document.querySelector('#detail article h2');
    return heading && heading.textContent.trim() === 'Hello, Kairos';
  }, { timeout: 8000 });
  const html = await page.locator('#detail article').innerHTML();
  assert(html.includes('<strong>'), 'no bold text in the rendered body');
  assert(html.includes('<ul>'), 'no list in the rendered body');
  return 'bold + list rendered';
});

await check('E9', 'the page produced no console or script errors', async () => {
  assert(pageErrors.length === 0, pageErrors.join(' | '));
  return '0 errors';
});

await browser.close();

const failed = results.filter((r) => !r.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' checks passed');
if (failed.length > 0) process.exit(1);
