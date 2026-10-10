# =========================================================
# Ordo spike — qwen2.5:7b 의 Ollama OpenAI 호환 tool-calling 안정성 검증
# 실행: powershell -ExecutionPolicy Bypass -File docs\scripts\spike-tools.ps1
# 사전조건: Spark SSH 터널 (localhost:11435 → Ollama 11500) 가 떠 있어야 함.
# 산출물: 콘솔 요약 표 + docs\scripts\spike-tools.result.md (발표용 markdown)
#         docs\scripts\spike-tools.log.jsonl (시도별 원시 로그)
# =========================================================
$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# ---- 설정 ---------------------------------------------------------
$base            = "http://localhost:11435/v1"
$model           = "qwen2.5:7b"
$iterations      = 5
$timeoutSec      = 60
$passThreshold   = 0.70   # 시나리오별 성공률 70% 이상 → PASS
$scriptDir       = $PSScriptRoot
$mdPath          = Join-Path $scriptDir "spike-tools.result.md"
$logPath         = Join-Path $scriptDir "spike-tools.log.jsonl"
if (Test-Path $logPath) { Remove-Item $logPath -Force }

# ---- 시스템 프롬프트 (운영 어시스턴트 톤의 축소판) ------------------
$systemPrompt = @'
너는 사내 IT 운영 어시스턴트다. 다음 원칙을 지킨다.
1) 사용자가 "요청/등록/접수해줘"라고 명확히 등록을 원할 때만 create_request 를 호출한다. "어떻게 해야 해?" "방법 알려줘" 같은 질문엔 도구 없이 자연어로만 설명한다.
2) 과거 요청 목록을 묻는 질문엔 list 계열 도구(list_my_requests 또는 list_requests)를 호출한다.
3) 특정 티켓번호(ORD-YYYYMMDD-NNNN)의 상태를 묻는 질문엔 get_request 를 그대로 호출한다. 티켓번호를 지어내지 않는다.
4) 도구에 전달하는 인자는 사용자 원문을 기반으로 하며, 임의로 요약하거나 꾸미지 않는다.
5) tenantId · userId 같은 권한 정보는 절대 전달하지 않는다.
6) 현재 노출된 도구 목록에 없는 도구는 호출을 시도하지 말고, 자연어로 안내한다.
'@

# ---- 도구 스펙 (REQUESTER 역할용 3종) ------------------------------
$toolsRequester = @(
    @{
        type = "function"
        function = @{
            name = "create_request"
            description = "사내 IT 운영 요청을 임시(DRAFT)로 등록한다. 사용자가 명확히 접수를 요구할 때만 호출."
            parameters = @{
                type = "object"
                properties = @{
                    text = @{ type = "string"; description = "요청 원문. 요약 금지, 사용자 문장 그대로." }
                }
                required = @("text")
                additionalProperties = $false
            }
        }
    },
    @{
        type = "function"
        function = @{
            name = "list_my_requests"
            description = "내 요청 목록을 조회한다. status 를 생략하면 전체."
            parameters = @{
                type = "object"
                properties = @{
                    status = @{
                        type = "string"
                        enum = @("DRAFT","SUBMITTED","TRIAGED","INFO_REQUIRED","IN_PROGRESS","RESOLVED","CLOSED")
                        description = "티켓 상태 필터 (선택)"
                    }
                }
                additionalProperties = $false
            }
        }
    },
    @{
        type = "function"
        function = @{
            name = "get_request"
            description = "티켓번호(ORD-YYYYMMDD-NNNN)로 요청 상세를 조회한다."
            parameters = @{
                type = "object"
                properties = @{
                    ticketNo = @{ type = "string"; description = "티켓번호 (예: ORD-20261008-0001)" }
                }
                required = @("ticketNo")
                additionalProperties = $false
            }
        }
    }
)

