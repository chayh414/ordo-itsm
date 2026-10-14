// 전체 합성 — scene 11개를 1080p / 720p 두 벌로 생성
// 각 scene별:
//   • 모션(5개): webm 통째 사용
//   • 녹화 clip 통합 scene040506 → 04/05/06 세 clip으로 trim
//   • scene07·08·09는 각각 독립 녹화
// scene별 흐름:
//   영상 → 1920×1080 흰 캔버스 중앙 pad → 해당 scene 음성과 mux → scene_NN.mp4
// 전체:
//   mp4 concat → BGM 믹싱 (sidechaincompress로 음성 구간 자동 감쇠) → 자막 하드서브
//   final 1080p + preview 720p
//
// 사용: node src/compose/compose.mjs
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
const scenesSpec = timing.scenes; // id, duration, voiceDuration, text

// 각 scene 영상 원본 (webm 또는 trim 명세)
const sources = {
  scene01: { file: 'scene01.webm' },
  scene02: { file: 'scene02.webm' },
  scene03: { file: 'scene03.webm' },
  // timeline(마크): draft=10.3, overlay=12.3, sup_typed=23.7, sup_saved=24.1, overlay97=28.1,
  //                 submit=34.1, list_sent=37.4, method_q=45.3, end=55.3
  scene04: { file: 'scene040506.webm', trimStart: 0,    trimDur: 24 },  // 로그인~overlay_score 끝
  scene05: { file: 'scene040506.webm', trimStart: 24,   trimDur: 13 },  // supplement~접수
  scene06: { file: 'scene040506.webm', trimStart: 37,   trimDur: 18 },  // 조회+방법 질문
  scene07: { file: 'scene07.webm' },
  scene08: { file: 'scene08.webm', composed: true }, // xstack 결과를 만들 예정
  scene09: { file: 'scene09.webm', composed: true }, // concat 결과를 만들 예정
  scene10: { file: 'scene10.webm' },
  scene11: { file: 'scene11.webm' },
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

// SCENE 08 합성: 세 녹화의 오른쪽 위 cropped 영역을 가로로 hstack
// 각 녹화는 1440×900, "사용자 이름 + 토스트"가 뜨는 영역
//   TopBar 사용자명: 오른쪽 상단 (대략 right=0 top=0 width=400 height=64)
//   ToastContainer: 명세 디자인에서 오른쪽 상단, 토스트가 뜨면 width ~400, height ~80 (여러 개 쌓임)
//   crop 영역: right 440px, top 0, height 400 → crop=440:400:(w-440):0
// 가로 3개 hstack: 각 440×400 → 1320×400 → 1920×1080 캔버스 세로 중앙 pad
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
  const d = Math.max(10, Math.min(dur, 50));
  const cmd = [
    'ffmpeg', '-y',
    '-i', `"${op}"`,
    '-i', `"${rA}"`,
    '-i', `"${rB}"`,
    '-filter_complex',
    `"[0:v]crop=440:460:1000:0,setsar=1[a];[1:v]crop=440:460:1000:0,setsar=1[b];[2:v]crop=440:460:1000:0,setsar=1[c];[a][b][c]hstack=inputs=3[row];[row]scale=1920:-2,pad=1920:1080:0:(oh-ih)/2:white,setsar=1[v]"`,
    '-map', '"[v]"',
    '-t', String(d),
    '-c:v', 'libvpx-vp9', '-b:v', '0', '-crf', '28', '-row-mt', '1',
    `"${outFile}"`
  ].join(' ');
  sh(cmd, 'SCENE 08 hstack 합성');
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
    `"[0:v]scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white,setsar=1[a];[1:v]scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white,setsar=1[b];[a][b]concat=n=2:v=1[v]"`,
    '-map', '"[v]"',
    '-c:v', 'libvpx-vp9', '-b:v', '0', '-crf', '28', '-row-mt', '1',
    `"${outFile}"`
  ].join(' ');
  sh(cmd, 'SCENE 09 concat');
  return true;
}

