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

  // 목록 맨 위 (주인공 요청 ORD-20261015-0001) 선택
  // DOM: ul > li > button (BackofficePage.tsx:144, 146)
  const firstRow = page.locator('ul li button').first();
  await firstRow.waitFor({ state: 'visible', timeout: 15000 });
  await firstRow.click();
  mark('first_row_click');
  await page.waitForTimeout(2500);

  // 상세 영역을 오른쪽 패널 안쪽 스크롤 (overflow-auto section)
  const rightPanel = page.locator('main > section').nth(1);
  await rightPanel.hover();
  for (let y = 0; y < 2200; y += 200) {
    await page.mouse.wheel(0, 200);
    await page.waitForTimeout(600);
  }
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
