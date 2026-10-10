# =========================================================
# Ordo 어시스턴트 시연 스크립트
# 실행: powershell -ExecutionPolicy Bypass -File docs\scripts\demo-assistant.ps1
# 사전조건:
#   docs\scripts\preflight.ps1 가 모두 OK (Docker PG, Spark 터널+모델, 백엔드)
# =========================================================
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$base = "http://localhost:8080"

function Call-Api {
    param([string]$Method, [string]$Path, [string]$Token, $Body)
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $params = @{
        Method = $Method; Uri = "$base$Path"; Headers = $headers
        ContentType = "application/json; charset=utf-8"; UseBasicParsing = $true
    }
    if ($Body) { $params["Body"] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10)) }
    try {
        $res = Invoke-WebRequest @params
        $text = [Text.Encoding]::UTF8.GetString($res.RawContentStream.ToArray())
        return [pscustomobject]@{ Status = [int]$res.StatusCode; Body = ($text | ConvertFrom-Json) }
    } catch {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        $reader = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = ($reader.ReadToEnd() | ConvertFrom-Json) }
    }
}

function Login([string]$email) {
    $login = Call-Api -Method Post -Path "/api/auth/login" -Body @{ email = $email; password = "Password123!" }
    if ($login.Status -ne 200) {
        throw "로그인 실패 ($email): $($login.Body | ConvertTo-Json -Compress)"
    }
    return $login.Body.accessToken
}

function Chat {
    param([string]$Title, [string]$Message, [string]$Token, $History = @())
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
    Write-Host "USER: $Message" -ForegroundColor Gray
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $res = Call-Api -Method Post -Path "/api/assistant/chat" -Token $Token -Body @{
        message = $Message
        history = $History
    }
    $sw.Stop()
    if ($res.Status -ne 200) {
        Write-Host "FAIL HTTP $($res.Status): $($res.Body | ConvertTo-Json -Depth 5 -Compress)" -ForegroundColor Red
        return $res.Body
    }
    $b = $res.Body
    Write-Host ("ASSISTANT ({0}ms): {1}" -f $sw.ElapsedMilliseconds, $b.reply) -ForegroundColor Green
    foreach ($c in $b.cards) {
        $preview = ($c.data | ConvertTo-Json -Depth 5 -Compress)
        if ($preview.Length -gt 220) { $preview = $preview.Substring(0,220) + "..." }
        Write-Host "  CARD [$($c.tool)] $preview" -ForegroundColor DarkYellow
    }
    foreach ($t in $b.toolCalls) {
        $tag = if ($t.ok) { "OK" } else { "FAIL" }
        $args = ($t.args | ConvertTo-Json -Compress)
        Write-Host "  TOOL $tag $($t.name) $args" -ForegroundColor DarkCyan
        if (-not $t.ok -and $t.errorMessage) {
            Write-Host "       사유: $($t.errorMessage)" -ForegroundColor Red
        }
    }
    if ($b.truncated) {
        Write-Host "  (3회 상한 도달 — truncated)" -ForegroundColor Yellow
    }
    return $b
}

function Submit {
    param([int]$TicketId, [string]$Token)
    $res = Call-Api -Method Post -Path "/api/tickets/$TicketId/submit" -Token $Token
    if ($res.Status -ne 200) {
        Write-Host "  SUBMIT 실패 HTTP $($res.Status): $($res.Body | ConvertTo-Json -Depth 5 -Compress)" -ForegroundColor Red
        return $null
    }
    Write-Host "  SUBMIT 완료 — 상태=$($res.Body.status), SLA 마감=$($res.Body.sla.resolutionDueAt)" -ForegroundColor Green
    return $res.Body
}

# -------------------- 시나리오 A: REQUESTER --------------------
Write-Host ""
Write-Host "########## REQUESTER (김요청 — A대학교) ##########" -ForegroundColor Magenta
$tokA = Login "requester@a-univ.demo"

Chat "A1 일반 대화" "안녕! 네가 뭘 할 수 있는지 두세줄로 알려줘." $tokA | Out-Null
Chat "A2 질문(등록 아님)" "방화벽 포트는 보통 어떻게 요청해야 돼?" $tokA | Out-Null

$resCreate = Chat "A3 등록 요청 (DRAFT 생성)" `
    "운영 DB 학생 포털 테이블에 컬럼 하나 추가 요청해줘. 23시 작업, 테스트는 끝났고 롤백 절차는 아직 못 짰어." $tokA
$draftTicket = $null
if ($resCreate.cards) {
    $card = $resCreate.cards | Where-Object { $_.tool -eq "create_request" } | Select-Object -First 1
    if ($card) { $draftTicket = $card.data.ticket }
}

if ($draftTicket) {
    Write-Host ""
    Write-Host "=== A4 상태 조회 (DRAFT) ===" -ForegroundColor Cyan
    Chat "A4 get_request" "방금 만든 $($draftTicket.ticketNo) 현재 상태 알려줘." $tokA | Out-Null

    Write-Host ""
    Write-Host "=== A5 사용자가 [접수하기] 를 눌러 confirm — LLM 도구 아님 ===" -ForegroundColor Cyan
    Submit $draftTicket.id $tokA | Out-Null

    Write-Host ""
    Write-Host "=== A6 접수 후 다시 상태 조회 ===" -ForegroundColor Cyan
    Chat "A6 get_request after submit" "$($draftTicket.ticketNo) 지금 어디까지 처리됐어?" $tokA | Out-Null
} else {
    Write-Host "  (A3 에서 DRAFT 티켓을 받지 못해 A4~A6 skip)" -ForegroundColor Yellow
}

Chat "A7 내 요청 목록" "내가 올린 요청들 목록 보여줘." $tokA | Out-Null
Chat "A8 상태 필터" "그 중에 보완 대기 중인 것만 다시 보여줘." $tokA | Out-Null

# -------------------- 시나리오 B: OPERATOR --------------------
Write-Host ""
Write-Host "########## OPERATOR (이운영 — 공급사) ##########" -ForegroundColor Magenta
$tokB = Login "operator@sysone.demo"

Chat "B1 공급사 측 목록 조회" "오늘 들어온 요청 중 보완 대기 중인 거 보여줘." $tokB | Out-Null
Chat "B2 특정 티켓 상세" `
    ($(if ($draftTicket) { "$($draftTicket.ticketNo) 상태 좀 봐줘." } else { "최근 요청 중 하나 상세 알려줘." })) `
    $tokB | Out-Null
Chat "B3 등록 시도 — 공급사는 create_request 없음 → 모델이 안내해야 함" `
    "방화벽 3306 포트 하나 열어줘. 요청 등록해." $tokB | Out-Null

Write-Host ""
Write-Host "완료. 승인·상태 변경 도구가 없기 때문에 B3 처럼 요청해도 접수되지 않음." -ForegroundColor Green
