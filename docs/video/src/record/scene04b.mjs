// SCENE 04b — 민감정보 마스킹 시연 (v2)
//   대기 조건: 로딩 표시("작성하고 있습니다") 사라질 때까지, 최대 40초
//   그 뒤 응답 내 [MASKED] 좌표를 찾아 timeline 에 기록 → compose 가 줌인
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
const target = resolve(outDir, 'scene04b.webm');
if (existsSync(target)) unlinkSync(target);
const timelinePath = resolve(outDir, 'timeline-04b.json');

const SENSITIVE_MSG = '포털 관리자 계정 비밀번호 초기화 부탁드려요. 지금 비밀번호는 Abcd1234! 입니다.';

const t0 = Date.now();
const marks = [];
function mark(name, extra = {}) {
  const t = (Date.now() - t0) / 1000;
  console.log(`  mark ${name.padEnd(28)} ${t.toFixed(2)}s ${JSON.stringify(extra)}`);
  marks.push({ name, t, ...extra });
}

const browser = await chromium.launch();
const context = await browser.newContext({
  viewport: VIEWPORT,
  deviceScaleFactor: 1,
  recordVideo: { dir: outDir, size: VIEWPORT },
});
await installCursor(context);
const page = await context.newPage();

let hasMasked = false;
let maskedBbox = null;

try {
  mark('start');
  await quickLogin(page, '김요청');
  mark('login');
  await page.waitForTimeout(500);

  const CHAT = 'textarea[placeholder*="메시지"]';
  await page.locator(CHAT).first().click();
  await typeHuman(page, CHAT, SENSITIVE_MSG, { minMs: 20, maxMs: 55 });
  await page.waitForTimeout(300);
  await page.keyboard.press('Enter');
  mark('sent');

  // 로딩 표시(요청서를 작성하고 있습니다…) 등장 대기 (최대 5s)
  const loadingLoc = page.locator('text=/작성하고 있습니다|분석하고 있습니다/');
  try {
    await loadingLoc.first().waitFor({ state: 'visible', timeout: 5000 });
    mark('loading_visible');
  } catch {
    mark('loading_not_shown');
  }

  // 로딩 표시 사라질 때까지 대기 — 응답 10~15초 걸림, 타임아웃 40초
  try {
    await loadingLoc.first().waitFor({ state: 'hidden', timeout: 40000 });
    mark('loading_hidden');
  } catch {
    mark('loading_timeout');
  }

  // 응답 안정 대기
  await page.waitForTimeout(1200);

  // 응답 내 [MASKED] 찾기 (좌측 메시지 영역)
  try {
    const maskedLoc = page.locator('text=[MASKED]').first();
    await maskedLoc.waitFor({ state: 'visible', timeout: 4000 });
    maskedBbox = await maskedLoc.boundingBox();
    hasMasked = true;
    mark('masked_found', { bbox: maskedBbox });
  } catch {
    mark('masked_not_found');
  }

  // 응답 화면 유지 (3.5s) — 녹화에 담김
  await page.waitForTimeout(3500);
  mark('end', { hasMasked });
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
writeFileSync(timelinePath, JSON.stringify({ marks, hasMasked, maskedBbox }, null, 2), 'utf8');
console.log(`→ ${timelinePath}`);
console.log(`\nMASKED 포함: ${hasMasked ? 'YES' : 'NO'}`);
