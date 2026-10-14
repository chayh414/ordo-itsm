// 최종 mp4에서 각 scene 대표 지점 캡처 1장씩 → docs/video/output/captures/
// 지점: 해당 scene의 음성 끝난 직후 (주요 내용이 다 노출된 뒤)
import { execSync } from 'node:child_process';
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const outDir = resolve(videoRoot, 'output', 'captures');
mkdirSync(outDir, { recursive: true });

const timing = JSON.parse(readFileSync(resolve(videoRoot, 'src', 'narration', 'timing.json'), 'utf8'));
const finalMp4 = resolve(videoRoot, 'output', 'ordo-demo-final.mp4');

// scene별 실제 clip 길이 (compose 수정 후 기준)
// 모션은 모션 webm 길이, 녹화는 trim 또는 음성 길이에 맞춰짐
const sceneRunlens = {
  scene01: 21.68,
  scene02: 20.30,
  scene03: 20.30,
  scene04: 24,
  scene05: 18.47,  // voice 18.17 + 0.3 pad
  scene06: 18,
  scene07: 28.02,  // voice 27.72 + 0.3 pad
  scene08: 34,
  scene09: 25.44 + 25.44, // concat
  scene10: 20.30,
  scene11: 15.30,
};

// capture 지점: 각 scene의 2/3 지점 (요소가 다 나타난 뒤)
let offset = 0;
for (const s of timing.scenes) {
  const dur = sceneRunlens[s.id] ?? s.duration;
  const t = offset + Math.max(1, dur * 0.6);
  const out = resolve(outDir, `${s.id}.png`);
  const cmd = `ffmpeg -y -v error -ss ${t.toFixed(2)} -i "${finalMp4}" -frames:v 1 "${out}"`;
  try {
    execSync(cmd);
    console.log(`→ ${s.id}.png (t=${t.toFixed(2)}s)`);
  } catch (e) {
    console.error(`${s.id} 캡처 실패:`, e.message);
  }
  offset += dur;
}
console.log('캡처 완료. 총 offset =', offset.toFixed(2));
