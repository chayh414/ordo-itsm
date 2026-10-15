// 랜딩 StepsSection 까지 스크롤해서 1920×1080 과 1440×900 캡처
import { chromium } from 'playwright';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mkdirSync } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(__dirname, '..', '..', 'output', 'preflight');
mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch();
for (const w of [1440, 1920]) {
  const h = w === 1920 ? 1080 : 900;
  const context = await browser.newContext({ viewport: { width: w, height: h } });
  const page = await context.newPage();
  await page.goto('http://localhost:5173/', { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  // Lenis 를 우회해 즉시 steps 로 스크롤
  await page.evaluate(() => {
    const el = document.getElementById('steps');
    if (el) el.scrollIntoView({ behavior: 'auto', block: 'start' });
  });
  await page.waitForTimeout(2500); // Framer stair animation 완주
  await page.screenshot({ path: resolve(outDir, `landing-steps-${w}.png`), fullPage: false });
  console.log(`→ landing-steps-${w}.png`);
  await context.close();
}
await browser.close();
