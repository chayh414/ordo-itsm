# =========================================================
# Ordo 테넌트 격리 시연 스크립트
# 실행: powershell -ExecutionPolicy Bypass -File docs\scripts\demo-tenant-isolation.ps1
# (서버가 켜져 있어야 함)
# =========================================================
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$base = "http://localhost:8080"

function Call-Api {
    param([string]$Method, [string]$Path, [string]$Token, $Body)

    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    $params = @{
        Method          = $Method
        Uri             = "$base$Path"
        Headers         = $headers
        ContentType     = "application/json; charset=utf-8"
        UseBasicParsing = $true
    }
    if ($Body) {
        $json = $Body | ConvertTo-Json -Depth 10
        $params["Body"] = [Text.Encoding]::UTF8.GetBytes($json)
    }

    try {
        $res  = Invoke-WebRequest @params
        $text = [Text.Encoding]::UTF8.GetString($res.RawContentStream.ToArray())
        return [pscustomobject]@{ Status = [int]$res.StatusCode; Body = ($text | ConvertFrom-Json) }
    } catch {
        $resp = $_.Exception.Response
        if (-not $resp) { throw }
        $reader = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
        $text   = $reader.ReadToEnd()
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = ($text | ConvertFrom-Json) }
    }
}

function Login([string]$email) {
    $r = Call-Api -Method Post -Path "/api/auth/login" -Body @{ email = $email; password = "Password123!" }
    return $r.Body.accessToken
}

function Check([string]$label, [bool]$ok, [string]$detail) {
    if ($ok) { Write-Host "  [OK]   $label  $detail" -ForegroundColor Green }
    else     { Write-Host "  [FAIL] $label  $detail" -ForegroundColor Red }
}

Write-Host ""
Write-Host "=== Ordo 테넌트 격리 시연 ===" -ForegroundColor Cyan

$tokenA  = Login "requester@a-univ.demo"
$tokenB  = Login "requester@b-bank.demo"
$tokenOp = Login "operator@sysone.demo"
Write-Host "1) 로그인: A대학교 요청자 / B은행 요청자 / 시스원 운영 담당자"

$created = Call-Api -Method Post -Path "/api/tickets" -Token $tokenA -Body @{
    subject         = "학생 포털 DB 컬럼 추가"
    originalRequest = "오늘 23시에 운영 DB 학생 포털 테이블에 컬럼을 추가하려고 합니다. 테스트는 했지만 롤백 절차는 아직 정리하지 못했습니다."
}
$ticketId = $created.Body.id
Write-Host "2) A대학교 요청자가 티켓 등록"
Check "등록 201" ($created.Status -eq 201) "$($created.Body.ticketNo)  SLA 해결 마감: $($created.Body.sla.resolutionDueAt)"

$aGet = Call-Api -Method Get -Path "/api/tickets/$ticketId" -Token $tokenA
Write-Host "3) A대학교 요청자가 자기 티켓 조회"
Check "조회 200" ($aGet.Status -eq 200) "$($aGet.Body.subject)"

$bGet = Call-Api -Method Get -Path "/api/tickets/$ticketId" -Token $tokenB
Write-Host "4) B은행 요청자가 A대학교 티켓 ID로 직접 접근"
Check "차단 403" ($bGet.Status -eq 403) "$($bGet.Body.message)"

$bList = Call-Api -Method Get -Path "/api/tickets" -Token $tokenB
$leak  = @($bList.Body.content | Where-Object { $_.id -eq $ticketId }).Count
Write-Host "5) B은행 요청자 목록에 A대학교 티켓 노출 여부"
Check "노출 없음" ($leak -eq 0) "B은행 목록 건수: $($bList.Body.totalElements)"

$opGet = Call-Api -Method Get -Path "/api/tickets/$ticketId" -Token $tokenOp
Write-Host "6) 시스원 운영 담당자(A대학교 담당)가 조회"
Check "조회 200" ($opGet.Status -eq 200) "$($opGet.Body.tenant.name) / $($opGet.Body.requester.name)"

Write-Host ""
Write-Host "접근 거부 감사 로그 확인:" -ForegroundColor Cyan
Write-Host '  docker exec -it ordo-postgres psql -U ordo -d ordo -c "select action, ticket_id, after_json, created_at from audit_logs order by id desc limit 5;"'
Write-Host ""
