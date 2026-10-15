// SCENE 08 — 3분할 실시간 알림
//   각 창에 큰 이름표 inject(상단), 한은행 창엔 "알림 없음 · 다른 고객사" 중앙 메시지
//   토스트 뜬 뒤 2~3초만 유지하고 종료
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
const timelinePath = resolve(outDir, 'timeline-08.json');

const t0 = Date.now();
const marks = [];
function mark(name) {
  const t = (Date.now() - t0) / 1000;
  console.log(`  mark ${name.padEnd(32)} ${t.toFixed(2)}s`);
  marks.push({ name, t });
}

async function pickProtagonistTicketId() {
  const token = await apiLogin(ACCOUNTS.operator);
  const r = await fetch('http://localhost:8080/api/tickets?size=20', {
    headers: { Authorization: `Bearer ${token}` },
  }).then(x => x.json());
  const items = r.content || r;
  const t = items.find(it => (it.title || it.rawRequest || '').includes('주문') || (it.title || '').includes('컬럼'));
  return t ? t.id : (items[0] ? items[0].id : null);
}

const NAME_BAR_SCRIPT = (label, isEmpty) => `
(() => {
  const bar = document.createElement('div');
  bar.textContent = ${JSON.stringify(label)};
  bar.style.cssText = 'position:fixed;top:0;left:0;right:0;height:52px;background:#111;color:#fff;font-size:22px;font-weight:700;display:flex;align-items:center;justify-content:flex-end;padding-right:40px;z-index:2147483645;font-family:"Malgun Gothic",sans-serif;letter-spacing:-0.01em;';
  document.body.appendChild(bar);
  // freeze 방지: name-bar 좌측에 subtle 진행선 (continuous 애니메이션으로 pixel 변화 유도)
  const line = document.createElement('div');
  line.style.cssText = 'position:fixed;top:50px;left:0;height:2px;background:#444;z-index:2147483646;animation:ordoline 2.4s linear infinite;';
  const style = document.createElement('style');
  style.textContent = '@keyframes ordoline { 0% { width: 0 } 50% { width: 100% } 100% { width: 0 } }';
  document.head.appendChild(style);
  document.body.appendChild(line);
  ${isEmpty ? `
  const msg = document.createElement('div');
  msg.innerHTML = '<div style="font-size:19px;color:#6F6F6F;letter-spacing:-0.01em;margin-bottom:12px;">알림 없음</div><div style="font-size:14px;color:#A6A6A6;">다른 고객사</div>';
  msg.style.cssText = 'position:fixed;top:160px;right:40px;width:360px;padding:48px 30px;background:#F6F6F4;border:1px solid #E3E3E3;text-align:center;z-index:2147483644;font-family:"Malgun Gothic",sans-serif;';
  document.body.appendChild(msg);
  ` : ''}
})();
`;

const browser = await chromium.launch();

async function openUser(name, email, outName, label, isBBank = false, isEmpty = false) {
  const ctx = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 1,
    recordVideo: { dir: outDir, size: VIEWPORT },
  });
  await installCursor(ctx);
  const p = await ctx.newPage();
  if (isBBank) {
    await p.goto(`${APP_BASE}/login`, { waitUntil: 'networkidle' });
    await p.fill('input[type="email"]', email);
    await p.fill('input[type="password"]', 'Password123!');
    await p.click('button[type="submit"]');
    await p.waitForURL('**/app', { timeout: 15000 });
  } else {
    await quickLogin(p, name);
  }
  await p.waitForTimeout(600);
  // 이름표 + (한은행이면) 알림 없음 메시지 주입
  await p.evaluate(NAME_BAR_SCRIPT(label, isEmpty));
  await p.waitForTimeout(300);
  return { ctx, page: p, outName };
}

try {
  mark('start');
  const [op, reqA, reqB] = await Promise.all([
    openUser('이운영', ACCOUNTS.operator,   'scene08-operator',   '이운영 · 운영 담당자'),
    openUser('김요청', ACCOUNTS.requesterA, 'scene08-requesterA', '김요청 · 요청자 A제조'),
    openUser('한은행', ACCOUNTS.requesterB, 'scene08-requesterB', '한은행 · 요청자 B은행', true, true),
  ]);
  mark('all_logged_in');

  await Promise.all([
    op.page.waitForTimeout(1500),
    reqA.page.waitForTimeout(1500),
    reqB.page.waitForTimeout(1500),
  ]);

  const adminToken = await apiLogin(ACCOUNTS.admin);
  const ticketId = await pickProtagonistTicketId();
  console.log('protagonist ticketId =', ticketId);

  // 1) 장애 이벤트
  mark('incident_before');
  await apiPost('/api/demo/incidents', adminToken, {
    tenantCode: 'A-UNIV',
    service: 'ERP',
    summary: '504 Gateway Timeout',
    severity: 'P2',
  });
  mark('incident_after');

  // 토스트 등장 유지 (장애 알림 7s) — freeze 방지: 큰 거리 이동 + mousedown toggle 로 커서 크기 변화
  async function wiggle(p, duration) {
    const end = Date.now() + duration;
    const points = [[1200, 220], [900, 700], [1300, 300], [700, 500], [1100, 750], [1250, 180]];
    let i = 0;
    while (Date.now() < end) {
      const [x, y] = points[i % points.length];
      await p.mouse.move(x, y, { steps: 8 });
      // 커서 크기 토글 (overlay 는 mousedown 시 22px, mouseup 시 14px — 미세 변화 유도)
      if (i % 2 === 0) { await p.mouse.down(); await p.waitForTimeout(80); await p.mouse.up(); }
      await p.waitForTimeout(380);
      i++;
    }
  }
  await Promise.all([
    wiggle(op.page, 7000),
    wiggle(reqA.page, 7000),
    wiggle(reqB.page, 7000),
  ]);
  mark('incident_toast_shown');

  // 2) SLA 임박
  mark('sla_near_before');
  if (ticketId) {
    try {
      await apiPost(`/api/demo/tickets/${ticketId}/sla-near`, adminToken, {});
    } catch (e) {
      console.warn('sla-near 실패:', e.message);
    }
  }
  mark('sla_near_after');

  // SLA 임박 토스트 카운트다운 유지 (6s) — freeze 방지: wiggle 재사용
  await Promise.all([
    wiggle(op.page, 6000),
    wiggle(reqA.page, 6000),
    wiggle(reqB.page, 6000),
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
