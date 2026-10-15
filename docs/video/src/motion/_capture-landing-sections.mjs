// 랜딩 02 Intro·03 Steps 섹션 캡처 (1440·1920)
import { chromium } from 'playwright';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mkdirSync } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(__dirname, '..', '..', '..', 'captures');
mkdirSync(outDir, { recursive: true });

async function cap(page, sectionId, name) {
  await page.evaluate((id) => {
    const el = document.getElementById(id);
    if (el) el.scrollIntoView({ behavior: 'auto', block: 'start' });
  }, sectionId);
  await page.waitForTimeout(2500);
  const path = resolve(outDir, name);
  await page.screenshot({ path });
  console.log(`→ ${name}`);
}

const browser = await chromium.launch();
for (const w of [1440, 1920]) {
  const h = w === 1920 ? 1080 : 900;
  const context = await browser.newContext({ viewport: { width: w, height: h } });
  const page = await context.newPage();
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  await cap(page, 'intro',   `landing-02-intro-${w}.png`);
  await cap(page, 'steps',   `landing-03-steps-${w}.png`);
  await context.close();
}
await browser.close();
