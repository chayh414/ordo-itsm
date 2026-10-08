# =========================================================
# Ordo preflight — 시연 전 로컬 환경 점검
# 실행: powershell -ExecutionPolicy Bypass -File docs\scripts\preflight.ps1
# 각 항목 [OK]/[FAIL] 로 출력. 실패 시 해결 명령어 안내.
# =========================================================
$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$script:failures = 0

function Write-Ok([string]$name, [string]$detail = "") {
    $suffix = if ($detail) { " — $detail" } else { "" }
    Write-Host ("[OK]   {0}{1}" -f $name, $suffix) -ForegroundColor Green
}

function Write-Fail([string]$name, [string]$reason, [string[]]$fix) {
    $script:failures++
    Write-Host ("[FAIL] {0} — {1}" -f $name, $reason) -ForegroundColor Red
    if ($fix -and $fix.Count -gt 0) {
        Write-Host "       해결:" -ForegroundColor Yellow
        foreach ($line in $fix) { Write-Host ("         > {0}" -f $line) -ForegroundColor Yellow }
    }
}

Write-Host ""
Write-Host "=== Ordo preflight ===" -ForegroundColor Cyan

# -------------------------------------------------------------------
# 1) Docker PostgreSQL (ordo-postgres) 실행 확인
# -------------------------------------------------------------------
$name = "Docker PostgreSQL (ordo-postgres)"
$dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCmd) {
    Write-Fail $name "docker CLI 를 찾을 수 없음" @(
        "Docker Desktop 설치 또는 PATH 확인"
    )
} else {
    try {
        $status = (& docker inspect -f "{{.State.Status}}" ordo-postgres 2>$null)
        if ($LASTEXITCODE -ne 0 -or -not $status) {
            Write-Fail $name "ordo-postgres 컨테이너가 없음" @(
                "docker compose up -d"
            )
        } elseif ($status.Trim() -ne "running") {
            Write-Fail $name ("컨테이너 상태: " + $status.Trim()) @(
                "docker start ordo-postgres",
                "또는 docker compose up -d"
            )
        } else {
            Write-Ok $name "running"
        }
    } catch {
        Write-Fail $name ("docker inspect 실패: " + $_.Exception.Message) @(
            "Docker Desktop 실행 중인지 확인"
        )
    }
}

# -------------------------------------------------------------------
# 2) Spark 통로(localhost:11435) Ollama 응답 + qwen2.5:7b 모델 존재
# -------------------------------------------------------------------
$name = "Spark Ollama (localhost:11435)"
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:11435/v1/models" -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -ne 200) {
        Write-Fail $name ("HTTP " + [int]$resp.StatusCode) @(
            "ssh -N -L 11435:127.0.0.1:11500 ssh.kopoda.store"
        )
    } else {
        $models = ($resp.Content | ConvertFrom-Json).data | ForEach-Object { $_.id }
        if ($models -contains "qwen2.5:7b") {
            Write-Ok $name "qwen2.5:7b 로드됨"
        } else {
            Write-Fail $name ("qwen2.5:7b 없음 — 노출 모델: " + ($models -join ", ")) @(
                "Spark에서(우리 전용 Ollama=11500): OLLAMA_HOST=127.0.0.1:11500 ollama pull qwen2.5:7b",
                "Spark 전용 Ollama가 꺼진 경우 재기동: OLLAMA_HOST=127.0.0.1:11500 OLLAMA_MODELS=`$HOME/ordo-ollama/models OLLAMA_KEEP_ALIVE=10m OLLAMA_MAX_LOADED_MODELS=1 nohup ollama serve > ~/ordo-ollama/server.log 2>&1 &",
                "또는 .env 의 LLM_MODEL 을 사용 가능 모델로 교체"
            )
        }
    }
} catch {
    Write-Fail $name ("연결 실패: " + $_.Exception.Message) @(
        "ssh -N -L 11435:127.0.0.1:11500 ssh.kopoda.store",
        "터널이 떠있는지: Get-NetTCPConnection -LocalPort 11435"
    )
}

# -------------------------------------------------------------------
# 3) 백엔드(localhost:8080) 로그인 가능 여부
# -------------------------------------------------------------------
$name = "Backend login (localhost:8080)"
$loginBody = @{ email = "requester@a-univ.demo"; password = "Password123!" } | ConvertTo-Json -Compress
try {
    $resp = Invoke-WebRequest -Uri "http://localhost:8080/api/auth/login" `
        -Method Post `
        -ContentType "application/json; charset=utf-8" `
        -Body ([Text.Encoding]::UTF8.GetBytes($loginBody)) `
        -UseBasicParsing -TimeoutSec 10
    $body = $resp.Content | ConvertFrom-Json
    if ($resp.StatusCode -eq 200 -and $body.accessToken) {
        Write-Ok $name "로그인 성공 (requester@a-univ.demo)"
    } else {
        Write-Fail $name ("응답 이상 HTTP " + [int]$resp.StatusCode) @(
            "백엔드 로그 확인"
        )
    }
} catch {
    $resp = $_.Exception.Response
    if ($resp) {
        $code = [int]$resp.StatusCode
        $reader = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
        $errText = $reader.ReadToEnd()
        if ($code -eq 401) {
            Write-Fail $name ("401 Unauthorized — 데모 계정 없음/비번 불일치") @(
                "비밀번호가 Password123! 인지 확인",
                "계정이 없으면 백엔드 재시작 — users 테이블이 비어 있을 때 DemoDataInitializer가 자동 생성"
            )
        } else {
            Write-Fail $name ("HTTP $code — " + ($errText.Trim())) @(
                "백엔드 로그 확인"
            )
        }
    } else {
        Write-Fail $name ("연결 실패: " + $_.Exception.Message) @(
            "cd backend; .\gradlew.bat bootRun",
            "포트 사용 중인지: Get-NetTCPConnection -LocalPort 8080"
        )
    }
}

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "모든 점검 통과. 시연 가능." -ForegroundColor Green
    exit 0
} else {
    Write-Host ("실패 {0}건 — 위 해결 명령어를 참고하세요." -f $script:failures) -ForegroundColor Red
    exit 1
}
