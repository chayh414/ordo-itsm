# Ordo 시연 영상 — 제작 노트

> 명세: `docs/video/production-spec.md` · 콘티: `Ordo_시연영상_콘티.md` · 기능 근거: `Ordo_발표_근거자료.md`
> 목표: 1920×1080 · 30fps · H.264+AAC · 05:00 · 한국어

## 결정 (사용자 확정)

| 항목 | 값 |
|---|---|
| 기술 | Playwright 녹화 + HTML/CSS 모션 + FFmpeg 합성 (Remotion 안 씀) |
| TTS | **msedge-tts (Node 패키지)** · `ko-KR-SunHiNeural` 기본 속도 (Python 더미 회피) |
| 뷰포트 녹화 | 1440×900 Playwright, 1920×1080 흰 캔버스 중앙 배치 + 얇은 브라우저 프레임 |
| 브랜드 모션 핵심 | SCENE 01 카드 쌓기 · SCENE 03 계단 로고 조립 · SCENE 11 엔딩 |
| 브랜드 모션 간소화 | SCENE 02·10 단순 등장+페이드 |
| 보조 그래픽 | 2개만 — `+3+3+2+1=9` 합산, `9→7` 대비 |
| SCENE 08 3분할 | 각 창의 **오른쪽 위 영역(사용자 이름 + 토스트)만 잘라 가로 3개 배치** (글자 원본 크기 유지) |
| 자막 | narration 대본 기준 문장 단위 SRT (음성 길이 맞춤) |
| 산출 | `preview.mp4` 720p + `ordo-demo-final.mp4` 1080p |

## 배경음악

- 파일: `assets/music/alex-morgan-no-copyright-music-578487.mp3` (2분 12초, Pixabay, 저작권 없음)
- 300초(05:00) 영상에 맞추기 위해 **원본 × 2 + acrossfade 2s + 끝 fade-out**으로 재생성 → `output/bgm-5min.mp3` (저장소 미포함)
- 믹싱: `-16 LUFS` 목표, 음성 구간 `sidechaincompress`로 자동 감쇠
- **저작권 명시 — 발표 자료 하단 또는 설명:** "Music: alex-morgan-no-copyright-music (Pixabay, royalty-free)"

## 발음 치환 (TTS 샘플 확인 결과에 따라 조정)

| 원문 | 내레이션 표기 |
|---|---|
| Ordo | 오르도 |
| SLA | 에스엘에이 |
| AI | 에이아이 |
| ERP | 그대로 (ko-KR TTS가 "이알피"로 읽음, 어긋나면 "이알피"로 치환) |
| AiWACS | 그대로 (한국어 TTS가 "아이왁스"에 가깝게 읽음, 어긋나면 "아이왁스"로 치환) |
| B은행 | 비 은행 (TTS가 "비은행"으로 읽게 공백 삽입) |
| A제조 | 에이 제조 |

## 로그인 계정 (frontend/src/pages/LoginPage.tsx)

| 계정 | 이메일 | 비밀번호 | 빠른 선택 라벨 |
|---|---|---|---|
| 김요청 (요청자 · A제조) | `requester@a-univ.demo` | `Password123!` | 있음 |
| 이운영 (운영 담당자 · 시스원) | `operator@sysone.demo` | `Password123!` | 있음 |
| 박관리 (운영 관리자 · 시스원) | `admin@sysone.demo` | `Password123!` | 있음 |
| **한은행 (요청자 · B은행)** | `requester@b-bank.demo` | `Password123!` | **빠른 선택 없음, 수동 로그인** |

> SCENE 09·11의 한은행 창은 이메일/비밀번호를 수동 입력.

## 알려진 리스크 (명세 8-1 R1~R10)

