#
# Ordo 시연 데이터 재투입 — 녹화 중 데이터가 어긋나면 이걸로 리셋
#
# 전제: 백엔드가 http://localhost:8080 에서 실행 중, DB 가 리셋 가능한 상태
# 하는 일: 콘티에 쓰이는 사전 데이터 0001~0005 를 실제 API 호출로 다시 만든다
#   - 0001 A제조 방화벽 3306 (이력 11→9→7, 접수)
#   - 0002 A제조 OS 보안 패치 (8, 보완 대기)
#   - 0003 A제조 개발 서버 테이블 (3, 보완 대기)
#   - 0004 A제조 고객 포털 502 장애 (대화 신고, 접수)
#   - 0005 B은행 인터넷뱅킹 방화벽 (11, 보완 대기)
#
# ⚠️ 기존 데이터가 남아있으면 번호가 밀립니다. 보통 DB 초기화 후 사용하세요.
#
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$pw   = 'Password123!'

function Call-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers, $Body)
    $uri = "$base$Path"
    $h = @{}
    if ($Headers) { $Headers.GetEnumerator() | ForEach-Object { $h[$_.Key] = $_.Value } }
    $json = $null
    if ($Body) { $json = ($Body | ConvertTo-Json -Depth 20 -Compress) }
    try {
        if ($json) {
            $bytes = [Text.Encoding]::UTF8.GetBytes($json)
            $r = Invoke-WebRequest -Uri $uri -Method $Method -Headers $h -ContentType 'application/json; charset=utf-8' -Body $bytes -UseBasicParsing -TimeoutSec 90
        } else {
            $r = Invoke-WebRequest -Uri $uri -Method $Method -Headers $h -UseBasicParsing -TimeoutSec 90
        }
        $txt = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return [pscustomobject]@{ Ok = $true; Status = [int]$r.StatusCode; Body = ($txt | ConvertFrom-Json); Raw = $txt }
    } catch {
        return [pscustomobject]@{ Ok = $false; Status = -1; Body = $null; Raw = $_.Exception.Message }
    }
}

function Login($email) {
    $r = Call-Api -Method POST -Path '/api/auth/login' -Body @{ email = $email; password = $pw }
    if (-not $r.Ok) { throw "login fail: $email" }
    return $r.Body.accessToken
}

function Chat($token, $message) {
    return Call-Api -Method POST -Path '/api/assistant/chat' `
        -Headers @{ Authorization = "Bearer $token" } `
        -Body @{ message = $message; history = @() }
}

function Get-TicketFromCard($r) {
    if (-not $r.Ok -or -not $r.Body.cards -or $r.Body.cards.Count -eq 0) { return $null }
    $card = $r.Body.cards[-1]
    if ($card.tool -ne 'create_request') { return $null }
    return $card.data.ticket
}

Write-Host "=== Ordo 시연 데이터 재투입 ==="

$tokA = Login 'requester@a-univ.demo'
$tokB = Login 'requester@b-bank.demo'

# 0001 — 방화벽 3306 (A제조, 이력 11→9→7, 접수)
Write-Host "0001 A제조 방화벽 3306..."
$r1 = Chat $tokA '내일 밤 10시에 운영 서버 10.0.1.5에서 디비 서버 10.0.2.7로 3306 포트 열어주세요. 정산 배치 연동 때문입니다.'
$t1 = Get-TicketFromCard $r1
if ($t1) {
    Call-Api -Method POST -Path "/api/tickets/$($t1.id)/supplement" -Headers @{ Authorization = "Bearer $tokA" } -Body @{ field = 'rollbackPlan'; answer = '방화벽 정책 삭제로 원복' } | Out-Null
    Call-Api -Method POST -Path "/api/tickets/$($t1.id)/supplement" -Headers @{ Authorization = "Bearer $tokA" } -Body @{ field = 'testPlan'; answer = '스테이징에서 연결 테스트 완료' } | Out-Null
    Call-Api -Method POST -Path "/api/tickets/$($t1.id)/submit" -Headers @{ Authorization = "Bearer $tokA" } | Out-Null
    Write-Host "  → $($t1.ticketNo) 접수 (11→9→7)"
}

# 0002 — OS 보안 패치 (A제조, 8점, 보완 대기 — 접수 안 함)
Write-Host "0002 A제조 OS 보안 패치..."
$r2 = Chat $tokA '모레 밤 2시에 운영 웹서버 OS 보안 패치 적용하려고 합니다. 재부팅 필요합니다.'
$t2 = Get-TicketFromCard $r2
if ($t2) { Write-Host "  → $($t2.ticketNo) (초안·보완 대기)" }

# 0003 — 개발 서버 테이블 (A제조, 3점, 보완 대기)
Write-Host "0003 A제조 개발 서버 테이블..."
$r3 = Chat $tokA '개발 서버 디비에 테스트용 테이블 하나 만들어주세요. 롤백은 테이블 삭제하면 되고 테스트도 개발에서 진행합니다.'
$t3 = Get-TicketFromCard $r3
if ($t3) { Write-Host "  → $($t3.ticketNo) (초안·보완 대기)" }

# 0004 — 고객 포털 502 장애 (A제조, 대화 신고, 접수)
Write-Host "0004 A제조 고객 포털 502..."
$r4 = Chat $tokA '수강신청 사이트가 오전 9시부터 접속이 안 돼요. 502 Bad Gateway라고 떠요.'
$t4 = Get-TicketFromCard $r4
if ($t4) {
    Call-Api -Method POST -Path "/api/tickets/$($t4.id)/submit" -Headers @{ Authorization = "Bearer $tokA" } | Out-Null
    Write-Host "  → $($t4.ticketNo) 접수"
}

# 0005 — B은행 인터넷뱅킹 방화벽 (11점, 보완 대기)
Write-Host "0005 B은행 인터넷뱅킹 방화벽..."
$r5 = Chat $tokB '내일 밤 11시에 운영 인터넷뱅킹 서버 방화벽 포트 열어주세요.'
$t5 = Get-TicketFromCard $r5
if ($t5) { Write-Host "  → $($t5.ticketNo) (초안·보완 대기)" }

Write-Host ""
Write-Host "완료. 녹화 재개하세요."
