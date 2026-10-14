package com.ordo.itsm.assistant;

import com.ordo.itsm.assistant.dto.ChatRequest;
import com.ordo.itsm.assistant.dto.ChatResponse;
import com.ordo.itsm.assistant.engine.AssistantLlmClient;
import com.ordo.itsm.assistant.engine.AssistantLlmException;
import com.ordo.itsm.assistant.engine.AssistantReply;
import com.ordo.itsm.assistant.engine.ChatMessage;
import com.ordo.itsm.assistant.engine.ToolCall;
import com.ordo.itsm.assistant.engine.ToolCallRecovery;
import com.ordo.itsm.assistant.engine.ToolSpec;
import com.ordo.itsm.assistant.tool.AssistantTool;
import com.ordo.itsm.assistant.tool.ToolResult;
import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.triage.SensitiveDataMasker;
import com.ordo.itsm.triage.TriageLabels;
import com.ordo.itsm.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 어시스턴트의 대화 루프.
 * 1) 사용자 메시지 마스킹 → 2) 역할별 tools 와 함께 LLM 호출
 * 3) tool_calls 있으면 dispatcher 로 실행 → tool 결과 되돌려주기
 * 4) tool_calls 가 없고 content 에 함수 호출 텍스트가 있으면 복구해서 실행 (ToolCallRecovery)
 * 5) 자연어 답변이 나올 때까지 최대 maxIterations 회 반복. 상한 초과 시 안전한 폴백 메시지.
 *
 * 사용자에게 돌려주는 최종 reply content 에는 함수 호출 문자열이 남지 않도록 strip 를 거친다.
 *
 * 과거 history 는 서버에 저장하지 않는다. 클라이언트가 매 턴 재전송 (서버는 20개까지만 수용).
 * history 의 role=tool 은 신뢰할 수 없으므로 user/assistant 만 받아들인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final int HISTORY_MAX = 20;

    private final AssistantLlmClient llm;
    private final AssistantToolRegistry registry;
    private final AssistantToolDispatcher dispatcher;
    private final SensitiveDataMasker masker;
    private final JsonMapper jsonMapper;

    @Value("${assistant.max-tool-iterations:3}")
    private int maxIterations;

    public ChatResponse chat(AuthUser user, ChatRequest request) {
        List<AssistantTool> tools = registry.toolsFor(user.role());
        if (tools.isEmpty()) {
            return new ChatResponse(
                    "현재 역할(" + user.role() + ")에서는 어시스턴트가 사용할 수 있는 도구가 없습니다.",
                    List.of(), List.of(), false);
        }
        List<ToolSpec> toolSpecs = tools.stream()
                .map(t -> new ToolSpec(t.name(), t.description(), t.parametersSchema()))
                .toList();
        Set<String> allowedToolNames = tools.stream()
                .map(AssistantTool::name)
                .collect(Collectors.toUnmodifiableSet());
        // strip 용 — 역할 밖 도구 이름이 content 로 노출돼도 지우기 위해 전체 도구 이름 사용.
        // 실행 권한은 여전히 allowedToolNames (dispatcher) 가 결정.
        Set<String> allToolNames = registry.allToolNames();

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPromptFor(user.role())));
        for (ChatRequest.HistoryMessage h : safeHistory(request.history())) {
            String content = h.content() == null ? "" : h.content();
            if ("user".equals(h.role())) {
                messages.add(ChatMessage.user(masker.mask(content)));
            } else if ("assistant".equals(h.role())) {
                messages.add(ChatMessage.assistant(content, null));
            }
            // role=tool / 그 외는 무시 (클라이언트가 조작한 tool 결과 주입 방지)
        }
        messages.add(ChatMessage.user(masker.mask(request.message())));

        List<ChatResponse.Card> cards = new ArrayList<>();
        List<ChatResponse.ToolCallSummary> callLog = new ArrayList<>();
        int emptyRetries = 0;

        for (int i = 0; i < maxIterations; i++) {
            AssistantReply reply;
            try {
                reply = llm.chat(messages, toolSpecs);
            } catch (AssistantLlmException e) {
                log.warn("[Assistant] LLM 호출 실패: {}", e.getMessage());
                return new ChatResponse(
                        "지금 AI 서버와 연결이 원활하지 않아요. 잠시 뒤 다시 시도해 주세요.",
                        cards, callLog, false);
            }

            List<ToolCall> effectiveCalls = reply.toolCalls();
            boolean recovered = false;
            String assistantContent = reply.content();

            // tool_calls 가 비어있으면 content 에서 복구 시도 (노트북 Ollama 컨텍스트 압박 시 잦음)
            if (effectiveCalls == null || effectiveCalls.isEmpty()) {
                List<ToolCallRecovery.Recovered> rc =
                        ToolCallRecovery.parse(reply.content(), allowedToolNames, jsonMapper);
                if (!rc.isEmpty()) {
                    recovered = true;
                    effectiveCalls = new ArrayList<>();
                    for (ToolCallRecovery.Recovered r : rc) {
                        String id = "rec_" + UUID.randomUUID().toString().substring(0, 8);
                        effectiveCalls.add(new ToolCall(id, r.name(), r.arguments()));
                    }
                    // 복구가 성공했으면 content 에서 호출 문자열은 제거 (남은 자연어만 history 에 남긴다)
                    String stripped = ToolCallRecovery.strip(reply.content(), allToolNames, jsonMapper);
                    assistantContent = stripped.isBlank() ? null : stripped;
                    log.info("[Assistant] 텍스트 도구 호출 복구 {}건 (user={})",
                            effectiveCalls.size(), user.userId());
                }
            }

            messages.add(ChatMessage.assistant(assistantContent, effectiveCalls));

            if (effectiveCalls == null || effectiveCalls.isEmpty()) {
                // tool 호출 없음 & 복구 없음 → 자연어 답변으로 종료.
                // 역할 밖 도구 이름이 content 에 글자로 남는 것까지 지운다 (실행과 분리).
                String rawReply = reply.content();
                boolean containedRoleOutsideTool = containsRoleOutsideTool(rawReply, allToolNames, allowedToolNames);
                String clean = ToolCallRecovery.strip(rawReply, allToolNames, jsonMapper);
                // 함수 호출 문자열을 지운 뒤 생긴 연속 빈 줄은 한 줄 공백으로 축소.
                clean = clean.replaceAll("\\n{3,}", "\n\n").trim();
                if (clean.isBlank()) {
                    // 이전에 tool 결과가 피드백됐는데 2차 응답 content 가 비었다면 1회만 재시도.
                    boolean hadToolFeedback = messages.stream().anyMatch(m -> "tool".equals(m.role()));
                    if (hadToolFeedback && emptyRetries == 0) {
                        emptyRetries++;
                        log.info("[Assistant] 2차 응답 content 비어있음 — 1회 재시도 (user={})", user.userId());
                        // 방금 추가한 빈 assistant 메시지 제거 후 같은 messages 로 재질의
                        messages.remove(messages.size() - 1);
                        continue;
                    }
                    // 재시도도 실패 or 재시도 불가 → cards 기반 템플릿으로 안전 폴백
                    if (containedRoleOutsideTool && cards.isEmpty()) {
                        clean = "해당 요청은 처리할 수 없습니다. 다른 방법으로 알려 주실래요?";
                    } else {
                        clean = cards.isEmpty()
                                ? "요청을 이해하지 못했어요. 조금만 더 설명해 주실래요?"
                                : fallbackReplyFromCards(cards);
                    }
                }
                return new ChatResponse(applyKoreanLabels(clean), cards, callLog, false);
            }

            for (ToolCall call : effectiveCalls) {
                ToolResult result = dispatcher.dispatch(user, call.name(), call.arguments(), recovered);
                messages.add(ChatMessage.tool(call.id(), result.llmText()));
                if (result.cardData() != null) {
                    cards.add(new ChatResponse.Card(call.name(), result.cardData()));
                }
                callLog.add(new ChatResponse.ToolCallSummary(
                        call.name(), call.arguments(), result.ok(), result.errorMessage()));
            }
        }

        // 상한 초과 — 더 이상 모델에 재질문하지 않고 안전한 폴백.
        return new ChatResponse(
                "요청하신 작업을 처리했어요. 결과는 아래 카드를 확인해 주세요.",
                cards, callLog, true);
    }

    /**
     * reply 원문에 "역할 밖 도구" 이름이 글자로 포함돼 있었는지. (ToolCallRecovery.strip 가 지우기 전 판정.)
     * strip 결과가 빈 안내 문구로 넘어가는 분기에서 "해당 요청은 처리할 수 없습니다" 로 교체할지 결정.
     */
    private static boolean containsRoleOutsideTool(String rawReply, java.util.Set<String> all, java.util.Set<String> allowed) {
        if (rawReply == null || rawReply.isBlank()) return false;
        for (String name : all) {
            if (allowed.contains(name)) continue;
            if (rawReply.contains(name)) return true;
        }
        return false;
    }

    /**
     * 2차 응답 content 가 비었고 재시도도 실패했을 때, 마지막 카드의 종류로
     * 사용자에게 안내할 안전한 템플릿 문장을 만든다. 상태값·분류값 등 enum 코드는 쓰지 않는다.
     */
    static String fallbackReplyFromCards(List<ChatResponse.Card> cards) {
        ChatResponse.Card last = cards.get(cards.size() - 1);
        return switch (last.tool()) {
            case "create_request" -> "요청서를 준비했어요. 오른쪽 요청서에서 확인하고 '접수하기' 버튼을 눌러주세요.";
            case "get_request" -> "조회한 요청 정보를 오른쪽 요청서에서 확인해주세요.";
            case "list_my_requests", "list_requests" -> "요청 목록을 조회했어요. 아래 결과를 확인해주세요.";
            default -> "요청하신 작업을 처리했어요. 아래 결과를 확인해 주세요.";
        };
    }

    /**
     * 사용자에게 보이는 최종 reply 에 영어 enum 코드(INFO_REQUIRED, DATABASE_SCHEMA, rollbackPlan 등)가 섞여
     * 나올 때 한글 라벨로 치환해주는 안전차. 매핑에 없는 코드는 그대로 둔다.
     * 단어 경계(\b)로만 매치해 "INFORMATION" 등 다른 단어 안에서는 치환되지 않는다.
     *
     * 참고: 프롬프트는 건드리지 않는다. 이 치환은 백엔드 응답 측 보호장치.
     */
    static String applyKoreanLabels(String text) {
        if (text == null || text.isBlank()) return text;
        String out = text;
        for (var entry : TriageLabels.allEnumMappings().entrySet()) {
            String code = entry.getKey();
            String label = entry.getValue();
            // ASCII 식별자 경계: 앞뒤가 영문자/숫자/_ 가 아니거나 문자열 끝. 한글은 non-word 로 취급.
            out = out.replaceAll(
                    "(?<![A-Za-z0-9_])" + java.util.regex.Pattern.quote(code) + "(?![A-Za-z0-9_])",
                    java.util.regex.Matcher.quoteReplacement(label));
        }
        // 용어 통일 (프롬프트 미건드림, reply 측 안전차)
        out = out.replace("임시 요청서", "요청서 초안");
        return out;
    }

    private static List<ChatRequest.HistoryMessage> safeHistory(List<ChatRequest.HistoryMessage> raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        int from = Math.max(0, raw.size() - HISTORY_MAX);
        return raw.subList(from, raw.size());
    }

    private static String systemPromptFor(UserRole role) {
        return """
                너는 시스원 사내 IT 운영 어시스턴트다. Ordo 컨셉은 "양식 없이 평소 말하듯 요청" 이므로
                사용자가 "등록해줘" 같은 명시적 요청어를 붙이지 않아도 요청 의도를 적극적으로 알아듣는다.

                [결정 트리 — 반드시 이 순서로 판단]

                1. 질문 신호가 있는가? ("보통 어떻게", "~려면", "방법", "뭐가 필요", "~가능해?",
                   "~해도 돼?", "~알려줘", 끝에 "?") 그리고 본인이 지금 그 작업을 하려는 구체적 정보
                   (대상 이름·시간·장비 등)가 없는가? → 도구 없이 설명. create_request 금지.
                2. 과거 요청 목록을 묻는가? → list_my_requests / list_requests
                3. 특정 티켓번호(ORD-YYYYMMDD-NNNN) 상태를 묻는가? → get_request
                4. 본인이 "지금/오늘/이번에" 하려는 작업 또는 겪고 있는 장애를 구체적으로 말하는가?
                   → create_request

                create_request 호출 조건 (아래 중 2개 이상 있을 때 안전):
                  · 작업 대상이 특정됨: "학생 포털 테이블", "3306 포트", "ERP 서버" 등 고유 이름
                  · 작업 시점이 특정됨: "오늘 23시", "내일 오전"
                  · 본인 액션 표현: "~하려고요", "~해주세요", "~할 예정", "~이 안 돼요"
                  · 장애 증상: 접속 불가, 에러 메시지, "먹통", "느려요"

                일반 호기심("보통 어떻게", "~하려면 뭐가 필요")은 작업 대상이 추상적("방화벽 포트",
                "서버")이고 시점·본인 액션이 없으므로 create 하지 않는다.

                [예시 — 정답 외우기]
                  · "오늘 23시에 운영 DB 학생 포털 테이블에 컬럼 추가하려고요. 테스트는 했는데 롤백 절차는 아직 없어요."
                     → create_request  (대상+시점+"~하려고요")
                  · "ERP 접속이 안 되고 영업팀 전체가 주문 입력을 못 하고 있어요"
                     → create_request  (장애 증상)
                  · "방화벽 포트 열려면 보통 어떻게 해야 해?"
                     → 도구 없이 설명 ("보통 어떻게" + "~려면" = 질문)
                  · "방화벽 오픈 요청은 뭐가 필요해?"
                     → 도구 없이 설명 ("뭐가 필요" = 질문)
                  · "내 요청 어떻게 됐어?"
                     → list_my_requests
                  · "ORD-20261013-0003 어디까지 됐어?"
                     → get_request

                [출력 원칙]
                1) 도구 호출은 반드시 tool_calls 로 보낸다. 답변 본문에 create_request("…") 같은
                   함수 호출 문자열을 쓰지 않는다.
                2) 도구 호출 뒤 결과는 사용자 언어로 짧게(2~3문장) 요약한다.
                3) 승인·상태 변경·실행 도구는 존재하지 않는다. 요청받아도 하지 못한다고 안내한다.
                4) 요청자(사용자)는 요청서를 확인하고 '접수하기' 버튼을 눌러 백오피스에 등록한다.
                   요청자에게 "승인하세요" 라고 말하지 않는다. 승인은 운영사 승인자가 별도로 한다.
                5) 'UI' · '화면에서' 대신 "오른쪽 요청서에서" 로 쓴다 (왼쪽 대화 + 오른쪽 요청서 구조).
                6) 답변엔 영어 enum 코드 (DATABASE_SCHEMA, rollbackPlan, INFO_REQUIRED 등) 대신
                   한글 이름 사용. 예: DATABASE_SCHEMA → 'DB 스키마', rollbackPlan → '롤백 절차',
                   INFO_REQUIRED → '보완 대기', night_work → '심야 작업'.

                역할: """ + role.name();
    }
}
