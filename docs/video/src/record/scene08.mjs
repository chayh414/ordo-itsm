// SCENE 08 — 3분할 실시간 알림 (이운영 · 김요청 · 한은행)
//   3개 창을 동시에 띄워 각각 녹화 → 각 녹화의 오른쪽 위(사용자 이름+토스트) crop → hstack
//
// 외부 트리거:
//   1) POST /api/demo/incidents  (A-UNIV ERP 504)
//   2) 10초 뒤 POST /api/demo/tickets/{주인공 id}/sla-near
//
// 각 녹화 결과:
//   assets/recordings/scene08-operator.webm
//   assets/recordings/scene08-requesterA.webm
//   assets/recordings/scene08-requesterB.webm
//   assets/recordings/timeline-08.json

import { chromium } from 'playwright';
import { mkdirSync, writeFileSync, renameSync, existsSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  APP_BASE, VIEWPORT, ACCOUNTS,
  installCursor, quickLogin, apiLogin, apiPost, apiGet,
} from './common.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const outDir = resolve(videoRoot, 'assets', 'recordings');
mkdirSync(outDir, { recursive: true });
const timelinePath = resolve(outDir, 'timeline-08.json');

const t0 = Date.now();
const marks = [];
function mark(name) {
  const t = (Date.now() - t0) / 1000;
  console.log(`  mark ${name.padEnd(32)} ${t.toFixed(2)}s`);
  marks.push({ name, t });
}

async function pickProtagonistTicketId() {
  // 가장 최근 접수된 "주문 테이블" 티켓을 찾아 id 반환
  const token = await apiLogin(ACCOUNTS.operator);
  const r = await fetch(`${APP_BASE.replace('5173','8080')}/api/tickets?size=20`, {
    headers: { Authorization: `Bearer ${token}` },
  }).then(x => x.json());
  const items = r.content || r;
  const t = items.find(it => (it.title || it.rawRequest || '').includes('주문') || (it.title || '').includes('컬럼'));
  return t ? t.id : (items[0] ? items[0].id : null);
}

const browser = await chromium.launch({ args: ['--disable-blink-features=AutomationControlled'] });

async function openUser(name, email, outName, isBBank = false) {
  const ctx = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 1,
    recordVideo: { dir: outDir, size: VIEWPORT },
  });
  await installCursor(ctx);
  const p = await ctx.newPage();
  if (isBBank) {
    // 한은행은 빠른 선택 없음 → 수동 로그인
    await p.goto(`${APP_BASE}/login`, { waitUntil: 'networkidle' });
    await p.fill('input[type="email"]', email);
    await p.fill('input[type="password"]', 'Password123!');
    await p.click('button[type="submit"]');
    await p.waitForURL('**/app', { timeout: 15000 });
  } else {
    await quickLogin(p, name);
  }
  await p.waitForTimeout(1000);
  return { ctx, page: p, outName };
}

try {
  mark('start');
  const [op, reqA, reqB] = await Promise.all([
    openUser('이운영', ACCOUNTS.operator, 'scene08-operator'),
    openUser('김요청', ACCOUNTS.requesterA, 'scene08-requesterA'),
    openUser('한은행', ACCOUNTS.requesterB, 'scene08-requesterB', true),
  ]);
  mark('all_logged_in');

  await Promise.all([
    op.page.waitForTimeout(2500),
    reqA.page.waitForTimeout(2500),
    reqB.page.waitForTimeout(2500),
  ]);

  const adminToken = await apiLogin(ACCOUNTS.admin);
  const ticketId = await pickProtagonistTicketId();
  console.log('protagonist ticketId =', ticketId);

  // 1) 장애 이벤트 (A-UNIV · ERP · 504)
  mark('incident_before');
  await apiPost('/api/demo/incidents', adminToken, {
    tenantCode: 'A-UNIV',
    service: 'ERP',
    summary: '504 Gateway Timeout',
    severity: 'P2',
  });
  mark('incident_after');

  // 토스트 등장 유지
  await Promise.all([
    op.page.waitForTimeout(14000),
    reqA.page.waitForTimeout(14000),
    reqB.page.waitForTimeout(14000),
  ]);

  // 2) SLA 임박 (주인공 요청)
  mark('sla_near_before');
  if (ticketId) {
    try {
      await apiPost(`/api/demo/tickets/${ticketId}/sla-near`, adminToken, {});
    } catch (e) {
      console.warn('sla-near 실패:', e.message);
    }
  }
  mark('sla_near_after');

  await Promise.all([
    op.page.waitForTimeout(14000),
    reqA.page.waitForTimeout(14000),
    reqB.page.waitForTimeout(14000),
  ]);
  mark('end');

  const [vOp, vA, vB] = [op.page.video(), reqA.page.video(), reqB.page.video()];
  await Promise.all([op.ctx.close(), reqA.ctx.close(), reqB.ctx.close()]);

  for (const [v, name] of [[vOp, 'scene08-operator'], [vA, 'scene08-requesterA'], [vB, 'scene08-requesterB']]) {
    if (!v) continue;
    const savedPath = await v.path();
    const dst = resolve(outDir, `${name}.webm`);
    if (existsSync(dst)) unlinkSync(dst);
    renameSync(savedPath, dst);
    console.log(`→ ${dst}`);
  }
} catch (err) {
  console.error('녹화 중 오류:', err);
  marks.push({ name: 'ERROR', t: (Date.now() - t0) / 1000, error: err.message });
}

await browser.close();
writeFileSync(timelinePath, JSON.stringify({ marks }, null, 2), 'utf8');
console.log(`→ ${timelinePath}`);
