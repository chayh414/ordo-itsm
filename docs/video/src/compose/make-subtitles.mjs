// narration.json + timing.json 을 바탕으로 문장 단위 SRT 자막 생성
// 각 scene의 음성이 그 scene의 시작 시각에서 재생된다고 가정, 문장 글자 수 비율로 분배
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const narration = JSON.parse(readFileSync(resolve(videoRoot, 'src', 'narration', 'narration.json'), 'utf8'));
const timing = JSON.parse(readFileSync(resolve(videoRoot, 'src', 'narration', 'timing.json'), 'utf8'));

const timingById = Object.fromEntries(timing.scenes.map(s => [s.id, s]));

const outDir = resolve(videoRoot, 'output');
mkdirSync(outDir, { recursive: true });
const outPath = resolve(outDir, 'subtitles.srt');

function splitSentences(text) {
  // 종결 부호 뒤 공백 또는 끝. 안쪽 공백은 유지.
  const parts = text.split(/(?<=[.?!])\s+/);
  return parts.map(p => p.trim()).filter(Boolean);
}

function fmt(seconds) {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  const ms = Math.round((seconds - Math.floor(seconds)) * 1000);
  return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')},${String(ms).padStart(3,'0')}`;
}

let sceneOffset = 0;
let idx = 1;
const lines = [];

for (const scene of narration.scenes) {
  const t = timingById[scene.id];
  const voiceLen = t ? t.voiceDuration : scene.duration * 0.5;

  const sentences = splitSentences(scene.text);
  const totalChars = sentences.reduce((a, s) => a + s.length, 0);

  // 문장별 분배 시간은 글자수 비례. 자막 유지 시간이 너무 짧으면 최소 1.4s.
  let localStart = 0;
  for (const sentence of sentences) {
    const share = (sentence.length / totalChars) * voiceLen;
    const dur = Math.max(1.4, share);
    const abStart = sceneOffset + localStart;
    const abEnd = sceneOffset + Math.min(voiceLen, localStart + dur);
    lines.push(`${idx}`);
    lines.push(`${fmt(abStart)} --> ${fmt(abEnd)}`);
    lines.push(sentence);
    lines.push('');
    idx++;
    localStart += dur;
  }

  sceneOffset += scene.duration;
}

writeFileSync(outPath, lines.join('\n'), 'utf8');
console.log(`→ ${outPath}  (총 ${idx-1}개 자막)`);