# ---- 도구 스펙 (OPERATOR 역할용 2종 — 등록 도구 없음) --------------
$toolsOperator = @(
    @{
        type = "function"
        function = @{
            name = "list_requests"
            description = "내가 접근 가능한 고객사의 요청 목록을 조회한다. 공급사 담당자 전용. status 를 생략하면 전체."
            parameters = @{
                type = "object"
                properties = @{
                    status = @{
                        type = "string"
                        enum = @("DRAFT","SUBMITTED","TRIAGED","INFO_REQUIRED","IN_PROGRESS","RESOLVED","CLOSED")
                        description = "티켓 상태 필터 (선택)"
                    }
                }
                additionalProperties = $false
            }
        }
    },
    @{
        type = "function"
        function = @{
            name = "get_request"
            description = "티켓번호(ORD-YYYYMMDD-NNNN)로 요청 상세를 조회한다."
            parameters = @{
                type = "object"
                properties = @{
                    ticketNo = @{ type = "string"; description = "티켓번호 (예: ORD-20261008-0001)" }
                }
                required = @("ticketNo")
                additionalProperties = $false
            }
        }
    }
)

# ---- 시나리오 정의 -------------------------------------------------
# ArgCheck 는 scriptblock. 인자 $a 는 ConvertFrom-Json 결과.
$scenarios = @(
    [pscustomobject]@{
        Id = 1
        ToolSet = "REQUESTER"
        Prompt = "운영 DB 학생 포털 테이블에 컬럼 하나 추가하는 요청 등록해줘. 23시 작업, 테스트는 끝났고 롤백 절차는 아직 못 짰어."
        ExpectedTool = "create_request"
        ArgCheck = { param($a) ($a.text -ne $null) -and ($a.text.Length -ge 15) }
        Note = "명시적 등록 요청 — create_request 로 가야 함"
    },
    [pscustomobject]@{
        Id = 2
        ToolSet = "REQUESTER"
        Prompt = "내가 올린 요청들 상태 좀 보여줘."
        ExpectedTool = "list_my_requests"
        ArgCheck = { param($a) $true }
        Note = "목록 조회 — 상태 필터 없음도 OK"
    },
    [pscustomobject]@{
        Id = 3
        ToolSet = "REQUESTER"
        Prompt = "보완 대기 중인 내 요청만 추려줘."
        ExpectedTool = "list_my_requests"
        ArgCheck = { param($a) ($a.status -eq "INFO_REQUIRED") -or ($a.status -eq $null) }
        Note = "상태 필터 — 가장 가까운 enum 선택 or 전체"
    },
    [pscustomobject]@{
        Id = 4
        ToolSet = "REQUESTER"
        Prompt = "ORD-20261008-0003 요청 어떻게 처리되고 있어?"
        ExpectedTool = "get_request"
        ArgCheck = { param($a) $a.ticketNo -eq "ORD-20261008-0003" }
        Note = "티켓번호 정확 전달 — 환각 금지"
    },
    [pscustomobject]@{
        Id = 5
        ToolSet = "REQUESTER"
        Prompt = "안녕! 오늘 날씨 어때?"
        ExpectedTool = $null
        ArgCheck = { param($a) $true }
        Note = "일반 대화 — 도구 호출하지 말아야 함"
    },
    [pscustomobject]@{
        Id = 6
        ToolSet = "REQUESTER"
        Prompt = "방화벽 포트 열려면 보통 어떻게 해야 해?"
        ExpectedTool = $null
        ArgCheck = { param($a) $true }
        Note = "방법 질문 — create_request 를 부르면 안 되고 설명으로 답해야 함"
    },
    [pscustomobject]@{
        Id = 7
        ToolSet = "OPERATOR"
        Prompt = "A대학교 요청 중에 보완 대기 중인 거 보여줘."
        ExpectedTool = "list_requests"
        ArgCheck = { param($a) ($a.status -eq "INFO_REQUIRED") -or ($a.status -eq $null) }
        Note = "공급사 측 — list_requests(status=INFO_REQUIRED). 테넌트 필터는 서버 scope 가 처리하므로 arg 로 못 받음"
    }
)

