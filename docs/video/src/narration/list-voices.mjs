import { MsEdgeTTS } from 'msedge-tts';
const tts = new MsEdgeTTS();
const voices = await tts.getVoices();
const ko = voices.filter(v => (v.Locale || '').startsWith('ko-KR') || /ko-?KR/i.test(v.ShortName) || /Multilingual.*ko-?KR/i.test(v.ShortName));
// Multilingual 음성도 ko-KR 쓸 수 있으므로 포함
const multi = voices.filter(v => /Multilingual/i.test(v.ShortName));
console.log('--- ko-KR voices ---');
for (const v of ko) console.log(`${v.ShortName.padEnd(50)} ${v.Gender}  ${v.FriendlyName}`);
console.log('\n--- Multilingual voices (ko-KR 지원 가능) ---');
for (const v of multi) console.log(`${v.ShortName.padEnd(50)} ${v.Gender}  ${v.FriendlyName}`);
tts.close();
