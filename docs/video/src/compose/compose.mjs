// compose v2 — 30fps 통일 · 각 scene clip 길이 = 음성 길이 + 0.8s · freeze 금지 · xfade 전환 · 자막 수정
import { execSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const recDir = resolve(videoRoot, 'assets', 'recordings');
const voiceDir = resolve(videoRoot, 'assets', 'voice');
const outDir = resolve(videoRoot, 'output');
const clipDir = resolve(outDir, 'clips');
mkdirSync(clipDir, { recursive: true });

const timing = JSON.parse(readFileSync(resolve(videoRoot, 'src', 'narration', 'timing.json'), 'utf8'));
const narration = JSON.parse(readFileSync(resolve(videoRoot, 'src', 'narration', 'narration.json'), 'utf8'));
// narration.json 의 최신 duration 을 timing.scenes 에 반영
const durationFromNar = Object.fromEntries(narration.scenes.map(s => [s.id, s.duration]));
const scenesSpec = timing.scenes.map(s => ({ ...s, duration: durationFromNar[s.id] ?? s.duration }));

// 영상 원본 (모션은 render-frames.mjs 로 생성한 .mp4, 녹화는 .webm)
const sources = {
  scene01: { file: 'scene01.mp4' },
  scene02: { file: 'scene02.mp4' },
  scene03: { file: 'scene03.mp4' },
  scene_hero: { file: 'scene_hero.mp4' },
  chapter1: { file: 'chapter1.mp4' },
  scene04: { file: 'scene040506.webm', trimStart: 0,    trimDur: 24 },
  chapter2: { file: 'chapter2.mp4' },
  scene05: { file: 'scene040506.webm', trimStart: 24,   trimDur: 13 },
  scene06: { file: 'scene040506.webm', trimStart: 37,   trimDur: 18 },
  chapter3: { file: 'chapter3.mp4' },
  scene07: { file: 'scene07.webm' },
  chapter4: { file: 'chapter4.mp4' },
  scene08: { file: 'scene08.webm', composed: true },
  scene08b: { file: 'scene08b.webm', trimStart: 2, trimDur: 15 },
  chapter5: { file: 'chapter5.mp4' },
  scene09: { file: 'scene09.webm', composed: true },
  scene10: { file: 'scene10.mp4' },
  scene11: { file: 'scene11.mp4' },
};

function sh(cmd, label, opts = {}) {
  console.log(`▶ ${label}`);
  execSync(cmd, { stdio: 'inherit', ...opts });
}

function probeDur(file) {
  const out = execSync(
    `ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "${file}"`,
    { encoding: 'utf8' }
  );
  return parseFloat(out.trim());
}

// SCENE 08: 세 녹화의 crop(사용자 이름표 + 토스트) → hstack 가로 3개
//   이름표 bar가 상단 52px, 토스트 top:160, bottom 아래로 ~300px. 전체 ~500px.
//   crop 440×500, offset_y=0, offset_x=1000 (right side)
function composeScene08() {
  const outFile = resolve(recDir, 'scene08.webm');
  const op = resolve(recDir, 'scene08-operator.webm');
  const rA = resolve(recDir, 'scene08-requesterA.webm');
  const rB = resolve(recDir, 'scene08-requesterB.webm');
  if (!existsSync(op) || !existsSync(rA) || !existsSync(rB)) {
    console.warn('scene08 녹화 파일 누락 — 합성 스킵');
    return false;
  }
  const dur = Math.min(probeDur(op), probeDur(rA), probeDur(rB));
  const d = Math.max(10, Math.min(dur, 60));
  const cmd = [
    'ffmpeg', '-y',
    '-i', `"${op}"`,
    '-i', `"${rA}"`,
    '-i', `"${rB}"`,
    '-filter_complex',
    `"[0:v]crop=440:500:1000:0,setsar=1[a];` +
    `[1:v]crop=440:500:1000:0,setsar=1[b];` +
    `[2:v]crop=440:500:1000:0,setsar=1[c];` +
    `[a][b][c]hstack=inputs=3[row];` +
    `[row]scale=1680:-2,setsar=1,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white[v]"`,
    '-map', '"[v]"',
    '-t', String(d),
    '-c:v', 'libvpx-vp9', '-b:v', '0', '-crf', '28', '-row-mt', '1',
    `"${outFile}"`
  ].join(' ');
  sh(cmd, 'SCENE 08 hstack (3분할 crop)');
  return true;
}

// SCENE 09: 김요청 + 한은행 concat
function composeScene09() {
  const outFile = resolve(recDir, 'scene09.webm');
  const rA = resolve(recDir, 'scene09-requesterA.webm');
  const rB = resolve(recDir, 'scene09-requesterB.webm');
  if (!existsSync(rA) || !existsSync(rB)) {
    console.warn('scene09 녹화 파일 누락 — 합성 스킵');
    return false;
  }
  const cmd = [
    'ffmpeg', '-y',
    '-i', `"${rA}"`,
    '-i', `"${rB}"`,
    '-filter_complex',
    `"[0:v]scale=1680:-2:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white,setsar=1[a];` +
    `[1:v]scale=1680:-2:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white,setsar=1[b];` +
    `[a][b]concat=n=2:v=1[v]"`,
    '-map', '"[v]"',
    '-c:v', 'libvpx-vp9', '-b:v', '0', '-crf', '28', '-row-mt', '1',
    `"${outFile}"`
  ].join(' ');
  sh(cmd, 'SCENE 09 concat');
  return true;
}

// 챕터별 진행 표시 (계단 5칸)
const progressIdxMap = {
  chapter1: 1, scene04: 1,
  chapter2: 2, scene05: 2, scene06: 2,
  chapter3: 3, scene07: 3,
  chapter4: 4, scene08: 4, scene08b: 4,
  chapter5: 5, scene09: 5,
};

// 각 scene mp4 clip 생성 — 영상 길이 = max(음성 길이 + 0.8s, scene.duration)
// 영상 길이가 target과 다르면 setpts 로 조정 (freeze 금지, 미세 slow/fast)
function makeClip(s) {
  const src = sources[s.id];
  const voice = resolve(voiceDir, `${s.id}.mp3`);
  const voiceExists = existsSync(voice);
  const voiceLen = voiceExists ? (s.voiceDuration || 0) : 0;
  const target = voiceExists ? Math.max(voiceLen + 0.8, s.duration) : s.duration;
  const outFile = resolve(clipDir, `${s.id}.mp4`);

  const inFile = resolve(recDir, src.file);
  if (!existsSync(inFile)) {
    console.warn(`${s.id}: 영상 없음 → 흰 배경 placeholder`);
    const cmd = [
      'ffmpeg', '-y',
      '-f', 'lavfi', '-i', `color=c=white:s=1920x1080:d=${target.toFixed(2)}:r=30`,
      '-i', `"${voice}"`,
      '-shortest',
      '-c:v', 'libx264', '-crf', '20', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '192k',
      `"${outFile}"`
    ].join(' ');
    sh(cmd, `${s.id} placeholder`);
    return outFile;
  }

  // 원본 영상 길이 결정
  const inProbe = probeDur(inFile);
  const srcLen = src.trimDur || inProbe;
  // setpts scale: target / srcLen > 1 이면 slow, < 1 이면 fast
  const ptsScale = target / srcLen;

  // 영상 필터
  // 녹화 clip(모션 아닌 것) 분류 — scene_hero, chapter N 은 모션으로 간주
  const motionIds = /^(scene(0[123]|1[01])|scene_hero|chapter[1-5])$/;
  const isRecorded = !motionIds.test(s.id);
  // 녹화는 1680 pad, 모션(이미 1920×1080)은 그대로. zoomTail 이 crop 하므로 pad 영역이 좀 커도 상관없음
  const commonTail = `scale=1680:-2:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white,setsar=1,fps=30`;
  // 전역 sine pan 제거 — 사용자 피드백 (영상 전체 흔들림 금지, 가장자리 잘림 없음)
  // 4~5초 정지는 OK. 핵심 순간 의도적 줌만 scene04 segment-based 처리에서 유지.
  const zoomTail = '';

  let vFilter;
  if (s.id === 'scene04') {
    // 녹화 timeline (webm 실제): 0~10s 로그인+입력, 10~22s "작성 중"(4x fast), 22~24s 요청서+overlay(slow)
    // target=15s 로 단축: A(8s) + B(2.3s · 14s→2.3s) + C(4.7s · 2s→4.7s, 2.35x slow)
    const aLen = 8;
    const bPts = 2.3 / 14;
    const cSrcLen = 2;
    const cTarget = Math.max(2, target - aLen - 2.3);
    const cPts = cTarget / cSrcLen;
    vFilter =
      `[0:v]trim=0:8,setpts=PTS-STARTPTS[a04];` +
      `[0:v]trim=8:22,setpts=(PTS-STARTPTS)*${bPts.toFixed(4)}[b04];` +
      `[0:v]trim=22:24,setpts=(PTS-STARTPTS)*${cPts.toFixed(4)}[c04];` +
      `[a04][b04][c04]concat=n=3:v=1[cat04];` +
      `[cat04]${commonTail}${zoomTail}[v]`;
  } else if (src.trimDur !== undefined) {
    vFilter =
      `[0:v]trim=start=${src.trimStart}:duration=${src.trimDur},setpts=(PTS-STARTPTS)*${ptsScale.toFixed(4)},` +
      `${commonTail}${zoomTail}[v]`;
  } else {
    vFilter =
      `[0:v]setpts=(PTS-STARTPTS)*${ptsScale.toFixed(4)},` +
      `scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white,setsar=1,fps=30${zoomTail}[v]`;
  }

  // 챕터 진행 표시 overlay
  const progressIdx = progressIdxMap[s.id];
  const progressPng = progressIdx
    ? resolve(videoRoot, 'assets', 'overlay', `progress-${progressIdx}.png`)
    : null;
  const overlayStage = progressPng
    ? `;[v][${voiceExists ? 2 : 2}:v]overlay=W-w-32:28:format=auto[vout]`
    : '';
  const vMap = progressPng ? '[vout]' : '[v]';

  if (voiceExists) {
    const aFilter = `[1:a]apad=pad_dur=${(target - voiceLen).toFixed(2)}[a]`;
    const inputs = ['-i', `"${inFile}"`, '-i', `"${voice}"`];
    if (progressPng) inputs.push('-i', `"${progressPng}"`);
    const cmd = [
      'ffmpeg', '-y',
      ...inputs,
      '-filter_complex', `"${vFilter};${aFilter}${overlayStage}"`,
      '-map', `"${vMap}"`, '-map', '"[a]"',
      '-t', target.toFixed(2),
      '-c:v', 'libx264', '-crf', '20', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '192k',
      `"${outFile}"`,
    ].join(' ');
    sh(cmd, `${s.id} clip (voice=${voiceLen.toFixed(2)}s · target=${target.toFixed(2)}s · pts×${ptsScale.toFixed(3)}${progressIdx ? ` · prog ${progressIdx}` : ''})`);
  } else {
    // 챕터 카드 등 음성 없는 scene — 영상만, 무음 트랙 추가
    const inputs = ['-i', `"${inFile}"`, '-f', 'lavfi', '-i', 'anullsrc=channel_layout=stereo:sample_rate=48000'];
    if (progressPng) inputs.push('-i', `"${progressPng}"`);
    const cmd = [
      'ffmpeg', '-y',
      ...inputs,
      '-filter_complex', `"${vFilter}${overlayStage}"`,
      '-map', `"${vMap}"`, '-map', '1:a',
      '-t', target.toFixed(2),
      '-c:v', 'libx264', '-crf', '20', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '192k',
      `"${outFile}"`,
    ].join(' ');
    sh(cmd, `${s.id} clip (voice=없음 · target=${target.toFixed(2)}s · pts×${ptsScale.toFixed(3)}${progressIdx ? ` · prog ${progressIdx}` : ''})`);
  }
  return outFile;
}

// 각 scene 끝과 다음 scene 시작을 xfade 로 crossfade
function concatXfade(clips, durations) {
  // 각 전환 xfade duration = 0.5s
  const X = 0.5;
  // 누적 offset 계산 (전환마다 X 만큼 단축)
  // v0 길이 d0, 전환 X 후 v1 시작은 offset = d0 - X
  // v01 길이 = d0 + d1 - X
  // v01 다음 전환은 offset = d0 + d1 - 2X
  const outFile = resolve(outDir, 'video-concat.mp4');

  const inputs = clips.map(f => ['-i', `"${f}"`]).flat();
  let fc = '';
  // 첫 cross-fade 는 [0:v][1:v]. 그 뒤는 [vN][M:v]  (label은 콜론 없이)
  let lastRef = '[0:v]';
  let runningLen = durations[0];
  for (let i = 1; i < clips.length; i++) {
    const nextLabel = (i === clips.length - 1) ? 'v' : `v${i}`;
    const offset = (runningLen - X).toFixed(3);
    fc += `${lastRef}[${i}:v]xfade=transition=fade:duration=${X}:offset=${offset}[${nextLabel}];`;
    runningLen = runningLen + durations[i] - X;
    lastRef = `[${nextLabel}]`;
  }
  // 오디오 concat (crossfade 없이 연속)
  const aParts = clips.map((_, i) => `[${i}:a]`).join('');
  fc += `${aParts}concat=n=${clips.length}:v=0:a=1[a]`;

  const cmd = [
    'ffmpeg', '-y',
    ...inputs,
    '-filter_complex', `"${fc}"`,
    '-map', '"[v]"', '-map', '"[a]"',
    '-c:v', 'libx264', '-crf', '20', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
    '-c:a', 'aac', '-b:a', '192k',
    `"${outFile}"`,
  ].join(' ');
  sh(cmd, `concat xfade (${clips.length} clips, ${X}s crossfade)`);
  return outFile;
}

// freezedetect 자가 검사 (사용자 지시: n=0.003 · d=4)
function freezedetect(file) {
  console.log('\n▶ freezedetect(n=0.003, d=4) 검사');
  const log = execSync(
    `ffmpeg -i "${file}" -vf "freezedetect=n=0.003:d=4" -f null - 2>&1`,
    { encoding: 'utf8' }
  );
  const freezes = [...log.matchAll(/freeze_start: ([\d.]+)[\s\S]*?freeze_duration: ([\d.]+)[\s\S]*?freeze_end: ([\d.]+)/g)];
  if (freezes.length === 0) {
    console.log('  freeze 구간 없음 (n=0.003, d≥4s)');
    return [];
  }
  const list = freezes.map(m => ({
    start: parseFloat(m[1]),
    duration: parseFloat(m[2]),
    end: parseFloat(m[3]),
  }));
  console.log(`  ${list.length}개 freeze(n=0.003, d≥4s) 감지:`);
  for (const f of list) {
    console.log(`    ${f.start.toFixed(2)}s ~ ${f.end.toFixed(2)}s (${f.duration.toFixed(2)}s)`);
  }
  return list;
}

// ───────────── 실행 ─────────────
console.log('[1/5] SCENE 08·09 합성');
composeScene08();
composeScene09();

console.log('\n[2/5] 11개 scene clip 생성');
const clipFiles = [];
const durations = [];
for (const s of scenesSpec) {
  const f = makeClip(s);
  clipFiles.push(f);
  durations.push(Math.max(s.voiceDuration + 0.8, s.duration));
}
console.log(`\n각 clip 길이: ${durations.map((d, i) => `${scenesSpec[i].id}=${d.toFixed(2)}s`).join(' · ')}`);
console.log(`합계 (xfade 11개 · 0.5s): ${(durations.reduce((a,b)=>a+b, 0) - 0.5 * (durations.length - 1)).toFixed(2)}s`);

console.log('\n[3/5] xfade concat');
const concatMp4 = concatXfade(clipFiles, durations);
const finalDur = probeDur(concatMp4);
console.log(`concat 결과 길이: ${finalDur.toFixed(2)}s`);

console.log('\n[4/5] BGM 믹싱 + 자막 (ass alpha 수정) + 최종 인코딩');
const bgm = resolve(outDir, 'bgm-5min.mp3');
const srt = resolve(outDir, 'subtitles.srt');

const finalMp4 = resolve(outDir, 'ordo-demo-final.mp4');
const previewMp4 = resolve(outDir, 'preview.mp4');

const finalFilter =
  `"[1:a]aformat=channel_layouts=stereo,volume=-14dB,atrim=0:${finalDur.toFixed(2)},asetpts=PTS-STARTPTS[bgm];` +
  `[0:a][bgm]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[aout]"`;

// 자막: BorderStyle=3 반투명 검정 박스 + 얇은 외곽선 (libass BorderStyle=4 미지원 회피)
// FontSize=34 · MarginV=60 (요약 숫자카드 안 가리게)
sh(
  `ffmpeg -y -i "${concatMp4}" -i "${bgm}" -filter_complex ${finalFilter} ` +
  `-map 0:v -map "[aout]" ` +
  `-vf "subtitles=subtitles.srt:force_style='FontName=Malgun Gothic,FontSize=34,PrimaryColour=&H00FFFFFF,BackColour=&HA0000000,BorderStyle=3,Outline=8,Shadow=0,MarginV=60,PlayResX=1920,PlayResY=1080'" ` +
  `-c:v libx264 -crf 20 -preset veryfast -pix_fmt yuv420p ` +
  `-c:a aac -b:a 192k ` +
  `"${finalMp4}"`,
  'final 1080p',
  { cwd: outDir }
);

sh(
  `ffmpeg -y -i "${finalMp4}" -vf "scale=1280:720" -c:v libx264 -crf 23 -preset veryfast -pix_fmt yuv420p -c:a copy "${previewMp4}"`,
  'preview 720p'
);

console.log('\n[5/5] freezedetect 자가 검사');
const freezes = freezedetect(finalMp4);
writeFileSync(resolve(outDir, 'freezedetect.json'), JSON.stringify({ finalDur, freezes }, null, 2));

console.log('\n완료:');
console.log('  final:   ', finalMp4);
console.log('  preview: ', previewMp4);
console.log('  freeze 로그:', resolve(outDir, 'freezedetect.json'));
