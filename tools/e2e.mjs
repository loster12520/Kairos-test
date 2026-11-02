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
page.on('dialog', (dialog) => dialog.accept());
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

await check('E14', 'untrusted comment markup is escaped and unsafe links are dropped', async () => {
  const marker = 'xss ' + Date.now();
  await page.locator('#comments input[name="author"]').fill('xss');
  await page
    .locator('#comments textarea[name="body"]')
    .fill('<script>window.__kairosXss = 1</script> [click](javascript:alert(1)) ' + marker);
  await page.locator('#comments button[type="submit"]').click();
  await page.waitForFunction(
    (text) => document.querySelector('#comments')?.textContent.includes(text),
    marker,
    { timeout: 8000 },
  );
  const executed = await page.evaluate(() => window.__kairosXss);
  assert(executed === undefined, 'inline script executed: ' + executed);
  const html = await page.locator('#comments').innerHTML();
  assert(!/<script>window\.__kairosXss/.test(html), 'raw script tag leaked into the comment html');
  assert(!/href="javascript:/i.test(html), 'javascript: link leaked into the comment html');
  assert(html.includes('click'), 'link label should survive as text');
  return 'script escaped, javascript: link dropped';
});

await check('E15', 'strict security headers are sent with the page', async () => {
  const response = await page.request.get(BASE + '/');
  const csp = response.headers()['content-security-policy'] || '';
  assert(csp.includes("script-src 'self'"), 'missing strict script-src: ' + csp);
  assert(!/script-src[^;]*unsafe-inline/.test(csp), 'script-src allows inline scripts: ' + csp);
  assert(csp.includes("frame-ancestors 'none'"), 'missing frame-ancestors: ' + csp);
  assert(response.headers()['x-content-type-options'] === 'nosniff', 'missing nosniff header');
  return csp.slice(0, 80) + '...';
});

await check('E16', 'a mutating request without the csrf token is rejected', async () => {
  const response = await page.request.post(BASE + '/posts', {
    form: { title: 'csrf probe', body: 'csrf probe' },
  });
  assert(response.status() === 403, 'expected 403 without a csrf token, got ' + response.status());
  return '403 without csrf token';
});

await check('E17', 'keyboard users can open a post and focus follows the fragment', async () => {
  await page.locator('#post-list [data-title] a').first().focus();
  await page.keyboard.press('Enter');
  await page.waitForSelector('#detail-title', { timeout: 8000 });
  await page.waitForTimeout(150);
  const focused = await page.evaluate(() => document.activeElement?.id);
  assert(focused === 'detail-title', 'focus landed on ' + focused);
  return 'Enter opened the post, focus on #detail-title';
});

await check('E18', 'the comments region announces updates to assistive technology', async () => {
  const live = await page.locator('#comments').evaluate((element) => ({
    role: element.getAttribute('role'),
    live: element.getAttribute('aria-live'),
  }));
  assert(live.role === 'status', 'comments role is ' + live.role);
  assert(live.live === 'polite', 'comments aria-live is ' + live.live);
  return 'role=status aria-live=polite';
});

await check('E19', 'invalid form input is re-rendered with an error and the entered values', async () => {
  const token = await page.locator('input[name="_csrf"]').first().getAttribute('value');
  assert(token, 'no csrf token on the page');
  const response = await page.request.post(BASE + '/posts', {
    form: { title: 'kept title', body: '', _csrf: token },
  });
  assert(response.status() === 200, 'expected a re-rendered form, got ' + response.status());
  const html = await response.text();
  assert(html.includes('new-post-form'), 'the form was not re-rendered');
  assert(html.includes('title and body are required'), 'the error message is missing');
  assert(html.includes('kept title'), 'the entered title was not preserved');
  return 'error + entered value preserved';
});

await check('E20', 'invalid comment input is re-rendered with an error', async () => {
  const token = await page.locator('input[name="_csrf"]').first().getAttribute('value');
  const postId = await page.evaluate(() => {
    const form = document.querySelector('#comments form[hx-post]');
    if (!form) return null;
    const match = (form.getAttribute('hx-post') || '').match(/\/posts\/(\d+)\/comments/);
    return match ? match[1] : null;
  });
  assert(postId, 'no open post to comment on');
  const response = await page.request.post(BASE + '/posts/' + postId + '/comments', {
    form: { author: 'reader', body: '', _csrf: token },
  });
  const html = await response.text();
  assert(html.includes('comment is required'), 'the error message is missing');
  assert(html.includes('reader'), 'the author value was not preserved');
  return 'comment error + author preserved';
});

await check('E21', 'editing a post updates the detail and the list in place', async () => {
  await page.locator('#post-list [data-title] a').first().click();
  await page.waitForSelector('#detail-title', { timeout: 8000 });
  await page.locator('#detail button:has-text("Edit post")').click();
  await page.waitForSelector('#edit-post-form', { timeout: 8000 });
  const title = 'Edited ' + Date.now();
  await page.locator('#edit-post-form input[name="title"]').fill(title);
  await page.locator('#edit-post-form button[type="submit"]').click();
  await page.waitForFunction(
    (text) => document.querySelector('#detail-title')?.textContent.includes(text),
    title,
    { timeout: 8000 },
  );
  const listTitles = await page.locator('#post-list [data-title]').evaluateAll((nodes) =>
    nodes.map((node) => node.textContent || ''),
  );
  assert(listTitles.some((text) => text.includes(title)), 'the list did not update after edit');
  return title;
});

await check('E22', 'deleting a post removes it from the list', async () => {
  const before = await page.locator('#post-list [data-title]').count();
  await page.locator('#post-list [data-title]').first().locator('button:has-text("Delete")').click();
  await page.waitForFunction(
    (count) => document.querySelectorAll('#post-list [data-title]').length === count,
    before - 1,
    { timeout: 8000 },
  );
  return before + ' -> ' + (before - 1);
});

await check('E23', 'sorting by comments reorders the list', async () => {
  await page.locator('button:has-text("Most comments")').click();
  await page.waitForTimeout(250);
  const counts = await page.locator('#post-list [data-title]').evaluateAll((nodes) =>
    nodes.map((node) => {
      const match = (node.textContent || '').match(/(\d+) comments/);
      return match ? Number(match[1]) : 0;
    }),
  );
  const sorted = [...counts].sort((left, right) => right - left);
  assert(JSON.stringify(counts) === JSON.stringify(sorted), 'list is not sorted by comments: ' + counts.join(','));
  return counts.join(', ');
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