// 각 scene mp4 clip 생성 (영상 + 음성, duration은 영상 길이)
function makeClip(id) {
  const src = sources[id];
  const inFile = resolve(recDir, src.file);
  if (!existsSync(inFile)) {
    console.warn(`${id}: 영상 없음 (${inFile}) — 검정 10s 자리채움`);
    const outFile = resolve(clipDir, `${id}.mp4`);
    const voice = resolve(voiceDir, `${id}.mp3`);
    const cmd = [
      'ffmpeg', '-y',
      '-f', 'lavfi', '-i', 'color=c=white:s=1920x1080:d=10:r=30',
      '-i', `"${voice}"`,
      '-shortest',
      '-c:v', 'libx264', '-crf', '20', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '192k',
      `"${outFile}"`
    ].join(' ');
    sh(cmd, `${id} placeholder (영상 없음)`);
    return outFile;
  }

  const voice = resolve(voiceDir, `${id}.mp3`);
  if (!existsSync(voice)) {
    console.warn(`${id}: 음성 없음 — 무음 사용`);
  }

  const outFile = resolve(clipDir, `${id}.mp4`);

  // 영상 scale/pad: 모션은 이미 1920×1080, 녹화는 1440×900 → 중앙 pad
  const inProbe = probeDur(inFile);
  let vFilter;
  if (id.match(/^scene0[4-6]$/)) {
    // trim 범위에서 자르고 1920×1080 중앙 pad
    const t0 = src.trimStart, dur = src.trimDur;
    vFilter = `[0:v]trim=start=${t0}:duration=${dur},setpts=PTS-STARTPTS,scale=1680:1050:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white,setsar=1,fps=30[v]`;
  } else if (id === 'scene07') {
    vFilter = `[0:v]scale=1680:1050:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:white,setsar=1,fps=30[v]`;
  } else {
    // 모션·scene08·scene09는 이미 1920×1080
    vFilter = `[0:v]scale=1920:1080,setsar=1,fps=30[v]`;
  }

  // 음성 길이 측정
  let voiceLen = 0;
  if (existsSync(voice)) voiceLen = probeDur(voice);

  // 영상 길이 결정 (trim 포함)
  let clipLen = inProbe;
  if (src.trimDur) clipLen = src.trimDur;
  // 영상·음성 중 더 긴 쪽에 맞춤
  const target = Math.max(clipLen, voiceLen + 0.3);
  const aFilter = existsSync(voice)
    ? `[1:a]apad=pad_dur=${Math.max(0, target - voiceLen).toFixed(2)}[a]`
    : `aevalsrc=0:d=${target.toFixed(2)}[a]`;

  // 영상이 음성보다 짧으면 마지막 프레임 freeze (tpad)
  if (voiceLen + 0.3 > clipLen) {
    const padDur = voiceLen + 0.3 - clipLen;
    vFilter = vFilter.replace('[v]', `[vraw];[vraw]tpad=stop_mode=clone:stop_duration=${padDur.toFixed(2)}[v]`);
  }

  const inputs = existsSync(voice)
    ? ['-i', `"${inFile}"`, '-i', `"${voice}"`]
    : ['-i', `"${inFile}"`];
  const filterComplex = existsSync(voice) ? `"${vFilter};${aFilter}"` : `"${vFilter}"`;
  const map = existsSync(voice) ? ['-map', '"[v]"', '-map', '"[a]"'] : ['-map', '"[v]"'];

  const cmd = [
    'ffmpeg', '-y',
    ...inputs,
    '-filter_complex', filterComplex,
    ...map,
    '-t', target.toFixed(2),
    '-c:v', 'libx264', '-crf', '20', '-preset', 'veryfast', '-pix_fmt', 'yuv420p',
    ...(existsSync(voice) ? ['-c:a', 'aac', '-b:a', '192k'] : []),
    `"${outFile}"`
  ].join(' ');
  sh(cmd, `${id} clip (len=${target.toFixed(2)}s)`);
  return outFile;
}

// ───────────── 실행 ─────────────
console.log('[1/4] SCENE 08·09 합성');
composeScene08();
composeScene09();

console.log('\n[2/4] 11개 scene clip 생성');
const clipFiles = [];
for (const s of scenesSpec) {
  const f = makeClip(s.id);
  clipFiles.push(f);
}

console.log('\n[3/4] concat');
const concatList = resolve(clipDir, 'concat.txt');
writeFileSync(concatList, clipFiles.map(f => `file '${f.replace(/\\/g,'/')}'`).join('\n'), 'utf8');
const concatMp4 = resolve(outDir, 'video-concat.mp4');
sh(`ffmpeg -y -f concat -safe 0 -i "${concatList}" -c:v libx264 -crf 20 -preset veryfast -pix_fmt yuv420p -c:a aac -b:a 192k "${concatMp4}"`, 'concat');

console.log('\n[4/4] BGM 믹싱 + 자막 + 최종 인코딩');
const bgm = resolve(outDir, 'bgm-5min.mp3');
const srt = resolve(outDir, 'subtitles.srt');
const finalDur = probeDur(concatMp4);
console.log(`최종 길이: ${finalDur.toFixed(2)}s`);

// BGM + 자막 하드서브
// Windows 경로 escape 회피: cwd=outDir 로 실행하고 상대경로 subtitles.srt 사용
const finalMp4 = resolve(outDir, 'ordo-demo-final.mp4');
const previewMp4 = resolve(outDir, 'preview.mp4');

const finalFilter = `"[1:a]aformat=channel_layouts=stereo,volume=-14dB,atrim=0:${finalDur.toFixed(2)},asetpts=PTS-STARTPTS[bgm];[0:a][bgm]amix=inputs=2:duration=first:dropout_transition=0:normalize=0[aout]"`;

sh(
  `ffmpeg -y -i "${concatMp4}" -i "${bgm}" -filter_complex ${finalFilter} ` +
  `-map 0:v -map "[aout]" ` +
  `-vf "subtitles=subtitles.srt:force_style='FontName=Pretendard,FontSize=22,PrimaryColour=&HFFFFFFFF,OutlineColour=&HFF000000,BorderStyle=1,Outline=2,Shadow=0,MarginV=48'" ` +
  `-c:v libx264 -crf 20 -preset slow -pix_fmt yuv420p ` +
  `-c:a aac -b:a 192k ` +
  `"${finalMp4}"`,
  'final 1080p',
  { cwd: outDir }
);

sh(
  `ffmpeg -y -i "${finalMp4}" -vf "scale=1280:720" -c:v libx264 -crf 23 -preset veryfast -pix_fmt yuv420p -c:a copy "${previewMp4}"`,
  'preview 720p'
);

console.log('\n완료:');
console.log('  final:   ', finalMp4);
console.log('  preview: ', previewMp4);
