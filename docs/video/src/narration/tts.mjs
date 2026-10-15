// msedge-tts로 narration.json 의 11개 scene을 mp3로 생성
// 출력: docs/video/assets/voice/sceneNN.mp3
// 길이 측정: ffprobe → docs/video/src/narration/timing.json
import { MsEdgeTTS, OUTPUT_FORMAT } from 'msedge-tts';
import { readFileSync, writeFileSync, mkdirSync, renameSync, existsSync, unlinkSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const voiceDir = resolve(videoRoot, 'assets', 'voice');
mkdirSync(voiceDir, { recursive: true });

const spec = JSON.parse(readFileSync(resolve(__dirname, 'narration.json'), 'utf8'));

function probeDuration(file) {
  const out = execSync(
    `ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "${file}"`,
    { encoding: 'utf8' }
  );
  return parseFloat(out.trim());
}

const timing = { voice: spec.voice, rate: spec.rate, scenes: [] };

const tts = new MsEdgeTTS();
await tts.setMetadata(spec.voice, OUTPUT_FORMAT.AUDIO_24KHZ_48KBITRATE_MONO_MP3);

for (const s of spec.scenes) {
  const finalFile = resolve(voiceDir, `${s.id}.mp3`);
  if (existsSync(finalFile)) unlinkSync(finalFile);
  const ttsText = (s.tts ?? s.text ?? '').trim();
  if (!ttsText) {
    // 음성 없는 scene (챕터 카드 등) — timing 에만 기록, 음성 안 생성
    console.log(`[${s.id}] 음성 없음 (duration=${s.duration}s)`);
    timing.scenes.push({ id: s.id, duration: s.duration, voiceDuration: 0, text: s.text || '' });
    continue;
  }
  console.log(`[${s.id}] 생성 중...`);
  const { audioFilePath } = await tts.toFile(voiceDir, ttsText, { rate: spec.rate });
  renameSync(audioFilePath, finalFile);
  const dur = probeDuration(finalFile);
  console.log(`  → ${finalFile}  (${dur.toFixed(2)}s / scene ${s.duration}s)`);
  timing.scenes.push({ id: s.id, duration: s.duration, voiceDuration: dur, text: s.text });
}

tts.close();

writeFileSync(
  resolve(__dirname, 'timing.json'),
  JSON.stringify(timing, null, 2),
  'utf8'
);
console.log('\n모든 TTS 완료. timing.json 저장됨.');
