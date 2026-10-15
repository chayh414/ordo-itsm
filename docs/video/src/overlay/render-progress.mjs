// progress-template.html 을 chapter idx 1~5 각각 PNG (투명 배경) 로 캡처
import { chromium } from 'playwright';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { mkdirSync } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(__dirname, '..', '..', 'assets', 'overlay');
mkdirSync(outDir, { recursive: true });

const html = resolve(__dirname, 'progress-template.html');
const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: { width: 300, height: 100 },
  deviceScaleFactor: 2,  // 선명도
});
const page = await context.newPage();

for (let i = 1; i <= 5; i++) {
  const url = pathToFileURL(html).toString() + '?i=' + i;
  await page.goto(url, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(200);
  const out = resolve(outDir, `progress-${i}.png`);
  await page.screenshot({ path: out, omitBackground: true, type: 'png' });
  console.log(`→ ${out}`);
}
await browser.close();