# ---- LLM 호출 함수 ------------------------------------------------
function Invoke-ToolChat {
    param(
        [Parameter(Mandatory=$true)]$Messages,
        [Parameter(Mandatory=$true)]$Tools,
        [int]$Timeout = 60
    )
    $body = @{
        model = $model
        temperature = 0
        messages = $Messages
        tools = $Tools
        tool_choice = "auto"
    } | ConvertTo-Json -Depth 20

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $res = Invoke-WebRequest `
            -Uri "$base/chat/completions" `
            -Method POST `
            -ContentType "application/json; charset=utf-8" `
            -Body ([Text.Encoding]::UTF8.GetBytes($body)) `
            -UseBasicParsing -TimeoutSec $Timeout
        $sw.Stop()
        # PS 5.1 Invoke-WebRequest 는 JSON 응답을 ISO-8859-1 로 디코딩할 수 있으므로 바이트로 다시 UTF-8 처리
        $text = [Text.Encoding]::UTF8.GetString($res.RawContentStream.ToArray())
        $payload = $text | ConvertFrom-Json
        return [pscustomobject]@{
            Ok = $true
            LatencyMs = [int]$sw.ElapsedMilliseconds
            Message = $payload.choices[0].message
            Error = $null
        }
    } catch {
        $sw.Stop()
        return [pscustomobject]@{
            Ok = $false
            LatencyMs = [int]$sw.ElapsedMilliseconds
            Message = $null
            Error = $_.Exception.Message
        }
    }
}

function Test-Attempt {
    param($Attempt, $Expected)
    if (-not $Attempt.Ok) {
        return [pscustomobject]@{ Pass = $false; Reason = "HTTP 실패: $($Attempt.Error)" }
    }
    $msg = $Attempt.Message
    $toolCalls = $msg.tool_calls

    if ($null -eq $Expected.ExpectedTool) {
        if ($toolCalls -and @($toolCalls).Count -gt 0) {
            return [pscustomobject]@{ Pass = $false; Reason = "도구 호출됨(기대: 호출 없음) → $($toolCalls[0].function.name)" }
        }
        $content = if ($msg.content) { $msg.content } else { "(빈 응답)" }
        $preview = if ($content.Length -gt 40) { $content.Substring(0,40) + "..." } else { $content }
        return [pscustomobject]@{ Pass = $true; Reason = "자연어: $preview" }
    }

    if (-not $toolCalls -or @($toolCalls).Count -eq 0) {
        return [pscustomobject]@{ Pass = $false; Reason = "도구 호출 없음 (기대: $($Expected.ExpectedTool))" }
    }
    $call = $toolCalls[0]
    if ($call.function.name -ne $Expected.ExpectedTool) {
        return [pscustomobject]@{ Pass = $false; Reason = "다른 도구: $($call.function.name)" }
    }
    try {
        $toolArgs = $call.function.arguments | ConvertFrom-Json
    } catch {
        return [pscustomobject]@{ Pass = $false; Reason = "arguments JSON 파싱 실패: $($call.function.arguments)" }
    }
    $argPass = & $Expected.ArgCheck $toolArgs
    if (-not $argPass) {
        return [pscustomobject]@{ Pass = $false; Reason = "인자 검증 실패: $($call.function.arguments)" }
    }
    $argsPreview = $call.function.arguments
    if ($argsPreview.Length -gt 60) { $argsPreview = $argsPreview.Substring(0,60) + "..." }
    return [pscustomobject]@{ Pass = $true; Reason = "OK · $argsPreview" }
}

# ---- 메인 실행 ----------------------------------------------------
Write-Host ""
Write-Host "=== Ordo spike — qwen2.5:7b tool-calling 검증 ===" -ForegroundColor Cyan
Write-Host "endpoint=$base  model=$model  iterations/scenario=$iterations" -ForegroundColor Gray
Write-Host ""

$allResults = @()
foreach ($s in $scenarios) {
    $toolsForScenario = if ($s.ToolSet -eq "OPERATOR") { $toolsOperator } else { $toolsRequester }
    Write-Host ("[S{0} / {1}] {2}" -f $s.Id, $s.ToolSet, $s.Prompt) -ForegroundColor Yellow
    Write-Host ("     기대: {0}  — {1}" -f ($(if ($s.ExpectedTool) { $s.ExpectedTool } else { "(no tool)" })), $s.Note) -ForegroundColor DarkGray
    for ($i = 1; $i -le $iterations; $i++) {
        $messages = @(
            @{ role = "system"; content = $systemPrompt },
            @{ role = "user";   content = $s.Prompt }
        )
        $att = Invoke-ToolChat -Messages $messages -Tools $toolsForScenario -Timeout $timeoutSec
        $eval = Test-Attempt -Attempt $att -Expected $s
        $color = if ($eval.Pass) { "Green" } else { "Red" }
        $tag   = if ($eval.Pass) { "PASS" } else { "FAIL" }
        Write-Host ("     #{0}  {1}  {2,5}ms  {3}" -f $i, $tag, $att.LatencyMs, $eval.Reason) -ForegroundColor $color
        $row = [pscustomobject]@{
            Scenario  = $s.Id
            ToolSet   = $s.ToolSet
            Prompt    = $s.Prompt
            Expected  = $(if ($s.ExpectedTool) { $s.ExpectedTool } else { "(no tool)" })
            Attempt   = $i
            Pass      = $eval.Pass
            LatencyMs = $att.LatencyMs
            Reason    = $eval.Reason
        }
        $allResults += $row
        ($row | ConvertTo-Json -Compress) | Add-Content -Path $logPath -Encoding UTF8
    }
    Write-Host ""
}

# ---- Round-trip 검증 ---------------------------------------------
# tool 결과를 되돌려 주면 자연어 답변이 나오는지 (최종 production 루프의 핵심 가정)
Write-Host "[Round-trip] get_request → tool result → natural-language reply" -ForegroundColor Yellow
$rtPrompt = "ORD-20261008-0003 지금 어디까지 처리됐어?"
$rtMessages = @(
    @{ role = "system"; content = $systemPrompt },
    @{ role = "user";   content = $rtPrompt }
)
$rt1 = Invoke-ToolChat -Messages $rtMessages -Tools $toolsRequester -Timeout $timeoutSec
$rtPass = $false; $rtReply = ""
if ($rt1.Ok -and $rt1.Message.tool_calls -and @($rt1.Message.tool_calls).Count -gt 0) {
    $call = $rt1.Message.tool_calls[0]
    $assistantTurn = @{
        role = "assistant"
        content = $null
        tool_calls = @($rt1.Message.tool_calls)
    }
    $toolTurn = @{
        role = "tool"
        tool_call_id = $call.id
        content = '{"ticketNo":"ORD-20261008-0003","status":"INFO_REQUIRED","category":"DATABASE_SCHEMA","missingFields":["rollbackPlan"],"slaResolutionDueAt":"2026-10-10T09:00:00+09:00","riskFactors":["production_environment","database_schema_change","rollback_plan_missing"]}'
    }
    $rtMessages += $assistantTurn
    $rtMessages += $toolTurn
    $rt2 = Invoke-ToolChat -Messages $rtMessages -Tools $toolsRequester -Timeout $timeoutSec
    if ($rt2.Ok -and (-not $rt2.Message.tool_calls -or @($rt2.Message.tool_calls).Count -eq 0) -and $rt2.Message.content) {
        $rtPass = $true
        $rtReply = $rt2.Message.content
    } else {
        $rtReply = "자연어 답변 생성 실패"
    }
} else {
    $rtReply = "1차 호출에서 get_request tool_call 미발생"
}
$rtColor = if ($rtPass) { "Green" } else { "Red" }
$rtTag   = if ($rtPass) { "PASS" } else { "FAIL" }
Write-Host ("     {0}  {1}" -f $rtTag, $rtReply.Substring(0, [Math]::Min(80, $rtReply.Length))) -ForegroundColor $rtColor
Write-Host ""

# ---- 집계 --------------------------------------------------------
$summary = $allResults | Group-Object Scenario | ForEach-Object {
    $g = $_
    $passCount = (@($g.Group | Where-Object { $_.Pass })).Count
    $total     = @($g.Group).Count
    $rate      = [math]::Round(($passCount / $total), 2)
    [pscustomobject]@{
        Scenario = [int]$g.Name
        ToolSet  = $g.Group[0].ToolSet
        Prompt   = $g.Group[0].Prompt
        Expected = $g.Group[0].Expected
        Pass     = "$passCount/$total"
        Rate     = $rate
        AvgMs    = [int](($g.Group | Measure-Object LatencyMs -Average).Average)
    }
} | Sort-Object Scenario

Write-Host "=== 시나리오별 요약 ===" -ForegroundColor Cyan
$summary | Format-Table Scenario, ToolSet, Expected, Pass, Rate, AvgMs -AutoSize

$overall = [math]::Round((@($allResults | Where-Object { $_.Pass }).Count / $allResults.Count), 2)
$allSceneOk = ($summary | Where-Object { $_.Rate -lt $passThreshold }).Count -eq 0
$gatePass = $allSceneOk -and ($overall -ge $passThreshold) -and $rtPass

Write-Host ("overall success rate: {0}   round-trip: {1}   gate(>=70% & round-trip): {2}" `
    -f $overall, $(if ($rtPass) { "PASS" } else { "FAIL" }), $(if ($gatePass) { "PASS" } else { "FAIL" })) `
    -ForegroundColor $(if ($gatePass) { "Green" } else { "Red" })

# ---- S6 안전 가드: 질문에 create_request 를 부른 횟수를 명시적으로 보고 -
$s6 = $allResults | Where-Object { $_.Scenario -eq 6 }
if ($s6) {
    $falseCreate = @($s6 | Where-Object { -not $_.Pass -and $_.Reason -like "*create_request*" }).Count
    $s6Total = @($s6).Count
    $s6Rate = [math]::Round(($falseCreate / $s6Total), 2)
    if ($falseCreate -gt 0) {
        Write-Host ("[WARN] S6 질문에 create_request 호출 {0}/{1} 회({2}) — 시스템 프롬프트 보강 필요" `
            -f $falseCreate, $s6Total, $s6Rate) -ForegroundColor Yellow
    } else {
        Write-Host "[OK] S6 질문에 create_request 를 호출한 횟수 없음" -ForegroundColor Green
    }
}

