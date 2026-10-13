package com.ordo.itsm.assistant.tool;

import com.ordo.itsm.global.security.AuthUser;
import com.ordo.itsm.ticket.TicketService;
import com.ordo.itsm.ticket.dto.CreateTicketRequest;
import com.ordo.itsm.ticket.dto.TicketCreatedResponse;
import com.ordo.itsm.triage.TriageService;
import com.ordo.itsm.triage.dto.TriageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자 요청을 DRAFT 티켓으로 임시 등록하고 트리아지를 실행한다.
 * 실제 접수(SLA 시작)는 사용자가 카드의 [접수하기] 로 POST /api/tickets/{id}/submit 를 호출해야 일어난다.
 */
@Component
@RequiredArgsConstructor
public class CreateRequestTool implements AssistantTool {

    public static final String NAME = "create_request";
    private static final int SUBJECT_MAX = 40;

    private final TicketService ticketService;
    private final TriageService triageService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "사내 IT 운영 요청을 임시(DRAFT)로 등록한다. 사용자가 '등록/접수해줘'라고 명확히 요구할 때만 호출. "
                + "질문이나 '방법을 알려줘'면 호출하지 않는다. 실제 접수는 사용자가 UI 버튼으로 확정한다.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of("type", "string",
                                "description", "요청 원문. 사용자 문장 그대로. 요약·꾸밈 금지.")
                ),
                "required", List.of("text"),
                "additionalProperties", false
        );
    }

    @Override
    public ToolResult execute(AuthUser user, Map<String, Object> args) {
        String text = asString(args, "text");
        if (text == null || text.isBlank() || text.length() < 5) {
            return ToolResult.error("요청 원문이 너무 짧습니다. 어떤 요청인지 조금 더 설명해 주세요.");
        }

        String subject = toSubject(text);

        TicketCreatedResponse created = ticketService.createDraft(user, new CreateTicketRequest(subject, text));
        TriageResponse triage = triageService.run(user, created.id());

        StringBuilder llm = new StringBuilder();
        llm.append("임시 요청서 ").append(created.ticketNo()).append(" 을 작성했습니다. ");
        if (triage.result() != null) {
            llm.append("분류: ").append(com.ordo.itsm.triage.TriageLabels.category(triage.result().category()));
            if (!triage.result().missingFields().isEmpty()) {
                String missing = triage.result().missingFields().stream()
                        .map(com.ordo.itsm.triage.TriageLabels::field)
                        .collect(java.util.stream.Collectors.joining(", "));
                llm.append(". 추가로 확인이 필요한 내용: ").append(missing);
            }
        }
        if (triage.riskGrade() != null && triage.riskScore() != null) {
            String grade = switch (triage.riskGrade().name()) {
                case "LOW" -> "낮음";
                case "MEDIUM" -> "보통";
                case "HIGH" -> "높음";
                case "CRITICAL" -> "매우 높음";
                default -> triage.riskGrade().name();
            };
            llm.append(". 위험도: ").append(grade).append(" (").append(triage.riskScore()).append("점)");
        }
        llm.append(". 접수하기 버튼을 눌러야 백오피스에 실제 등록됩니다.");

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("ticket", created);
        card.put("triage", triage);
        return ToolResult.ok(llm.toString(), card);
    }

    private static String toSubject(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUBJECT_MAX ? flat : flat.substring(0, SUBJECT_MAX);
    }

    static String asString(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? null : v.toString();
    }
}
