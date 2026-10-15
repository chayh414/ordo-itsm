import { chromium } from 'playwright';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mkdirSync } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const out = resolve(__dirname, '..', '..', 'output', 'preflight', 'landing-black-buttons.png');
mkdirSync(resolve(__dirname, '..', '..', 'output', 'preflight'), { recursive: true });

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
await page.evaluate(() => document.fonts.ready);
await page.waitForTimeout(1500); // framer motion / lenis settle
await page.screenshot({ path: out, fullPage: false });
console.log('→', out);
await browser.close();