# ---- markdown 보고서 ---------------------------------------------
$md = @()
$md += "# Ordo spike — qwen2.5:7b tool-calling 결과"
$md += ""
$md += "- 엔드포인트: $base"
$md += "- 모델: $model"
$md += "- 시나리오별 반복: $iterations 회"
$md += "- 통과 기준: 각 시나리오 성공률 >= $([int]($passThreshold*100))% + round-trip PASS"
$md += "- 실행 시각: $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
$md += ""
$md += "| # | 세트 | 요청 | 기대 도구 | 성공 | 성공률 | 평균(ms) |"
$md += "|---:|---|---|---|---:|---:|---:|"
foreach ($row in $summary) {
    $md += ("| {0} | {1} | {2} | {3} | {4} | {5} | {6} |" -f `
        $row.Scenario, $row.ToolSet, $row.Prompt, $row.Expected, $row.Pass, $row.Rate, $row.AvgMs)
}
$md += ""
$md += "**Round-trip (tool result → 자연어 답변):** " + $(if ($rtPass) { "PASS" } else { "FAIL" })
if ($rtPass) {
    $md += ""
    $md += "> " + $rtReply
}
$md += ""
$md += "**Overall:** success_rate=$overall · gate=" + $(if ($gatePass) { "PASS" } else { "FAIL" })
$md += ""
$md += "원시 로그: ``docs/scripts/spike-tools.log.jsonl``"
$md -join "`n" | Out-File -FilePath $mdPath -Encoding UTF8

Write-Host ""
Write-Host "md 보고서: $mdPath" -ForegroundColor Gray
Write-Host "원시 로그: $logPath" -ForegroundColor Gray

if ($gatePass) { exit 0 } else { exit 1 }
