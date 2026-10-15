// Stage 3 캡처: 1440·1920 두 해상도에서 요청서 3상태(초안·보완 저장 후·접수 완료)
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { APP_BASE, quickLogin } from './common.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = resolve(__dirname, '..', '..', '..', 'captures');
mkdirSync(OUT, { recursive: true });

const SIZES = [
  { label: '1440', width: 1440, height: 900 },
  { label: '1920', width: 1920, height: 1080 },
];

async function captureSize(size) {
  console.log(`\n[${size.label}] 시작`);
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: size.width, height: size.height },
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();

  await quickLogin(page, '김요청');

  // 1) 초안 상태 — 요청 입력 후 create_request 카드 수신까지 대기
  const chatInput = page.locator('textarea').first();
  await chatInput.waitFor({ timeout: 5000 });
  await chatInput.fill('내일 오후에 운영 서버 방화벽 포트 443 개방해주세요.');

  // 디버그: 입력 후 상태 캡처
  await page.screenshot({ path: resolve(OUT, `_dbg-after-fill-${size.label}.png`) });

  // 전송 버튼 또는 Enter 로 전송
  const sendBtn = page.locator('button[type="submit"], button:has-text("전송"), button:has-text("보내기")').first();
  const sendBtnCount = await sendBtn.count();
  if (sendBtnCount > 0) {
    await sendBtn.click();
  } else {
    await page.keyboard.press('Enter');
  }

  await page.waitForTimeout(3000);
  await page.screenshot({ path: resolve(OUT, `_dbg-after-send-${size.label}.png`) });

  // CreateDoc 의 "요청 정보" 헤더가 보일 때까지 대기
  await page.locator('h3:has-text("요청 정보")').waitFor({ timeout: 15000 });
  await page.waitForTimeout(1000);

  const draftPath = resolve(OUT, `stage3-draft-${size.label}.png`);
  await page.screenshot({ path: draftPath });
  console.log(`  ✓ 초안: ${draftPath}`);

  // 2) 보완 저장 — 특정 field 지목 (테스트 계획, 롤백 절차 → score 10→6)
  async function fillByLabel(labelText, answer) {
    // OpenRow 는 <strong>{label}</strong> + 같은 섹션 안에 input + "저장" 버튼
    const row = page.locator('div.py-4').filter({ has: page.locator(`strong:has-text("${labelText}")`) }).first();
    await row.waitFor({ timeout: 5000 });
    await row.locator('input[type="text"]').fill(answer);
    await row.locator('button:has-text("저장")').click();
    // 재분류 완료 대기 — "분석 중…" 버튼이 사라질 때까지
    await page.waitForFunction(() => {
      return !Array.from(document.querySelectorAll('button')).some(b => b.textContent?.includes('분석 중'));
    }, { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(600);
  }

  try {
    await fillByLabel('테스트 계획', '스테이징 환경에서 연결 테스트 완료, 로그 확인 완료');
  } catch (e) { console.log('  테스트 계획 skip:', e.message); }
  try {
    await fillByLabel('롤백 절차', '추가한 허용 규칙 즉시 삭제, 작업 전 설정 백업');
  } catch (e) { console.log('  롤백 절차 skip:', e.message); }

  await page.waitForTimeout(800);
  const supPath = resolve(OUT, `stage3-supplemented-${size.label}.png`);
  await page.screenshot({ path: supPath });
  console.log(`  ✓ 보완 저장 후: ${supPath}`);

  // 3) 접수 완료 — "접수하기" 버튼 (xl 이상에선 사이드바, 그 외엔 BottomBar — 보이는 쪽만 선택)
  const submit = page.locator('button:has-text("접수하기")').locator('visible=true').first();
  await submit.waitFor({ timeout: 5000 });
  await submit.click();
  await page.locator('h1:has-text("요청이 접수되었습니다")').waitFor({ timeout: 10000 });
  await page.waitForTimeout(800);

  const submittedPath = resolve(OUT, `stage3-submitted-${size.label}.png`);
  await page.screenshot({ path: submittedPath });
  console.log(`  ✓ 접수 완료: ${submittedPath}`);

  await browser.close();
}

for (const size of SIZES) {
  await captureSize(size);
}
console.log('\n모두 완료:', OUT);
