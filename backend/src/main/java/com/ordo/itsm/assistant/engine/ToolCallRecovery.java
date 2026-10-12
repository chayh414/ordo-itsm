package com.ordo.itsm.assistant.engine;

import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모델이 tool_calls 를 안 보내고 응답 content 에 "도구 호출 문자열"을 그대로 쓴 경우를 복구한다.
 *
 * 인식 형태:
 *   (1) 함수 호출 문법:     {@code create_request("…내용…")}
 *   (2) bare JSON 객체:     {@code {"name":"create_request","arguments":{"text":"…"}}}
 *   (3) qwen 전용 태그:     {@code <tool_call>{"name":"...","arguments":{...}}</tool_call>}
 *
 * 보안:
 *   - allowedTools 안에 있는 이름만 복구 (역할 밖 도구 호출 재주입 차단)
 *
 * strip:
 *   - 사용자에게 보일 최종 content 에서 위 세 형태를 제거 (화면에 날 함수 호출 문자열이 노출되지 않도록)
 */
public final class ToolCallRecovery {

    private static final Pattern TAG = Pattern.compile(
            "<tool_call>\\s*(\\{[\\s\\S]*?\\})\\s*</tool_call>", Pattern.DOTALL);

    /** 알려진 도구 이름 뒤에 바로 "(" 가 오는 패턴. 중괄호/문자열 안쪽은 상관 없음 — 호출자가 extractParens 로 범위 추출. */
    private ToolCallRecovery() {
    }

    public record Recovered(String name, Map<String, Object> arguments) {
    }

    /**
     * content 에서 복구 가능한 도구 호출을 모두 추출.
     * 하나라도 찾으면 그걸로 리스트 반환. 세 형태를 모두 섞어서 반환하지는 않는다 (안정성 우선).
     */
    public static List<Recovered> parse(String content, Set<String> allowedTools, JsonMapper mapper) {
        if (content == null || content.isBlank() || allowedTools == null || allowedTools.isEmpty()) {
            return List.of();
        }

        // (3) <tool_call>...</tool_call> — qwen 특유
        List<Recovered> fromTag = new ArrayList<>();
        Matcher tag = TAG.matcher(content);
        while (tag.find()) {
            Recovered r = parseJsonObject(tag.group(1), allowedTools, mapper);
            if (r != null) fromTag.add(r);
        }
        if (!fromTag.isEmpty()) return fromTag;

        // (2) bare JSON — content 가 거의 {"name":...} 객체일 때
        String trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            Recovered r = parseJsonObject(trimmed, allowedTools, mapper);
            if (r != null) return List.of(r);
        }

