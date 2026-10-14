// SCENE 04 문장으로 ko-KR 음성 샘플 3개 생성 → output/voice-samples/
import { MsEdgeTTS, OUTPUT_FORMAT } from 'msedge-tts';
import { mkdirSync, renameSync, existsSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const outDir = resolve(videoRoot, 'output', 'voice-samples');
mkdirSync(outDir, { recursive: true });

// 피드백 반영 안: 문장 짧게 끊고, 쉼표 뒤 자연스러운 쉼 + 느린 속도
const sampleText = '김요청 님은 오늘 밤 주문 시스템 작업을 앞두고 있습니다. 양식 없이, 평소처럼 말하면 됩니다. 등록해 달라고 하지 않아도, 오르도가 작업 요청으로 알아듣고 요청서 초안을 만듭니다. 위험 요인마다, 판단 근거가 된 원래 문장이 함께 표시됩니다. 점수는 에이아이가 아니라, 정해진 규칙이 계산합니다.';

const voices = [
  { id: 'sunhi',     name: 'ko-KR-SunHiNeural',              rate: '-5%' },
  { id: 'injoon',    name: 'ko-KR-InJoonNeural',             rate: '-5%' },
  { id: 'hyunsu-ml', name: 'ko-KR-HyunsuMultilingualNeural', rate: '-5%' },
];

for (const v of voices) {
  const tts = new MsEdgeTTS();
  await tts.setMetadata(v.name, OUTPUT_FORMAT.AUDIO_24KHZ_48KBITRATE_MONO_MP3);
  const target = resolve(outDir, `scene04-${v.id}.mp3`);
  if (existsSync(target)) unlinkSync(target);
  console.log(`[${v.id}] 생성: ${v.name} (rate=${v.rate})`);
  const { audioFilePath } = await tts.toFile(outDir, sampleText, { rate: v.rate });
  renameSync(audioFilePath, target);
  console.log(`  → ${target}`);
  tts.close();
}
console.log('샘플 3개 완료.');
