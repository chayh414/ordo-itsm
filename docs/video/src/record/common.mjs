// 공통 유틸: 커서 오버레이, 토큰 로그인, 느린 타이핑, API 호출
import { request } from 'node:http';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

export const APP_BASE = 'http://localhost:5173';
export const API_BASE = 'http://localhost:8080';
export const DEMO_PASSWORD = 'Password123!';

export const ACCOUNTS = {
  requesterA: 'requester@a-univ.demo',
  operator:   'operator@sysone.demo',
  admin:      'admin@sysone.demo',
  requesterB: 'requester@b-bank.demo',
};

export const VIEWPORT = { width: 1440, height: 900 };

// 커서 오버레이: Playwright 마우스 이동 좌표를 따라 동그라미 그리기
const CURSOR_SCRIPT = `
(() => {
  const el = document.createElement('div');
  el.id = '__ordo_cursor__';
  el.style.cssText = 'position:fixed;z-index:2147483647;width:14px;height:14px;border-radius:9999px;background:rgba(1,100,178,0.75);border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.25);pointer-events:none;transform:translate(-50%,-50%);transition:left .12s ease-out,top .12s ease-out,width .15s,height .15s,opacity .15s;left:-20px;top:-20px;';
  document.documentElement.appendChild(el);
  let lastX = -20, lastY = -20;
  window.addEventListener('mousemove', (e) => {
    lastX = e.clientX; lastY = e.clientY;
    el.style.left = e.clientX + 'px';
    el.style.top = e.clientY + 'px';
  }, { passive: true });
  window.addEventListener('mousedown', () => {
    el.style.width = '22px'; el.style.height = '22px'; el.style.background = 'rgba(1,100,178,0.95)';
  });
  window.addEventListener('mouseup', () => {
    el.style.width = '14px'; el.style.height = '14px'; el.style.background = 'rgba(1,100,178,0.75)';
  });
})();
`;

export async function installCursor(context) {
  await context.addInitScript(CURSOR_SCRIPT);
}

export async function login(page, email) {
  await page.goto(`${APP_BASE}/login`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await page.fill('input[type="email"]', email);
  await page.fill('input[type="password"]', DEMO_PASSWORD);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/app', { timeout: 15000 });
  await page.waitForTimeout(600);
}

export async function quickLogin(page, label) {
  await page.goto(`${APP_BASE}/login`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  const btn = page.locator('button', { hasText: label }).first();
  await btn.click();
  await page.waitForURL('**/app', { timeout: 15000 });
  await page.waitForTimeout(600);
}

// 사람이 타이핑하는 듯한 느린 입력
export async function typeHuman(page, selector, text, { minMs = 35, maxMs = 90 } = {}) {
  await page.focus(selector);
  await page.waitForTimeout(200);
  for (const ch of text) {
    await page.keyboard.type(ch);
    const d = minMs + Math.random() * (maxMs - minMs);
    await page.waitForTimeout(d);
  }
}

// 백엔드 API 직접 호출 (관리자 토큰 필요시)
export async function apiLogin(email) {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: DEMO_PASSWORD }),
  });
  const j = await res.json();
  return j.accessToken;
}

export async function apiPost(path, token, body) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`${path} → HTTP ${res.status}`);
  return res.json();
}

export async function apiGet(path, token) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}${path}`, { headers });
  if (!res.ok) throw new Error(`${path} → HTTP ${res.status}`);
  return res.json();
}

// 오버레이 iframe 삽입 (보조 그래픽: +3+3+2+1=9 / 9→7)
export async function injectOverlay(page, overlayHref, { duration = 6, right = 20, top = 90 } = {}) {
  await page.evaluate(({ src, duration, right, top }) => {
    const existing = document.getElementById('__ordo_overlay__');
    if (existing) existing.remove();
    const f = document.createElement('iframe');
    f.id = '__ordo_overlay__';
    f.src = src;
    f.style.cssText = `position:fixed;z-index:2147483646;right:${right}px;top:${top}px;width:600px;height:420px;border:0;background:transparent;pointer-events:none;`;
    f.allow = 'autoplay';
    document.body.appendChild(f);
    setTimeout(() => f.remove(), duration * 1000);
  }, { src: overlayHref, duration, right, top });
}

export function loadMotionAsset(name) {
  const __dirname = dirname(fileURLToPath(import.meta.url));
  return `file:///${resolve(__dirname, '..', 'overlay', name).replace(/\\/g, '/')}`;
}
