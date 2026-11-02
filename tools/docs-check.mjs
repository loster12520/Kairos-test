#!/usr/bin/env node
// M5 dogfood acceptance: the documentation site is rendered by Kairos itself, every internal
// link resolves, and no markdown leaks into the HTML.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SITE = join(ROOT, 'docs-site', 'build', 'site');

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

console.log('Kairos docs site check');
console.log('  site  ' + SITE);
console.log('');

check('D1', 'the docs site builds with one page per markdown source', () => {
  execFileSync('./gradlew', ['--console=plain', ':docs-site:assembleSite'], { cwd: ROOT, stdio: 'pipe' });
  const pages = readdirSync(SITE).filter((name) => name.endsWith('.html'));
  assert(pages.includes('index.html'), 'index.html missing');
  assert(pages.length >= 12, 'expected at least 12 pages, got ' + pages.length);
  for (const required of [
    'docs-adr-0001-product-positioning.html',
    'docs-adr-0005-ssr-enhancement-model.html',
    'docs-contracts-public-api.html',
    'direction.html',
  ]) {
    assert(pages.includes(required), 'missing page ' + required);
  }
  return pages.length + ' pages';
});

check('D2', 'every page renders a title, navigation and compiled stylesheet', () => {
  const pages = readdirSync(SITE).filter((name) => name.endsWith('.html'));
  for (const name of pages) {
    const html = readFileSync(join(SITE, name), 'utf8');
    assert(/<h1 class="[^"]+">/.test(html), name + ' has no rendered title');
    assert(html.includes('<nav'), name + ' has no navigation');
    assert(html.includes('href="kairos.css"'), name + ' does not load the compiled stylesheet');
    assert(html.includes('dogfood'), name + ' lost the dogfood footer');
  }
  return pages.length + ' pages with nav + title';
});

check('D3', 'no markdown syntax leaks into the rendered HTML', () => {
  const pages = readdirSync(SITE).filter((name) => name.endsWith('.html'));
  for (const name of pages) {
    const html = readFileSync(join(SITE, name), 'utf8');
    const text = html
      .replace(/<pre[\s\S]*?<\/pre>/g, '')
      .replace(/<code[\s\S]*?<\/code>/g, '')
      .replace(/<[^>]*>/g, '');
    assert(!/^#{1,3}\s/m.test(text), name + ' contains a raw markdown heading');
    assert(!/\]\([^)]*\)/.test(text), name + ' contains a raw markdown link');
  }
  return pages.length + ' pages clean';
});

check('D4', 'every internal link resolves to a generated page', () => {
  const pages = readdirSync(SITE).filter((name) => name.endsWith('.html'));
  const missing = [];
  for (const name of pages) {
    const html = readFileSync(join(SITE, name), 'utf8');
    const hrefs = [...html.matchAll(/href="([^"]+)"/g)].map((match) => match[1]);
    for (const href of hrefs) {
      if (href.startsWith('http') || href.startsWith('#') || href.startsWith('mailto:')) continue;
      const target = href.split('#')[0];
      if (!target || !target.endsWith('.html')) continue;
      if (!existsSync(join(SITE, target))) missing.push(name + ' -> ' + href);
    }
  }
  assert(missing.length === 0, 'broken links: ' + missing.slice(0, 5).join(', '));
  return 'all href targets exist';
});

check('D5', 'the compiled stylesheet ships the layered rules used by the pages', () => {
  const css = readFileSync(join(SITE, 'kairos.css'), 'utf8');
  assert(css.includes('@layer kairos.reset'), 'stylesheet has no layer declaration');
  const pages = readdirSync(SITE).filter((name) => name.endsWith('.html'));
  const html = readFileSync(join(SITE, pages[0]), 'utf8');
  const classes = [...html.matchAll(/class="([^"]*)"/g)].flatMap((match) => match[1].split(/\s+/)).filter(Boolean);
  const missing = classes.filter((name) => !css.includes('.' + name));
  assert(missing.length === 0, 'page classes missing from css: ' + missing.slice(0, 3).join(', '));
  return classes.length + ' classes covered';
});

const failed = results.filter((result) => !result.ok);
console.log('');
console.log((results.length - failed.length) + '/' + results.length + ' checks passed');
if (failed.length > 0) process.exit(1);
