#!/usr/bin/env node
// Accessibility scan with axe-core on the assembled demo site.
import { chromium } from 'playwright';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';

const require = createRequire(import.meta.url);
const AXE = require.resolve('axe-core/axe.min.js');
const SITE = join(process.cwd(), 'demo-web', 'build', 'site', 'index.html');

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
await page.goto(pathToFileURL(SITE).href, { waitUntil: 'load' });
await page.addScriptTag({ path: AXE });
const results = await page.evaluate(() =>
  axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa'] } }),
);

const serious = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical');
console.log('Kairos accessibility scan');
console.log('  page       ' + SITE);
console.log('  violations ' + results.violations.length + ' total, ' + serious.length + ' serious/critical');
for (const violation of results.violations) {
  console.log('  - ' + violation.id + ' [' + violation.impact + '] ' + violation.nodes.length + ' node(s): ' + violation.help);
}
await browser.close();
if (serious.length > 0) {
  console.log('');
  console.log('FAILED: serious or critical accessibility violations');
  process.exit(1);
}
console.log('');
console.log('axe passed: no serious or critical violations');
