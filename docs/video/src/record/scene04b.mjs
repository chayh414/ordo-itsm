// SCENE 04b — 민감정보 마스킹 시연
//   김요청 로그인 → 비밀번호 포함 메시지 → 응답/요청서에 비밀번호 없음을 보여줌
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
  await quickLogin(page, '김요청');
  mark('login');
  await page.waitForTimeout(500);

  const CHAT = 'textarea[placeholder*="메시지"]';
  await page.locator(CHAT).first().click();
  await typeHuman(page, CHAT, SENSITIVE_MSG, { minMs: 20, maxMs: 55 });
  await page.waitForTimeout(300);
  await page.keyboard.press('Enter');
  mark('sent');

  // 응답 등장 대기 (요청서 또는 설명)
  try {
    await page.locator('text=/비밀번호|계정|요청서|초안|방법|어떤|확인|도와/').first().waitFor({ timeout: 25000 });
    mark('response_shown');
  } catch {
    mark('response_timeout');
  }
  await page.waitForTimeout(2500);
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