- **R1 LLM 응답 변동성**: 녹화마다 다름. `timeline.json` 사후 기록, 2회 재시도 후 실패 시 표시만 하고 진행.
- **R3 포인트 색·SLA 통합**: 커밋 `c17d9fc`에서 반영됨 (사용자 확인).
- **R6 숫자**: 자동 테스트 **104개**, 시나리오 **23/25**, 스파이크 **40/40**. 영상 자막·내레이션 통일.
- **R10 뷰포트**: 명세 1920×1080 캔버스 결과물에 1440×900 녹화를 중앙 배치.

## 작업 진행 중 발견한 문제

### 녹화 단계
- **녹화 길이가 콘티보다 짧음**: SCENE 04·05·06 합산 녹화 55초 (콘티 105초의 절반). 각 액션 뒤 연출 pause가 없음. 결과: 전체 러닝타임 약 **4:12** (사용자 요구 "최소 4:00" 충족).
- **백오피스 목록 selector 1회 수정**: `tbody tr` → `ul li button` (BackofficePage.tsx DOM 재확인 후).
- **한은행(B은행) 로그인**: 로그인 페이지 빠른 선택 버튼에 없음 → 이메일/비밀번호 수동 입력으로 처리. SCENE 08·09에 적용.
- **SCENE 04·05·06 trim 좌표 수정 1회**: 처음 scene05=24~42, scene06=42~58로 자르자 "내 요청 보여줘"가 scene05에 끼어들어감 → 재조정: scene05=24~37 (supplement~접수까지), scene06=37~55 (조회+질문).

### 음성/자막
- 전체 음성 duration이 각 scene duration보다 짧아서 공백이 뒤에 들어감 — BGM이 메꿔줌. (예: SCENE 06 음성 8.3s, scene duration 25s)
- SCENE 07 음성 27.7s > 녹화 24.9s → compose에서 `tpad=clone`으로 마지막 프레임 freeze 처리.
- "ERP"·"AiWACS"는 치환 없이 원문 유지 → ko-KR-SunHiNeural이 어떻게 발음하는지 샘플 확인 후 조정 가능 (현재 발음 미검증).

### 모션
- SCENE 03 로고가 1920×1080 캔버스에서 **중앙에서 약간 왼쪽에 치우쳐 보임** (viewBox 안에서 계단이 왼쪽 하단에서 오른쪽 상단으로 올라가는 본래 디자인 특성). 콘티 기준은 통과.

### 알려진 한계 (영상에 그대로 유지)
- scene040506 녹화가 **전체 한 세션**이라 콘티에 명시된 "약 2초 응답 대기" 등 세부 리듬은 FFmpeg trim으로만 분리. 각 scene 안에서 음성 끝난 뒤 정적 영상 유지 구간이 생김 (BGM이 깔림).
- 자막 하드서브 필터에서 Windows 경로 escape 문제로 1차 compose 실패 → `execSync({ cwd: outDir })` + 상대경로 `subtitles.srt` 로 재실행 성공.

## 최종 결과 (v2)

| 항목 | 값 |
|---|---|
| `output/ordo-demo-final.mp4` | **1920×1080 · H.264 · AAC · 4:08 · 5.7 MB** |
| `output/preview.mp4` | 1280×720 · 4:08 |
| 음성 | **ko-KR-HyunsuMultilingualNeural · rate=-5%** · 11장 (총 158초) |
| 자막 | 하드서브 · Malgun Gothic 28pt · 흰색+검정 outline (ASS alpha=&H00) |
| BGM | 2:12 × 3 crossfade → 300s trim · -16dB |
| 캡처 | `output/captures/scene01~11.png` |

## v2 수정 요약 (사용자 피드백 반영)

### 1. 빈 공백 → freeze 26개 → **모두 3~8s** (수정 전 15~28s)
- scene04 녹화의 응답 대기(10~22s · 12초)를 **4x 가속으로 3초로 압축**
- scene04 요청서 완성 구간(녹화 22~24s)을 slow motion으로 scene04 끝에 보여줌
- 모든 녹화 clip에 미세 `zoompan`(z+0.0004) 적용
- SCENE 08 토스트 유지를 14초 → 5.5초로 단축
- SCENE 09 응답 대기 → 응답 받은 뒤 2.5초만 유지하고 종료

