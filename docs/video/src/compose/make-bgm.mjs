// 2:12 BGM mp3 × 3 crossfade concat → 300s trim → 끝 3s fade-out
// 입력: assets/music/alex-morgan-no-copyright-music-578487.mp3
// 출력: output/bgm-5min.mp3
import { execSync } from 'node:child_process';
import { mkdirSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const videoRoot = resolve(__dirname, '..', '..');
const src = resolve(videoRoot, 'assets', 'music', 'alex-morgan-no-copyright-music-578487.mp3');
const outDir = resolve(videoRoot, 'output');
mkdirSync(outDir, { recursive: true });
const out = resolve(outDir, 'bgm-5min.mp3');

if (!existsSync(src)) {
  console.error('음악 파일 없음:', src);
  process.exit(1);
}

const cmd = [
  'ffmpeg', '-y',
  '-i', `"${src}"`,
  '-i', `"${src}"`,
  '-i', `"${src}"`,
  '-filter_complex',
  `"[0:a][1:a]acrossfade=d=2:c1=tri:c2=tri[ab];[ab][2:a]acrossfade=d=2:c1=tri:c2=tri,atrim=0:300,afade=t=out:st=297:d=3[out]"`,
  '-map', '"[out]"',
  '-c:a', 'libmp3lame', '-b:a', '192k',
  `"${out}"`
].join(' ');

console.log('실행:', cmd);
execSync(cmd, { stdio: 'inherit' });
console.log('완료:', out);
