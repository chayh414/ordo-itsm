// SCENE 08b — 토스트 '확인' 클릭 → 장애 상세 (1차 통보 기한)
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync, renameSync, existsSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  APP_BASE, VIEWPORT, ACCOUNTS,
  installCursor, quickLogin, apiLogin, apiPost,
} from './common.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const outDir = resolve(videoRoot, 'assets', 'recordings');
mkdirSync(outDir, { recursive: true });
const target = resolve(outDir, 'scene08b.webm');
if (existsSync(target)) unlinkSync(target);
const timelinePath = resolve(outDir, 'timeline-08b.json');

const t0 = Date.now();
const marks = [];
function mark(name) {
  const t = (Date.now() - t0) / 1000;
  console.log(`  mark ${name.padEnd(28)} ${t.toFixed(2)}s`);
  marks.push({ name, t });
}

const browser = await chromium.launch();
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
  await page.waitForTimeout(800);

  // 장애 발생 API (admin)
  const adminToken = await apiLogin(ACCOUNTS.admin);
  await apiPost('/api/demo/incidents', adminToken, {
    tenantCode: 'A-UNIV',
    service: 'ERP',
    summary: '504 Gateway Timeout',
    severity: 'P2',
  });
  mark('incident_posted');

  // 토스트 등장 대기 (SSE 전달 ~2-5s)
  await page.locator('[data-testid="notification-toast"]').first()
    .waitFor({ state: 'visible', timeout: 15000 });
  mark('toast_visible');
  await page.waitForTimeout(2500);

  // '확인' 링크 클릭 → ordo:openTicket 이벤트 → 백오피스 상세로 이동
  await page.locator('[data-testid="notification-toast"] a', { hasText: '확인' })
    .first().click();
  mark('confirm_clicked');
  await page.waitForTimeout(1500);

  // 백오피스 상세 영역 — '1차 통보 기한' 또는 '요청서 원문' 또는 감사 로그 등장 대기
  try {
    await page.locator('text=/1차 통보 기한|요청서 원문|감사 로그/')
      .first().waitFor({ state: 'visible', timeout: 8000 });
    mark('detail_shown');
  } catch {
    mark('detail_timeout');
  }

  // 1차 통보 기한 영역 안으로 스크롤 (바로 보이도록)
  const dueLabel = page.locator('text=/1차 통보 기한/').first();
  try {
    await dueLabel.scrollIntoViewIfNeeded();
  } catch {}

  // 상세 유지 (6초)
  await page.waitForTimeout(6000);
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