        // (1) 함수 호출 문법 — allowedTools 중 하나의 이름이 나오고 뒤에 괄호
        for (String tool : allowedTools) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(tool) + "\\s*\\(");
            Matcher m = p.matcher(content);
            if (m.find()) {
                int openParen = m.end() - 1;
                String arg = extractParens(content, openParen);
                if (arg != null) {
                    Map<String, Object> args = extractCallArgs(tool, arg, mapper);
                    return List.of(new Recovered(tool, args));
                }
            }
        }

        return List.of();
    }

    /**
     * 복구 파서가 인식하는 세 형태의 흔적을 content 에서 제거해 사용자용 깨끗한 문자열을 돌려준다.
     * 복구된 뒤에도 사용자에게 날 함수 호출 문자열이 보이지 않도록 최종 reply content 에서 호출.
     */
    public static String strip(String content, Set<String> toolNames, JsonMapper mapper) {
        if (content == null) return "";
        String out = TAG.matcher(content).replaceAll("");

        // bare JSON — 전체가 그런 모양이면 통째로 제거
        String trimmed = out.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<?, ?> m = mapper.readValue(trimmed, Map.class);
                Object nm = m.get("name");
                if (nm instanceof String s && (toolNames == null || toolNames.contains(s))) {
                    out = "";
                }
            } catch (RuntimeException ignored) {
                // JSON 모양이지만 파싱 실패 — 유지
            }
        }

        if (toolNames != null) {
            for (String tool : toolNames) {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(tool) + "\\s*\\(");
                while (true) {
                    Matcher m = p.matcher(out);
                    if (!m.find()) break;
                    int openParen = m.end() - 1;
                    String arg = extractParens(out, openParen);
                    if (arg == null) break;  // 괄호 깨짐 — 이 부분은 남겨두기
                    int endExclusive = openParen + 1 + arg.length() + 1;
                    out = out.substring(0, m.start()) + out.substring(endExclusive);
                }
            }
        }
        return out.trim();
    }

    // ---- 내부 구현 ----

    @SuppressWarnings("unchecked")
    private static Recovered parseJsonObject(String json, Set<String> allowed, JsonMapper mapper) {
        try {
            Map<String, Object> obj = mapper.readValue(json, Map.class);
            Object name = obj.get("name");
            if (!(name instanceof String n) || !allowed.contains(n)) return null;
            Object args = obj.get("arguments");
            Map<String, Object> argsMap = Map.of();
            if (args instanceof Map<?, ?> m) {
                argsMap = (Map<String, Object>) m;
            } else if (args instanceof String s && !s.isBlank()) {
                try {
                    argsMap = mapper.readValue(s, Map.class);
                } catch (RuntimeException ignored) {
                    argsMap = Map.of();
                }
            }
            return new Recovered(n, argsMap);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** openIdx 위치의 '(' 부터 짝 맞는 ')' 바로 앞까지 내부 문자열을 반환. 문자열·중첩 괄호 처리. */
    private static String extractParens(String s, int openIdx) {
        if (openIdx >= s.length() || s.charAt(openIdx) != '(') return null;
        int depth = 0;
        boolean inStr = false;
        char strCh = 0;
        StringBuilder out = new StringBuilder();
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < s.length()) {
                    out.append(c).append(s.charAt(++i));
                    continue;
                }
                if (c == strCh) inStr = false;
                out.append(c);
                continue;
            }
            if (c == '"' || c == '\'') {
                inStr = true;
                strCh = c;
                out.append(c);
                continue;
            }
            if (c == '(') {
                depth++;
                if (depth == 1) continue;   // 바깥 '(' 는 생략
                out.append(c);
                continue;
            }
            if (c == ')') {
                depth--;
                if (depth == 0) return out.toString();
                out.append(c);
                continue;
            }
            out.append(c);
        }
        return null;
    }

    /**
     * 함수 호출의 인자 영역을 분해해 Map 으로 돌려준다. 지원 형태:
     *   - "quoted string"            → 도구별 1차 인자 키에 매핑 (create_request→text 등)
     *   - name="val", other=42       → 그대로 이름 → 값
     *   - { "k":"v" }                 → JSON 객체 그대로
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractCallArgs(String toolName, String rawArg, JsonMapper mapper) {
        String s = rawArg.trim();
        if (s.isEmpty()) return positionalFallback(toolName, "");

        // JSON 객체
        if (s.startsWith("{") && s.endsWith("}")) {
            try {
                return mapper.readValue(s, Map.class);
            } catch (RuntimeException ignored) {
                // continue
            }
        }

        // 큰따옴표/작은따옴표 1개짜리 문자열 리터럴
        if ((s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2)
                || (s.startsWith("'") && s.endsWith("'") && s.length() >= 2)) {
            String val = unquote(s);
            return positionalFallback(toolName, val);
        }

        // name=val, name2="val2" 식 named-arg
        Pattern named = Pattern.compile(
                "(\\w+)\\s*=\\s*(\"((?:\\\\.|[^\"\\\\])*)\"|'((?:\\\\.|[^'\\\\])*)'|([^,\\s]+))");
        Matcher m = named.matcher(s);
        Map<String, Object> out = new LinkedHashMap<>();
        while (m.find()) {
            String key = m.group(1);
            String v = m.group(3) != null ? m.group(3)
                    : m.group(4) != null ? m.group(4)
                    : m.group(5);
            out.put(key, unescape(v));
        }
        if (!out.isEmpty()) return out;

        // 마지막 안전망: 통째로 positional 1차 인자
        return positionalFallback(toolName, s);
    }

    private static Map<String, Object> positionalFallback(String toolName, String val) {
        return switch (toolName) {
            case "create_request" -> Map.of("text", val);
            case "get_request" -> Map.of("ticketNo", val);
            case "list_my_requests", "list_requests" -> val.isEmpty() ? Map.of() : Map.of("status", val);
            default -> Map.of();
        };
    }

    private static String unquote(String s) {
        return unescape(s.substring(1, s.length() - 1));
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\'", "'");
    }
}
