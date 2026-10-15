// 각 모션 HTML에서 duration 만큼 1/30초마다 Playwright screenshot → ffmpeg concat → mp4 (30fps 매끈)
// 사용: node src/motion/render-frames.mjs
import { chromium } from 'playwright';
import { readFileSync, mkdirSync, existsSync, rmSync, unlinkSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { resolve, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const motionDir = __dirname;
const narration = JSON.parse(readFileSync(resolve(videoRoot, 'src', 'narration', 'narration.json'), 'utf8'));

const outDir = resolve(videoRoot, 'assets', 'recordings');
mkdirSync(outDir, { recursive: true });

const argIds = process.argv.slice(2).filter(a => !a.startsWith('-'));
const sceneIds = argIds.length ? argIds : [
  'scene01', 'scene02', 'scene03', 'scene10', 'scene11',
  'scene_hero', 'chapter1', 'chapter2', 'chapter3', 'chapter4', 'chapter5',
];
const durationFor = Object.fromEntries(narration.scenes.map(s => [s.id, s.duration]));

const browser = await chromium.launch();
for (const id of sceneIds) {
  const html = resolve(motionDir, `${id}.html`);
  const htmlContent = readFileSync(html, 'utf8');
  const htmlDurMatch = htmlContent.match(/scene-duration"\s+content="(\d+)"/);
  const htmlDur = htmlDurMatch ? parseInt(htmlDurMatch[1], 10) : 20;
  const dur = durationFor[id] || htmlDur;
  // 애니메이션 가속 비율 — scene duration 이 짧으면 animation 을 더 빠르게 흘림
  const animScale = htmlDur / dur;
  const totalFrames = Math.round(dur * 30);
  const framesDir = resolve(outDir, '_frames', id);
  if (existsSync(framesDir)) rmSync(framesDir, { recursive: true, force: true });
  mkdirSync(framesDir, { recursive: true });

  console.log(`[${id}] duration=${dur}s · htmlDur=${htmlDur}s · animScale=${animScale.toFixed(2)}x · ${totalFrames} 프레임`);
  const context = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
    deviceScaleFactor: 1,
  });
  const page = await context.newPage();
  await page.goto(pathToFileURL(html).toString(), { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);

  // 모든 CSS animation 을 멈춤
  await page.evaluate(() => {
    for (const a of document.getAnimations()) {
      try { a.pause(); } catch (e) {}
    }
  });

  const t0 = Date.now();
  for (let i = 0; i < totalFrames; i++) {
    const tMs = ((i * 1000) / 30) * animScale;
    await page.evaluate((t) => {
      for (const a of document.getAnimations()) {
        try { a.currentTime = t; } catch (e) {}
      }
    }, tMs);
    await page.screenshot({
      path: resolve(framesDir, `f_${String(i).padStart(5, '0')}.png`),
      type: 'png',
      fullPage: false,
      clip: { x: 0, y: 0, width: 1920, height: 1080 },
    });
    if (i % 60 === 0) {
      const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
      process.stdout.write(`  ${i}/${totalFrames}  ${elapsed}s\r`);
    }
  }
  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\n  캡처 완료 (${elapsed}s)`);

  await context.close();

  // ffmpeg: PNG 시퀀스 → mp4
  const target = resolve(outDir, `${id}.mp4`);
  if (existsSync(target)) unlinkSync(target);
  const cmd = [
    'ffmpeg', '-y',
    '-framerate', '30',
    '-i', `"${resolve(framesDir, 'f_%05d.png')}"`,
    '-c:v', 'libx264', '-crf', '18', '-preset', 'veryfast',
    '-pix_fmt', 'yuv420p',
    '-r', '30',
    `"${target}"`,
  ].join(' ');
  execSync(cmd, { stdio: 'inherit' });
  console.log(`  → ${target}`);

  // 임시 프레임 삭제
  rmSync(framesDir, { recursive: true, force: true });
}
await browser.close();
console.log('\n모션 프레임 렌더링 완료.');