### 2. 버벅임
- 브랜드 모션 5장(01·02·03·10·11)을 **녹화 대신 프레임 단위 렌더링** (Playwright CSS animation pause + currentTime 설정 + screenshot → ffmpeg image2 → 30fps mp4). 20s scene에 600 프레임.
- 모든 clip을 **30fps 통일**
- scene 간 전환은 `xfade=0.5s`로 통일

### 3. 자막
- 원인: `force_style='PrimaryColour=&HFFFFFFFF'` 의 **alpha FF가 완전 투명**(ASS 색상 포맷). `&H00FFFFFF`로 교체하자 자막이 선명하게 나타남
- Pretendard 폰트가 시스템에 없어 Malgun Gothic으로 명시 (libass 자동 fallback 확인됨)

### 4. 발음
- "AiWACS" 언급 제거 → **"실제 운영에서는 이 자리에 모니터링 시스템의 이벤트가 들어옵니다"**
- 자막도 동일하게 교체

### 5. 목소리
- `ko-KR-SunHiNeural` (여성) → **`ko-KR-HyunsuMultilingualNeural` (남성·멀티링궐)** 로 교체. 멀티링궐 계열이 더 자연스러움
- 전체 속도 `-5%` (0.95배)
- 문장을 쉼표 삽입으로 짧게 분리 (쉼 자동 삽입)
- SCENE 04 샘플 3개 비교용 `output/voice-samples/scene04-{sunhi,injoon,hyunsu-ml}.mp3`

### SCENE 08 레이아웃 (사용자 지시)
- 각 창 상단에 **큰 이름표 bar** 주입 (right-aligned, crop 영역 안에 글자 보이게)
  - `이운영 · 운영 담당자` / `김요청 · 요청자 A제조` / `한은행 · 요청자 B은행`
- 한은행 창에 **"알림 없음 · 다른 고객사"** 중앙 메시지
- 각 창 crop 440×500 (right 440px) → 1680 hstack → 1920 중앙 pad

## freezedetect 자가 검사 (d=3s, 민감도 n=0.001)

26개 freeze 감지 (전부 3.0~8.5s · 영상 재생 중에는 crossfade/zoompan이 보완). `output/freezedetect.json`에 상세.

```
18.00~24.73s  (scene01→scene02 xfade 지점, 3.1+3.6s)
62~90s        scene04 (응답 대기 끝 ~ 요청서 등장 사이, 각 3~5s)
99~140s       scene05~07 (각 3~6s)
151~188s      scene07~08
193~217s      scene08~09
```

**주의**: freezedetect `n=0.001`은 매우 민감 — zoompan으로 미세 변화가 있어도 임계값 밑이면 freeze로 분류. 실제 재생 시 요청서/백오피스/토스트가 흐르듯이 보임.

**영상에 들어간 숫자 (근거자료 기준 통일):**
- 요청서 **9점** → 보완 → **7점**
- 백오피스 위험 이력 **9 → 7**
- 요약: 도구 선택 **40 / 40** · 자동 테스트 **104** · 시나리오 **23 / 25**

**발음 치환 (TTS 샘플 미사전검증, 확인 필요):** Ordo→오르도, SLA→에스엘에이, AI→에이아이, AiWACS→아이왁스, ERP·B은행·A제조 그대로. 발표 전 1회 재생해서 발음 어긋나면 narration.json 수정 후 재생성.

## 커밋/푸시

**커밋 대상:** `.gitignore`, `docs/scripts/seed-demo.ps1`, `docs/video/NOTES.md`, `docs/video/package*.json`, `docs/video/src/**` (스크립트·HTML·JSON 소스만)

**gitignore 처리 (커밋 제외):** `docs/video/assets/voice/*.mp3`, `docs/video/assets/recordings/*.webm`, `docs/video/assets/music/*.mp3`, `docs/video/output/**`, `docs/video/node_modules/`
