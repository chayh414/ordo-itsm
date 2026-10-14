// 각 모션 HTML을 Playwright 로 1920×1080 webm 으로 녹화
// 사용: node src/motion/render-motion.mjs [scene01 scene02 scene03 scene10 scene11]
import { chromium } from 'playwright';
import { readFileSync, mkdirSync, renameSync, existsSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const motionDir = __dirname;
const outDir = resolve(videoRoot, 'assets', 'recordings');
mkdirSync(outDir, { recursive: true });

const defaults = ['scene01', 'scene02', 'scene03', 'scene10', 'scene11'];
const targets = process.argv.slice(2).length ? process.argv.slice(2) : defaults;

for (const id of targets) {
  const html = resolve(motionDir, `${id}.html`);
  const htmlContent = readFileSync(html, 'utf8');
  const durMatch = htmlContent.match(/scene-duration"\s+content="(\d+)"/);
  if (!durMatch) {
    console.error(`[${id}] scene-duration meta 를 찾을 수 없음`);
    continue;
  }
  const dur = parseInt(durMatch[1], 10);

  const target = resolve(outDir, `${id}.webm`);
  if (existsSync(target)) unlinkSync(target);

  console.log(`[${id}] duration=${dur}s 녹화 시작`);
  const browser = await chromium.launch({
    args: ['--disable-blink-features=AutomationControlled']
  });
  const context = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    deviceScaleFactor: 1,
    recordVideo: { dir: outDir, size: { width: 1920, height: 1080 } }
  });
  const page = await context.newPage();
  await page.goto(pathToFileURL(html).toString(), { waitUntil: 'load' });
  // 폰트·CSS 로드 안정화
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(400);
  // 애니메이션 재생을 위해 선두에서 다시 리로드 (타이밍 재시작)
  await page.evaluate(() => {
    document.getAnimations().forEach(a => a.cancel());
    // 다시 트리거하기 위해 all elements reflow
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT);
    while (walker.nextNode()) walker.currentNode.offsetHeight;
  });
  await page.reload({ waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(200);

  const t0 = Date.now();
  await page.waitForTimeout(dur * 1000 + 300);
  console.log(`  … 녹화 ${((Date.now()-t0)/1000).toFixed(2)}s 대기 끝`);

  const video = page.video();
  await context.close();
  await browser.close();

  if (video) {
    const savedPath = await video.path();
    renameSync(savedPath, target);
    console.log(`  → ${target}`);
  } else {
    console.error(`[${id}] video 핸들 없음`);
  }
}

console.log('\n모션 녹화 완료.');
