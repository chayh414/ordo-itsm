// SCENE 07 — 운영 담당자 (이운영) + 백오피스
//   B은행 보완 대기 조회 → 상단 백오피스 메뉴 → 주인공 요청(목록 맨 위) 선택 → 상세 스크롤
//
// 결과: assets/recordings/scene07.webm  +  timeline-07.json

import { chromium } from 'playwright';
import { mkdirSync, writeFileSync, renameSync, existsSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  APP_BASE, VIEWPORT, ACCOUNTS,
  installCursor, quickLogin, typeHuman,
} from './common.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const outDir = resolve(videoRoot, 'assets', 'recordings');
mkdirSync(outDir, { recursive: true });
const target = resolve(outDir, 'scene07.webm');
if (existsSync(target)) unlinkSync(target);
const timelinePath = resolve(outDir, 'timeline-07.json');

const t0 = Date.now();
const marks = [];
function mark(name) {
  const t = (Date.now() - t0) / 1000;
  console.log(`  mark ${name.padEnd(28)} ${t.toFixed(2)}s`);
  marks.push({ name, t });
}

const browser = await chromium.launch({ args: ['--disable-blink-features=AutomationControlled'] });
const context = await browser.newContext({
  viewport: VIEWPORT,
  deviceScaleFactor: 1,
  recordVideo: { dir: outDir, size: VIEWPORT },
});
await installCursor(context);
const page = await context.newPage();

try {
  mark('start');
  await quickLogin(page, '이운영');
  mark('login');
  await page.waitForTimeout(1200);

  // B은행 보완 대기 조회
  const CHAT = 'textarea[placeholder*="메시지"]';
  await page.locator(CHAT).first().click();
  await typeHuman(page, CHAT, 'B은행 요청 중에 보완 대기 중인 거 보여줘');
  await page.keyboard.press('Enter');
  mark('bbank_query');
  await page.waitForTimeout(6500);

  // 백오피스 메뉴 클릭
  const backofficeNav = page.locator('a, button', { hasText: '백오피스' }).first();
  await backofficeNav.click();
  mark('backoffice_click');
  await page.waitForURL('**/backoffice', { timeout: 10000 });
  await page.waitForTimeout(1500);

  // 목록에서 ORD-20261014-0001 (방화벽, 이력 11→9→7) 선택
  const row0001 = page.locator('ul li button', { hasText: 'ORD-20261014-0001' }).first();
  await row0001.waitFor({ state: 'visible', timeout: 15000 });
  await row0001.click();
  mark('row_0001_click');
  await page.waitForTimeout(2000);

  // 상세 영역 하단 '위험 평가 이력' 영역까지 스크롤
  const rightPanel = page.locator('main > section').nth(1);
  await rightPanel.hover();
  for (let y = 0; y < 2600; y += 180) {
    await page.mouse.wheel(0, 180);
    await page.waitForTimeout(450);
  }
  mark('scroll_done');
  await page.waitForTimeout(1500);
  mark('scroll_done');
  await page.waitForTimeout(2000);
  mark('end');
} catch (err) {
  console.error('녹화 중 오류:', err);
  marks.push({ name: 'ERROR', t: (Date.now() - t0) / 1000, error: err.message });
}

const video = page.video();
await context.close();
await browser.close();
if (video) {
  const savedPath = await video.path();
  renameSync(savedPath, target);
  console.log(`→ ${target}`);
}
writeFileSync(timelinePath, JSON.stringify({ marks }, null, 2), 'utf8');
console.log(`→ ${timelinePath}`);
