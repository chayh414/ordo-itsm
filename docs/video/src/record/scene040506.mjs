// SCENE 04 + 05 + 06 통합 녹화 (김요청 세션 하나로 묶음)
//   04 로그인 → 주인공 문장 → 요청서 등장 (0:00~0:45)
//   05 보완 → 9→7 → 접수            (0:45~1:20)
//   06 "내 요청 보여줘" + 방법 질문  (1:20~1:45)
//
// 결과: assets/recordings/scene040506.webm  +  timeline-040506.json

import { chromium } from 'playwright';
import { mkdirSync, writeFileSync, renameSync, existsSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  APP_BASE, VIEWPORT, ACCOUNTS,
  installCursor, quickLogin, typeHuman,
  injectOverlay, loadMotionAsset,
} from './common.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const outDir = resolve(videoRoot, 'assets', 'recordings');
mkdirSync(outDir, { recursive: true });

const target = resolve(outDir, 'scene040506.webm');
if (existsSync(target)) unlinkSync(target);
const timelinePath = resolve(outDir, 'timeline-040506.json');

const MAIN_MSG = '오늘 23시에 운영 DB 주문 테이블에 컬럼 추가하려고요. 테스트는 했는데 롤백 절차는 아직 없어요.';
const SUPPLEMENT = '작업 전 테이블 백업 + 추가한 컬럼 삭제 스크립트 준비';

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

  // ───── SCENE 04 ─────
  await quickLogin(page, '김요청');
  mark('login');
  await page.waitForTimeout(1200);

  // 입력창에 느리게 타이핑
  const chatInput = 'textarea[placeholder*="메시지"]';
  await page.locator(chatInput).first().waitFor({ state: 'visible', timeout: 10000 });
  await page.waitForTimeout(400);
  await typeHuman(page, chatInput, MAIN_MSG);
  mark('typed');
  await page.waitForTimeout(500);

  // Enter 또는 보내기 버튼
  await page.keyboard.press('Enter');
  mark('submit');

  // 요청서 생성 대기 — 새 UI 는 사이드바에 큰 숫자 점수 노출
  await page
    .locator('text=/매우 높음|9점|요청서 초안|접수하기/')
    .first()
    .waitFor({ state: 'visible', timeout: 60000 });
  await page.waitForTimeout(1500);
  mark('draft_shown');

  // 위험 요인·근거까지 로딩 대기
  await page.waitForTimeout(2000);

  // 보조 그래픽 삽입: +3+3+2+1=9 합산 (6초 유지)
  // 1440 뷰포트 기준 사이드바는 x≈1120~1440. 사이드바 바로 왼쪽에 띄움.
  await injectOverlay(page, loadMotionAsset('grafx-score.html'), { duration: 6, right: 360, top: 110 });
  mark('overlay_score');
  await page.waitForTimeout(6500);

  // ───── SCENE 05 ─────
  // 새 UI: OpenRow 는 <input type="text"> + "저장" 버튼. "롤백 절차" row 지목.
  const row = page.locator('div.py-4').filter({ has: page.locator('strong:has-text("롤백 절차")') }).first();
  await row.waitFor({ state: 'visible', timeout: 10000 });
  await row.scrollIntoViewIfNeeded();
  await page.waitForTimeout(400);
  const supInput = row.locator('input[type="text"]').first();
  await supInput.click();
  // typeHuman 는 selector 를 전역에서 받아야 하므로 row 안의 input 을 전역 셀렉터로 노출
  await typeHuman(page, 'div.py-4:has(strong:has-text("롤백 절차")) input[type="text"]', SUPPLEMENT);
  mark('supplement_typed');
  await page.waitForTimeout(400);

  // 롤백 절차 row 안의 "저장" 버튼
  const saveBtn = row.locator('button:has-text("저장")').first();
  await saveBtn.click();
  mark('supplement_saved');

  // 7점으로 재평가 대기
  await page.waitForTimeout(4000);
  // 9→7 overlay — 사이드바 점수 영역 바로 왼쪽
  await injectOverlay(page, loadMotionAsset('grafx-score-reduce.html'), { duration: 5, right: 360, top: 180 });
  mark('overlay_97');
  await page.waitForTimeout(5500);

  // "접수하기" 버튼 — 사이드바에 있음. visible 로 좁혀 BottomBar(hidden) 와 분리.
  const submitBtn = page.locator('button:has-text("접수하기")').locator('visible=true').first();
  await submitBtn.scrollIntoViewIfNeeded();
  await page.waitForTimeout(400);
  await submitBtn.click();
  mark('submit_click');
  // 접수 완료 화면이 뜨도록 대기 (SubmittedState)
  await page.locator('h1:has-text("요청이 접수되었습니다")').waitFor({ timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(2500);

  // ───── SCENE 06 ─────
  const CHAT = 'textarea[placeholder*="메시지"]';
  // "내 요청 보여줘"
  await page.locator(CHAT).first().click();
  await typeHuman(page, CHAT, '내 요청 보여줘');
  await page.keyboard.press('Enter');
  mark('list_sent');
  await page.waitForTimeout(6000);

  // 방법 질문
  await page.locator(CHAT).first().click();
  await typeHuman(page, CHAT, '방화벽 포트 열려면 보통 어떻게 해야 해?');
  await page.keyboard.press('Enter');
  mark('method_question_sent');
  // AI 응답 대기 (평균 5~7초)
  await page.waitForTimeout(10000);
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
